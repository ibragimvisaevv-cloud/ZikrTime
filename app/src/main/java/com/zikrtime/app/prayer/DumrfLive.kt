package com.zikrtime.app.prayer

import com.zikrtime.app.data.City
import com.zikrtime.app.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Официальное расписание Московской Соборной мечети (ДУМ РФ) с главной страницы dumrf.ru:
 * блок «Время намазов на сегодня … (Москва)».
 *
 * Сайт показывает только сегодняшний день, поэтому:
 *  - на сегодня берём время ровно как на сайте;
 *  - на другие дни считаем по формуле метода [CalcMethod.MOSCOW_CATHEDRAL]
 *    и добавляем сегодняшнюю разницу между сайтом и формулой (она меняется очень медленно).
 * Без интернета — просто формула, она подобрана по тому же расписанию.
 */
object DumrfLive {
    private const val URL_HOME = "https://dumrf.ru/"
    private const val KEY = "dumrf_day" // "2026-10-24|05:15,07:14,12:19,14:34,17:12,18:50"
    val MOSCOW_ZONE: ZoneId = ZoneId.of("Europe/Moscow")
    private val order = Prayer.entries

    /** Москва и ближнее Подмосковье (до 60 км от центра) — там действует расписание Соборной мечети. */
    fun isMoscow(city: City = Store.location): Boolean {
        val dLat = (city.latitude - 55.7558) * 111.0
        val dLon = (city.longitude - 37.6173) * 111.0 * cos(Math.toRadians(55.7558))
        return sqrt(dLat * dLat + dLon * dLon) < 60.0
    }

    /** Сохранённое официальное время: дата и шесть времён. */
    private fun stored(): Pair<LocalDate, Map<Prayer, LocalTime>>? {
        val raw = Store.getRaw(KEY) ?: return null
        return runCatching {
            val (date, list) = raw.split("|")
            val times = list.split(",").map { LocalTime.parse(it) }
            require(times.size == order.size)
            LocalDate.parse(date) to order.indices.associate { order[it] to times[it] }
        }.getOrNull()
    }

    /** Время ровно как на сайте — только для того дня, который был на сайте. */
    fun official(date: LocalDate): Map<Prayer, LocalTime>? {
        if (!isMoscow()) return null
        val (day, times) = stored() ?: return null
        return if (day == date) times else null
    }

    /** Разница «сайт минус формула» в минутах по каждому времени (если сверка была не позже недели назад). */
    fun corrections(calculated: (LocalDate) -> Map<Prayer, LocalTime>): Map<Prayer, Long>? {
        if (!isMoscow()) return null
        val (day, times) = stored() ?: return null
        if (abs(java.time.temporal.ChronoUnit.DAYS.between(day, LocalDate.now(MOSCOW_ZONE))) > 7) return null
        val calc = calculated(day)
        return order.associateWith { p ->
            val diff = java.time.Duration.between(calc.getValue(p), times.getValue(p)).toMinutes()
            diff.coerceIn(-10, 10)
        }
    }

    /** Загружает сегодняшнее расписание (не чаще раза в день, если не force). */
    suspend fun refresh(force: Boolean = false): Boolean {
        if (!isMoscow()) return false
        val today = LocalDate.now(MOSCOW_ZONE)
        if (!force && stored()?.first == today) return true
        val html = withContext(Dispatchers.IO) { runCatching { download() }.getOrNull() } ?: return stored() != null
        val times = parse(html) ?: return stored() != null
        Store.putRaw(KEY, today.toString() + "|" + order.joinToString(",") { times.getValue(it).toString() })
        Store.onlineUpdatedAt = System.currentTimeMillis()
        return true
    }

    private fun download(): String {
        val conn = URL(URL_HOME).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "ZikrTime (Android)")
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private val names = listOf(
        Prayer.FAJR to "Фаджр",
        Prayer.SUNRISE to "(?:Шурук|Восход)",
        Prayer.DHUHR to "Зухр",
        Prayer.ASR to "Аср",
        Prayer.MAGHRIB to "Магриб",
        Prayer.ISHA to "Иша",
    )

    /** Разбор страницы: ищем блок «Время намазов на сегодня» и шесть времён после названий. */
    fun parse(html: String): Map<Prayer, LocalTime>? {
        val text = html
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
        val start = text.indexOf("Время намаз").takeIf { it >= 0 } ?: return null
        val block = text.substring(start, minOf(text.length, start + 1500))
        val result = LinkedHashMap<Prayer, LocalTime>()
        for ((prayer, name) in names) {
            val m = Regex("$name\\s*:?\\s*(\\d{1,2}):(\\d{2})").find(block) ?: return null
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h > 23 || min > 59) return null
            result[prayer] = LocalTime.of(h, min)
        }
        // Проверка здравого смысла: время идёт по порядку
        val list = order.map { result.getValue(it) }
        if (list.zipWithNext().any { (a, b) -> !a.isBefore(b) }) return null
        return result
    }
}
