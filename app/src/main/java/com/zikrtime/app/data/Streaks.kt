package com.zikrtime.app.data

import java.time.LocalDate

/** Подсчёт серий дней подряд. */
object Streaks {

    /**
     * Текущая серия: сколько дней подряд до сегодняшнего включительно.
     * Если сегодня ещё не отмечено, серия не обрывается — считаем от вчерашнего дня.
     */
    fun current(days: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in days) today else today.minusDays(1)
        var count = 0
        while (day in days) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /** Лучшая серия за всё сохранённое время. */
    fun best(days: Set<LocalDate>): Int {
        var best = 0
        for (day in days) {
            if (day.minusDays(1) in days) continue // считаем только от начала каждой серии
            var length = 0
            var d = day
            while (d in days) {
                length++
                d = d.plusDays(1)
            }
            if (length > best) best = length
        }
        return best
    }

    /** Храним не больше 400 последних дней, чтобы настройки не разрастались. */
    fun trim(days: Set<LocalDate>): Set<LocalDate> =
        if (days.size <= 400) days else days.sortedDescending().take(400).toSet()
}
