package com.zikrtime.app.prayer

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

    private const val PREFIX = "otimes_"
    private val order = listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
    private val memory = HashMap<String, Map<LocalDate, Day>>()

    /** Данные зависят от координат, метода и мазхаба. */
    private fun signature(city: City = Store.location): String =
        String.format(
            Locale.US, "%.3f_%.3f_%d_%d",
            city.latitude, city.longitude, Store.method.aladhanId, Store.madhab.ordinal,
        )

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
        // Московская Соборная мечеть: официальное расписание берём с dumrf.ru, а не с aladhan.com.
        if (Store.method.aladhanId == null) return DumrfLive.refresh(force).also { version++ }
        val today = LocalDate.now()
        val months = buildList {
            add(YearMonth.from(today))
            if (today.lengthOfMonth() - today.dayOfMonth < 10) add(YearMonth.from(today).plusMonths(1))
        }
        val city = Store.location
        val method = Store.method.aladhanId ?: return false
        val school = if (Store.madhab == AsrMadhab.HANAFI) 1 else 0
        var ok = true
        for (month in months) {
            val key = key(month)
            if (!force && Store.getRaw(key) != null) continue
            val encoded = withContext(Dispatchers.IO) {
                runCatching { download(city, month, method, school) }.getOrNull()
            }
            // A city/method change can start another request while this one is suspended.
            // Do not save the old city's next month under the new city's key or delete its cache.
            if (serial != requestSerial || !Store.onlineTimes || signature() != source) return false
            if (encoded == null) {
                ok = ok && Store.getRaw(key) != null
                continue
            }
            Store.putRaw(key, encoded)
            memory.remove(key)
            Store.onlineUpdatedAt = System.currentTimeMillis()
        }
        // Старые месяцы и данные для других городов больше не нужны.
        val keep = (months + YearMonth.from(today).minusMonths(1)).map { key(it) }.toSet()
        Store.removeRawExcept(PREFIX, keep)
        version++
        return ok
    }

    private fun download(city: City, month: YearMonth, method: Int, school: Int): String {
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
            return encode(body)
        } finally {
            conn.disconnect()
        }
    }

    /** Ответ aladhan.com → компактный JSON: {"tz": "...", "days": {"2026-10-24": ["05:29", ...]}}. */
    private fun encode(body: String): String {
        val data = JSONObject(body).getJSONArray("data")
        val days = JSONObject()
        var tz = ""
        val dateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val timings = item.getJSONObject("timings")
            tz = item.getJSONObject("meta").getString("timezone")
            val date = LocalDate.parse(item.getJSONObject("date").getJSONObject("gregorian").getString("date"), dateFormat)
            val list = JSONArray()
            for (name in listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")) {
                // Значения вида "05:29 (MSK)" — берём только часы и минуты.
                val hhmm = timings.getString(name).trim().take(5)
                LocalTime.parse(hhmm) // проверка формата
                list.put(hhmm)
            }
            days.put(date.toString(), list)
        }
        ZoneId.of(tz) // проверка часового пояса
        require(days.length() > 0)
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
