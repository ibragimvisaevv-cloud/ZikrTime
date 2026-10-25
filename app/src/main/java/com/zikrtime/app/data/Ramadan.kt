package com.zikrtime.app.data

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** Даты Рамадана по календарю Умм аль-Кура со сдвигом, который выбрал человек (как объявит его муфтият). */
object Ramadan {
    class Period(val hijriYear: Int, val start: LocalDate, val days: Int) {
        val end: LocalDate get() = start.plusDays(days - 1L)
        fun dayOf(date: LocalDate): Int? =
            if (date.isBefore(start) || date.isAfter(end)) null else (date.toEpochDay() - start.toEpochDay()).toInt() + 1
    }

    private fun period(hYear: Int): Period {
        val first = HijrahDate.of(hYear, 9, 1)
        return Period(hYear, LocalDate.from(first).plusDays(Store.ramadanShift.toLong()), first.lengthOfMonth())
    }

    /** Идущий сейчас Рамадан или ближайший следующий. null — дата вне поддерживаемого календаря. */
    fun currentOrNext(today: LocalDate): Period? = runCatching {
        val hy = HijrahDate.from(today).get(ChronoField.YEAR)
        listOf(hy - 1, hy, hy + 1).map { period(it) }.first { !today.isAfter(it.end) }
    }.getOrNull()

    fun fasted(date: LocalDate) = date.toString() in Store.ramadanFasts

    fun toggle(date: LocalDate) {
        val key = date.toString()
        Store.ramadanFasts = Store.ramadanFasts.let { if (key in it) it - key else it + key }
    }
}
