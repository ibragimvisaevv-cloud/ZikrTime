package com.zikrtime.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

data class QuranReadingDay(val millis: Long = 0, val verses: Set<String> = emptySet())

/** Personal measurements only. Opening or scrolling never marks a verse as read. */
data class QuranReadingHistory(val days: Map<LocalDate, QuranReadingDay> = emptyMap()) {
    val uniqueVerses: Set<String> get() = days.values.flatMap { it.verses }.toSet()

    fun mark(day: LocalDate, surah: Int, first: Int, last: Int, verseCount: Int): QuranReadingHistory {
        require(surah in 1..114 && first in 1..verseCount && last in first..verseCount)
        val old = days[day] ?: QuranReadingDay()
        return copy(days = days + (day to old.copy(verses = old.verses + (first..last).map { surah.toString() + ":" + it })))
    }

    /** Split elapsed time at local midnight, including short/long DST days. */
    fun addTime(start: Instant, millis: Long, zone: ZoneId): QuranReadingHistory {
        if (millis <= 0) return this
        val result = days.toMutableMap()
        var cursor = start
        var remaining = millis
        while (remaining > 0) {
            val day = cursor.atZone(zone).toLocalDate()
            val midnight = day.plusDays(1).atStartOfDay(zone).toInstant()
            val part = minOf(remaining, java.time.Duration.between(cursor, midnight).toMillis().coerceAtLeast(1))
            val old = result[day] ?: QuranReadingDay()
            result[day] = old.copy(millis = old.millis + part)
            cursor = cursor.plusMillis(part)
            remaining -= part
        }
        return copy(days = result)
    }

    fun encode(): String {
        val root = JSONObject()
        days.toSortedMap().forEach { (day, value) ->
            root.put(day.toString(), JSONObject().put("ms", value.millis).put("verses", JSONArray(value.verses.sorted())))
        }
        return root.toString()
    }

    companion object {
        fun decode(raw: String?): QuranReadingHistory {
            val root = runCatching { JSONObject(raw ?: "{}") }.getOrDefault(JSONObject())
            val days = root.keys().asSequence().mapNotNull { key -> runCatching {
                val day = LocalDate.parse(key)
                val value = root.getJSONObject(key)
                val verses = value.optJSONArray("verses") ?: JSONArray()
                day to QuranReadingDay(value.optLong("ms").coerceAtLeast(0),
                    (0 until verses.length()).map { verses.optString(it) }
                        .filter { Regex("[1-9][0-9]{0,2}:[1-9][0-9]{0,2}").matches(it) }.toSet())
            }.getOrNull() }.toMap()
            return QuranReadingHistory(days)
        }
    }
}

data class QuranReadingTimer(val remaining: Long = 0, val paused: Boolean = false, val finished: Boolean = false) {
    /** Huge elapsed gaps are suspension, not reading. A paused/finished timer pauses the session too. */
    fun readingMillis(delta: Long, active: Boolean): Long {
        if (!active || paused || finished || delta !in 1..5000) return 0
        return if (remaining > 0) minOf(delta, remaining) else delta
    }

    fun advance(readingMillis: Long): QuranReadingTimer {
        if (remaining <= 0 || paused || finished || readingMillis <= 0) return this
        val next = (remaining - readingMillis).coerceAtLeast(0)
        return copy(remaining = next, finished = next == 0L)
    }
}

object QuranStatistics {
    const val KEY = "quran_statistics_v1"
    fun history(): QuranReadingHistory = QuranReadingHistory.decode(Store.getRaw(KEY))

    fun recordTime(start: Instant, millis: Long, zone: ZoneId = ZoneId.systemDefault()) {
        if (Store.personalProgressEnabled && millis > 0) Store.putRaw(KEY, history().addTime(start, millis, zone).encode())
    }

    fun mark(day: LocalDate, surah: Int, first: Int, last: Int, count: Int) {
        if (Store.personalProgressEnabled) Store.putRaw(KEY, history().mark(day, surah, first, last, count).encode())
    }

    fun clear() = Store.putRaw(KEY, "{}")
}
