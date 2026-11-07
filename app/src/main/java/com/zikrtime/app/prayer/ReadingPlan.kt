package com.zikrtime.app.prayer

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

enum class ReadingKind(val defaultMinute: Int, val route: String) {
    BEDTIME(21 * 60 + 30, "reading_bedtime"),
    FRIDAY(9 * 60, "reading_friday")
}

object ReadingPlan {
    fun at(date: LocalDate, minute: Int, zone: ZoneId): ZonedDateTime =
        date.atTime(LocalTime.ofSecondOfDay(minute * 60L)).atZone(zone)

    fun dueDate(kind: ReadingKind, minute: Int, instant: Instant, now: ZonedDateTime): LocalDate? {
        val due = instant.atZone(now.zone)
        val age = Duration.between(due, now)
        if (age.isNegative || age > Duration.ofMinutes(90)) return null
        val expected = next(kind, minute, due.minusSeconds(1))
        return due.toLocalDate().takeIf { expected.toInstant() == instant }
    }

    fun delivered(history: String?, day: LocalDate): Boolean = day.toString() in history.orEmpty().split(',')

    fun recordDelivery(history: String?, day: LocalDate): String =
        (history.orEmpty().split(',').filter { it.isNotEmpty() && it != day.toString() } + day.toString())
            .takeLast(14).joinToString(",")

    fun next(kind: ReadingKind, minute: Int, now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate()
        val day = if (kind == ReadingKind.FRIDAY) today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)) else today
        val first = at(day, minute, now.zone)
        if (first.isAfter(now)) return first
        return at(if (kind == ReadingKind.FRIDAY) day.plusWeeks(1) else day.plusDays(1), minute, now.zone)
    }
}
