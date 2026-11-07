package com.zikrtime.app.data

import com.zikrtime.app.prayer.Prayer
import java.time.LocalDate

enum class PrayerMark { UNMARKED, DONE, MISSED, MADE_UP }

object PrayerJournal {
    const val PREFIX = "prayer_journal_"

    fun decode(tokens: Set<String>): Map<Prayer, PrayerMark> = buildMap {
        for (prayer in Prayer.obligatory) {
            when {
                "${prayer.name}:made_up" in tokens -> put(prayer, PrayerMark.MADE_UP)
                "${prayer.name}:missed" in tokens -> put(prayer, PrayerMark.MISSED)
                prayer.name in tokens -> put(prayer, PrayerMark.DONE)
            }
        }
    }

    fun encode(marks: Map<Prayer, PrayerMark>): Set<String> = buildSet {
        for ((prayer, mark) in marks) if (prayer.isPrayer) when (mark) {
            PrayerMark.DONE -> add(prayer.name)
            PrayerMark.MISSED -> add("${prayer.name}:missed")
            PrayerMark.MADE_UP -> add("${prayer.name}:made_up")
            PrayerMark.UNMARKED -> Unit
        }
    }

    /** Old versions retained recent individual checks and a longer set of fully completed dates. */
    fun legacy(existing: Map<String, *>, includeRecentChecks: Boolean = true): Map<LocalDate, Set<String>> = buildMap {
        val completed = Prayer.obligatory.map { it.name }.toSet()
        for (raw in (existing["prayer_days"] as? Set<*>).orEmpty()) {
            val token = raw as? String ?: continue
            val date = runCatching { LocalDate.parse(token) }.getOrNull() ?: continue
            put(date, completed)
        }
        if (!includeRecentChecks) return@buildMap
        for ((key, value) in existing) {
            if (!key.startsWith("prayed_")) continue
            val date = runCatching { LocalDate.parse(key.removePrefix("prayed_")) }.getOrNull() ?: continue
            val checks = (value as? Set<*>).orEmpty().filterIsInstance<String>().intersect(completed)
            // Individual checks take precedence if a user edited a day later.
            if (checks.isEmpty()) remove(date) else put(date, checks)
        }
    }
}
