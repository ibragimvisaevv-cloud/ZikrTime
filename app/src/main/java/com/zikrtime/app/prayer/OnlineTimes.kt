package com.zikrtime.app.prayer

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.zikrtime.app.data.City
import com.zikrtime.app.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Время намаза из интернета (бесплатный сервис aladhan.com).
 * Загружаем месяц целиком и храним на телефоне, поэтому без интернета последние данные остаются.
 * Если данных нет — используется расчёт на телефоне ([PrayerCalculator]).
 */
object OnlineTimes {

    /** Время на один день в часовом поясе города. */
    class Day(val zone: ZoneId, val times: Map<Prayer, LocalTime>)

    /** Меняется после каждой успешной загрузки — экраны перерисовываются. */
    var version by mutableIntStateOf(0)
        private set

    private var requestSerial = 0L
    private val scheduleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Survives closing the city/settings screen. Receivers also use the same resolver. */
    fun refreshSchedule(context: Context, force: Boolean = false): kotlinx.coroutines.Job {
        val app = context.applicationContext
        return scheduleScope.launch {
            refresh(force)
            PrayerAlarms.scheduleNext(app)
            com.zikrtime.app.alarm.FajrAlarm.schedule(app)
            com.zikrtime.app.prayer.FridayReminders.schedule(app)
            com.zikrtime.app.widget.Widgets.updateAll(app)
        }
    }

    private const val PREFIX = "otimes2_"
    private val order = listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
    private val memory = java.util.concurrent.ConcurrentHashMap<String, Map<LocalDate, Day>>()

    /** Данные зависят от координат, метода и мазхаба. */
    private fun signature(city: City = Store.location): String =
        String.format(
            Locale.US, "%.4f_%.4f_%d_%d",
            city.latitude, city.longitude, Store.method.aladhanId, Store.madhab.ordinal,
        )

    private fun zoneKey(city: City = Store.location) =
        String.format(Locale.US, "ot_zone_%.4f_%.4f", city.latitude, city.longitude)

    fun cachedZone(): ZoneId? = Store.getRaw(zoneKey())?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    fun manualZone(): ZoneId? = Store.getRaw("manual_" + zoneKey())?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    fun setManualZone(zone: ZoneId?) = Store.putRaw("manual_" + zoneKey(), zone?.id.orEmpty())

    private fun key(month: YearMonth) = PREFIX + signature() + "_" + month

    /** Время на дату из сохранённых данных или null, если их нет. */
    fun day(date: LocalDate): Day? {
        if (!Store.onlineTimes || Store.method.aladhanId == null) return null
        val key = key(YearMonth.from(date))
        val month = memory[key] ?: Store.getRaw(key)?.let { runCatching { decode(it) }.getOrNull() }?.also { memory[key] = it }
        return month?.get(date)
    }

    /**
     * Загружает текущий месяц (и следующий, если до конца месяца меньше 10 дней).
     * Возвращает true, если данные есть (загружены сейчас или раньше).
     */
    suspend fun refresh(force: Boolean = false): Boolean {
        val serial = ++requestSerial
        val source = signature()
        if (!Store.onlineTimes) return false
        val regional = RegionalTimes.source()
        if (regional != null && RegionalTimes.refresh(regional, force) { serial == requestSerial }) {
            version++
            return true
        }
        if (serial != requestSerial || !Store.onlineTimes || signature() != source) return false
        if (RegionalTimes.preferred && RegionalTimes.cityId() == "moscow" && DumrfLive.refresh(force)) {
            version++
            return true
        }
        if (serial != requestSerial || !Store.onlineTimes || signature() != source) return false
        val city = Store.location
        val expectedZone = com.zikrtime.app.ui.CityZones.known(RegionalTimes.cityId())
        if (expectedZone == null && manualZone() == null && cachedZone() == null) {
            val detected = withContext(Dispatchers.IO) { runCatching { lookupZone(city) }.getOrNull() }
            if (serial != requestSerial || !Store.onlineTimes || signature() != source) return false
            if (detected != null) {
                Store.putRaw(zoneKey(city), detected.id)
                version++
            }
        }
        val today = LocalDate.now(PrayerSchedule.zone())
        val months = buildList {
            add(YearMonth.from(today))
            if (today.lengthOfMonth() - today.dayOfMonth < 10) add(YearMonth.from(today).plusMonths(1))
        }
        val method = Store.method.aladhanId ?: return cachedZone() != null
        val school = if (Store.madhab == AsrMadhab.HANAFI) 1 else 0
        var ok = true
        for (month in months) {
            val key = key(month)
            if (!force) {
                val cached = day(month.atDay(1))
                if (cached != null) {
                    if (cachedZone() == null) Store.putRaw(zoneKey(city), cached.zone.id)
                    continue
                }
            }
            val encoded = withContext(Dispatchers.IO) {
                runCatching { download(city, month, method, school, expectedZone) }.getOrNull()
            }
            // A city/method change can start another request while this one is suspended.
            // Do not save the old city's next month under the new city's key or delete its cache.
            if (serial != requestSerial || !Store.onlineTimes || signature() != source) return false
            if (encoded == null) {
                ok = ok && Store.getRaw(key) != null
                continue
            }
            Store.putRaw(key, encoded)
            Store.putRaw(zoneKey(city), JSONObject(encoded).getString("tz"))
            memory.remove(key)
            Store.onlineUpdatedAt = System.currentTimeMillis()
        }
        // Старые месяцы и данные для других городов больше не нужны.
        val keep = (months + YearMonth.from(today).minusMonths(1)).map { key(it) }.toSet()
        val prefix = PREFIX + source + "_"
        Store.removeRawExcept(prefix, keep)
        memory.keys.removeAll { it.startsWith(prefix) && it !in keep }
        version++
        return ok
    }

    /** Resolve an arbitrary GPS point independently from the selected calculation method. */
    private fun lookupZone(city: City): ZoneId {
        val url = URL(String.format(Locale.US,
            "https://api.aladhan.com/v1/timings/%s?latitude=%.4f&longitude=%.4f",
            LocalDate.now(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT)),
            city.latitude, city.longitude))
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 7_000
        try {
            require(conn.responseCode == 200)
            val meta = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }.getJSONObject("data").getJSONObject("meta")
            require(kotlin.math.abs(meta.getDouble("latitude") - city.latitude) < 0.03 &&
                kotlin.math.abs(meta.getDouble("longitude") - city.longitude) < 0.03)
            return ZoneId.of(meta.getString("timezone"))
        } finally {
            conn.disconnect()
        }
    }

    private fun download(city: City, month: YearMonth, method: Int, school: Int, expectedZone: ZoneId?): String {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.aladhan.com/v1/calendar/%d/%d?latitude=%.4f&longitude=%.4f&method=%d&school=%d&latitudeAdjustmentMethod=3",
                month.year, month.monthValue, city.latitude, city.longitude, method, school,
            ),
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return encode(body, city, month, method, school, expectedZone)
        } finally {
            conn.disconnect()
        }
    }

    /** Ответ aladhan.com → компактный JSON: {"tz": "...", "days": {"2026-10-24": ["05:29", ...]}}. */
    private fun encode(body: String, city: City, month: YearMonth, method: Int, school: Int, expectedZone: ZoneId?): String {
        val data = JSONObject(body).getJSONArray("data")
        val days = JSONObject()
        var tz = ""
        val dateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val timings = item.getJSONObject("timings")
            val meta = item.getJSONObject("meta")
            require(kotlin.math.abs(meta.getDouble("latitude") - city.latitude) < 0.03 &&
                kotlin.math.abs(meta.getDouble("longitude") - city.longitude) < 0.03)
            require(meta.getJSONObject("method").getInt("id") == method)
            require(meta.getString("school").equals(if (school == 1) "HANAFI" else "STANDARD", true))
            val zone = ZoneId.of(meta.getString("timezone"))
            if (expectedZone != null) require(zone.rules == expectedZone.rules)
            require(tz.isEmpty() || tz == zone.id)
            tz = zone.id
            val date = LocalDate.parse(item.getJSONObject("date").getJSONObject("gregorian").getString("date"), dateFormat)
            require(YearMonth.from(date) == month && !days.has(date.toString()))
            val list = JSONArray()
            for (name in listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")) {
                // Значения вида "05:29 (MSK)" — берём только часы и минуты.
                val hhmm = timings.getString(name).trim().take(5)
                LocalTime.parse(hhmm) // проверка формата
                list.put(hhmm)
            }
            require(RegionalTimes.valid((0 until list.length()).map { LocalTime.parse(list.getString(it)) }))
            days.put(date.toString(), list)
        }
        ZoneId.of(tz) // проверка часового пояса
        require(days.length() == month.lengthOfMonth())
        return JSONObject().put("tz", tz).put("days", days).toString()
    }

    private fun decode(stored: String): Map<LocalDate, Day> {
        val root = JSONObject(stored)
        val zone = ZoneId.of(root.getString("tz"))
        val days = root.getJSONObject("days")
        val result = HashMap<LocalDate, Day>()
        for (dateKey in days.keys()) {
            val list = days.getJSONArray(dateKey)
            val times = order.indices.associate { order[it] to LocalTime.parse(list.getString(it)) }
            result[LocalDate.parse(dateKey)] = Day(zone, times)
        }
        return result
    }
}
