package com.zikrtime.app.prayer

import android.content.Context
import com.zikrtime.app.data.Cities
import com.zikrtime.app.data.City
import com.zikrtime.app.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** One published row belongs to one locality and civil date. No extrapolation across either. */
object RegionalTimes {
    @Volatile private lateinit var context: Context
    private var lastCity: City? = null
    private var lastId: String? = null
    private var lastGroup: String? = null
    private var bundledCity: String? = null
    private var bundled = emptyMap<LocalDate, List<LocalTime>>()
    private val downloaded = mutableMapOf<String, Map<LocalDate, List<LocalTime>>>()

    @Synchronized fun init(app: Context) {
        context = app
        Cities.load(app)
    }

    var preferred: Boolean
        get() = Store.getRaw("regional_prayers_preferred") != "false"
        set(value) = Store.putRaw("regional_prayers_preferred", value.toString())

    /** Legacy GPS locations carry a display name and coordinates but no catalog id. */
    @Synchronized fun cityId(city: City = Store.location): String? {
        if (!::context.isInitialized) return city.id
        if (city == lastCity) return lastId
        val entries = Cities.load(context).second
        val match = entries.firstOrNull { entry ->
            (if (city.id != null) entry.id == city.id else entry.names.values.any { name ->
                val reported = city.name.trim()
                name.equals(reported, ignoreCase = true) ||
                    name.substringBefore(" (").substringBefore(",").equals(reported, ignoreCase = true)
            }) &&
                sameLocality(city.latitude, city.longitude, entry.lat, entry.lon)
        }
        lastCity = city
        lastId = match?.id
        lastGroup = match?.group
        return lastId
    }

    /** Region defaults affect only installations without an explicitly selected method. */
    @Synchronized fun defaultMethod(): CalcMethod {
        val id = cityId()
        return when {
            id == "cairo" -> CalcMethod.EGYPT
            id == "mecca" || id == "medina" -> CalcMethod.MAKKAH
            id == "new_york" -> CalcMethod.ISNA
            lastGroup == "turkey" -> CalcMethod.TURKEY
            lastGroup == "russia" || id == "moscow" -> CalcMethod.RUSSIA
            else -> CalcMethod.MWL
        }
    }

    /** A matching name or explicit selection is required as well as nearby coordinates. */
    internal fun sameLocality(lat: Double, lon: Double, centerLat: Double, centerLon: Double): Boolean {
        val dy = Math.toRadians(lat - centerLat)
        val dx = Math.toRadians(lon - centerLon)
        val a = kotlin.math.sin(dy / 2).let { it * it } + kotlin.math.cos(Math.toRadians(lat)) *
            kotlin.math.cos(Math.toRadians(centerLat)) * kotlin.math.sin(dx / 2).let { it * it }
        return 12742 * kotlin.math.asin(kotlin.math.sqrt(a.coerceIn(0.0, 1.0))) <= 20.0
    }

    fun source(): RegionalFeeds.Source? = if (preferred) cityId()?.let(RegionalFeeds::source) else null

    private fun key(source: RegionalFeeds.Source, month: YearMonth) = "rt2_${source.cityId}_$month"

    internal fun valid(values: List<LocalTime>): Boolean {
        if (values.size != 6) return false
        val (fajr, sunrise, _, _, maghrib) = values
        val isha = values[5]
        val beforeSunrise = fajr < sunrise || (fajr.hour >= 20 && sunrise < LocalTime.NOON)
        val afterSunset = isha > maghrib || (isha.hour < 6 && maghrib > LocalTime.NOON)
        return beforeSunrise && afterSunset && values.subList(1, 5).zipWithNext().all { (a, b) -> b > a }
    }

    @Synchronized private fun readBundled(cityId: String): Map<LocalDate, List<LocalTime>> {
        if (!::context.isInitialized) return emptyMap()
        if (bundledCity != cityId) {
            val values = mutableMapOf<LocalDate, List<LocalTime>>()
            val names = context.assets.list("prayer").orEmpty().toSet()
            for (file in listOf("regional.tsv", "published-2026.tsv").filter { it in names }) {
                context.assets.open("prayer/$file").bufferedReader().useLines { lines ->
                    lines.filter { it.startsWith("$cityId\t") }.forEach { line ->
                        val cells = line.split('\t')
                        if (cells.size == 8) {
                            val date = LocalDate.parse(cells[1])
                            val times = cells.drop(2).map(LocalTime::parse)
                            if (valid(times)) values[date] = times
                        }
                    }
                }
            }
            bundled = values
            bundledCity = cityId
        }
        return bundled
    }

    @Synchronized private fun cached(source: RegionalFeeds.Source, month: YearMonth): Map<LocalDate, List<LocalTime>> {
        val k = key(source, month)
        return downloaded.getOrPut(k) {
            runCatching {
                Store.getRaw(k).orEmpty().lineSequence().filter(String::isNotBlank).associate { line ->
                    val parts = line.split(',')
                    val date = LocalDate.parse(parts.first())
                    val times = parts.drop(1).map(LocalTime::parse)
                    require(YearMonth.from(date) == month && valid(times))
                    date to times
                }
            }.getOrDefault(emptyMap())
        }
    }

    fun day(date: LocalDate): OnlineTimes.Day? {
        val source = source() ?: return null
        val month = cached(source, YearMonth.from(date))
        val times = (if (month.isEmpty()) readBundled(source.cityId)[date] else month[date]) ?: return null
        return OnlineTimes.Day(source.zone, Prayer.entries.zip(times).toMap())
    }

    suspend fun refresh(source: RegionalFeeds.Source, force: Boolean, currentRequest: () -> Boolean): Boolean {
        val today = LocalDate.now(com.zikrtime.app.ui.CityZones.civil(source.zone, LocalDate.now(source.zone)))
        val current = YearMonth.from(today)
        for (month in listOf(current, current.plusMonths(1))) {
            val k = key(source, month)
            val last = Store.getRaw(k + "_updated")?.toLongOrNull() ?: 0L
            val now = Instant.now().toEpochMilli()
            if (!force && now - last in 0L until 86_400_000L && cached(source, month).isNotEmpty()) continue
            val days = withContext(Dispatchers.IO) { runCatching { RegionalFeeds.download(source, month) }.getOrNull() }
            if (!currentRequest() || !Store.onlineTimes || this.source() != source) return false
            if (days.isNullOrEmpty() || days.any { (date, row) -> YearMonth.from(date) != month || !valid(row) }) continue
            val updated = days
            Store.putRaw(k, updated.entries.sortedBy { it.key }.joinToString("\n") { (date, row) ->
                date.toString() + "," + row.joinToString(",")
            })
            synchronized(this) { downloaded[k] = updated }
            Store.putRaw(k + "_updated", now.toString())
        }
        // Keep city switches offline-capable without accumulating years of preferences.
        val keep = (-1L..1L).flatMap { offset ->
            val key = key(source, current.plusMonths(offset))
            listOf(key, key + "_updated")
        }.toSet()
        val prefix = "rt2_${source.cityId}_"
        Store.removeRawExcept(prefix, keep)
        synchronized(this) { downloaded.keys.removeAll { it.startsWith(prefix) && it !in keep } }
        return day(today) != null
    }
}
