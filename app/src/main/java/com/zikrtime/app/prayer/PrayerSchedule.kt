package com.zikrtime.app.prayer

import com.zikrtime.app.data.Store
import com.zikrtime.app.screentime.PrayerStart
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Время намаза для выбранного города и метода расчёта. */
object PrayerSchedule {

    fun forDate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): PrayerTimes {
        val loc = Store.location
        return PrayerCalculator.calculate(date, loc.latitude, loc.longitude, Store.method, Store.madhab, zone)
    }

    fun today(): PrayerTimes = forDate(LocalDate.now())

    /** Ближайший обязательный намаз строго после момента [after]. */
    fun next(after: ZonedDateTime = ZonedDateTime.now()): Pair<Prayer, ZonedDateTime> {
        val zone = after.zone
        for (offset in 0L..2L) {
            val times = forDate(after.toLocalDate().plusDays(offset), zone)
            for (p in Prayer.obligatory) {
                if (times[p].isAfter(after)) return p to times[p]
            }
        }
        // Сюда не попадаем при нормальных координатах; на всякий случай — фаджр завтра.
        val tomorrow = forDate(after.toLocalDate().plusDays(1), zone)
        return Prayer.FAJR to tomorrow[Prayer.FAJR]
    }

    /** Окна намазов за вчера и сегодня — для блокировки соцсетей (иша может перейти за полночь). */
    fun recentStarts(now: ZonedDateTime = ZonedDateTime.now()): List<PrayerStart> {
        val zone = now.zone
        val yesterday = forDate(now.toLocalDate().minusDays(1), zone)
        val today = forDate(now.toLocalDate(), zone)
        return listOf(PrayerStart(yesterday.date, Prayer.ISHA, yesterday[Prayer.ISHA].toInstant())) +
            Prayer.obligatory.map { PrayerStart(today.date, it, today[it].toInstant()) }
    }
}
