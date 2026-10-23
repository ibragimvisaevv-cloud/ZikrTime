package com.zikrtime.app.prayer

import com.zikrtime.app.data.Store
import com.zikrtime.app.screentime.PrayerStart
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Время намаза для выбранного города и метода расчёта. */
object PrayerSchedule {

    /**
     * Время на дату: из интернета, если данные загружены, иначе расчёт на телефоне.
     * К результату добавляются ручные поправки пользователя.
     */
    fun forDate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): PrayerTimes {
        val base = OnlineTimes.day(date)?.let { fromOnline(date, it, zone) } ?: calculated(date, zone)
        val adjusted = base.times.mapValues { (p, t) -> t.plusMinutes(Store.adjustment(p).toLong()) }
        return PrayerTimes(date, adjusted)
    }

    /** Источник данных для подписи на экране. */
    fun isOnline(date: LocalDate = LocalDate.now()): Boolean = OnlineTimes.day(date) != null

    private fun calculated(date: LocalDate, zone: ZoneId): PrayerTimes {
        val loc = Store.location
        return PrayerCalculator.calculate(date, loc.latitude, loc.longitude, Store.method, Store.madhab, zone)
    }

    private fun fromOnline(date: LocalDate, day: OnlineTimes.Day, zone: ZoneId): PrayerTimes {
        var previous: ZonedDateTime? = null
        val times = LinkedHashMap<Prayer, ZonedDateTime>()
        for (p in Prayer.entries) {
            var t = date.atTime(day.times.getValue(p)).atZone(day.zone)
            // Летом на севере иша бывает после полуночи — значит, это уже следующие сутки.
            if (previous != null && !t.isAfter(previous)) t = t.plusDays(1)
            previous = t
            times[p] = t.withZoneSameInstant(zone)
        }
        return PrayerTimes(date, times)
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
