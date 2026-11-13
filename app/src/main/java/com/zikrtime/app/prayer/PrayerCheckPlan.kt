package com.zikrtime.app.prayer

import com.zikrtime.app.data.PrayerMark
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

data class PrayerCheckOccurrence(
    val day: LocalDate,
    val prayer: Prayer,
    val due: Instant,
    val expires: Instant,
) {
    val key: String get() = "$day:${prayer.name}"
    fun encode(): String = "$day|${prayer.name}|${due.toEpochMilli()}|${expires.toEpochMilli()}"
}

/** Local journal dates belong to the selected city, including prayers crossing midnight. */
object PrayerCheckPlan {
    val delays = listOf(15, 30, 60)

    fun occurrence(day: LocalDate, prayer: Prayer, delayMinutes: Int,
                   timesForDate: (LocalDate) -> PrayerTimes): PrayerCheckOccurrence? {
        if (!prayer.isPrayer || delayMinutes !in delays) return null
        val times = timesForDate(day)
        val due = times[prayer].toInstant().plusSeconds(delayMinutes * 60L)
        val end = when (prayer) {
            Prayer.FAJR -> times[Prayer.SUNRISE]
            Prayer.DHUHR -> times[Prayer.ASR]
            Prayer.ASR -> times[Prayer.MAGHRIB]
            Prayer.MAGHRIB -> times[Prayer.ISHA]
            Prayer.ISHA -> timesForDate(day.plusDays(1))[Prayer.FAJR]
            Prayer.SUNRISE -> return null
        }.toInstant()
        val expires = minOf(end, due.plus(Duration.ofMinutes(90)))
        return if (due < expires) PrayerCheckOccurrence(day, prayer, due, expires) else null
    }

    fun next(now: ZonedDateTime, delayMinutes: Int, history: String?,
             marks: (LocalDate) -> Map<Prayer, PrayerMark>,
             timesForDate: (LocalDate) -> PrayerTimes): PrayerCheckOccurrence? =
        (-1L..2L).asSequence().flatMap { offset ->
            val day = now.toLocalDate().plusDays(offset)
            Prayer.obligatory.asSequence().mapNotNull { occurrence(day, it, delayMinutes, timesForDate) }
        }.filter { it.due > now.toInstant() && unmarked(it, marks) && !delivered(history, it) }
            .minByOrNull { it.due }

    fun canDeliver(saved: PrayerCheckOccurrence, expected: PrayerCheckOccurrence?, now: Instant,
                   enabled: Boolean, mark: PrayerMark?, history: String?): Boolean =
        enabled && saved == expected && now >= saved.due && now < saved.expires &&
            (mark == null || mark == PrayerMark.UNMARKED) && !delivered(history, saved)

    fun unmarked(item: PrayerCheckOccurrence, marks: (LocalDate) -> Map<Prayer, PrayerMark>): Boolean =
        marks(item.day)[item.prayer].let { it == null || it == PrayerMark.UNMARKED }

    fun delivered(history: String?, item: PrayerCheckOccurrence): Boolean =
        item.key in history.orEmpty().split(',')

    fun recordDelivery(history: String?, item: PrayerCheckOccurrence): String =
        (history.orEmpty().split(',').filter { it.isNotEmpty() && it != item.key } + item.key)
            .takeLast(100).joinToString(",")

    fun decode(raw: String?): PrayerCheckOccurrence? = runCatching {
        val fields = raw.orEmpty().split('|')
        val prayer = Prayer.valueOf(fields[1])
        require(prayer.isPrayer && fields.size == 4)
        PrayerCheckOccurrence(LocalDate.parse(fields[0]), prayer,
            Instant.ofEpochMilli(fields[2].toLong()), Instant.ofEpochMilli(fields[3].toLong()))
    }.getOrNull()
}
