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
        val base = if (Store.method == CalcMethod.MOSCOW_CATHEDRAL) moscow(date, zone)
        else OnlineTimes.day(date)?.let { fromOnline(date, it, zone) } ?: calculated(date, zone)
        val adjusted = base.times.mapValues { (p, t) -> t.plusMinutes(Store.adjustment(p).toLong()) }
        return PrayerTimes(date, adjusted)
    }

    /** Источник данных для подписи на экране. */
    fun isOnline(date: LocalDate = LocalDate.now()): Boolean =
        if (Store.method == CalcMethod.MOSCOW_CATHEDRAL) Store.onlineTimes && DumrfLive.isMoscow() && moscowCorrections() != null
        else OnlineTimes.day(date) != null

    /** Московская Соборная мечеть: на сегодня — как на сайте dumrf.ru, на другие дни — формула с поправкой по сайту. */
    private fun moscow(date: LocalDate, zone: ZoneId): PrayerTimes {
        val calc = calculated(date, zone)
        // В расписании ДУМ РФ аср стандартный; если выбран ханафитский аср — его берём из расчёта.
        val hanafi = Store.madhab == AsrMadhab.HANAFI
        if (!Store.onlineTimes) return calc
        DumrfLive.official(date)?.let { official ->
            return PrayerTimes(date, official.mapValues { (p, t) ->
                if (hanafi && p == Prayer.ASR) calc[p] else date.atTime(t).atZone(DumrfLive.MOSCOW_ZONE).withZoneSameInstant(zone)
            })
        }
        val corr = moscowCorrections() ?: return calc
        return PrayerTimes(date, calc.times.mapValues { (p, t) -> if (hanafi && p == Prayer.ASR) t else t.plusMinutes(corr[p] ?: 0L) })
    }

    private fun moscowCorrections(): Map<Prayer, Long>? = DumrfLive.corrections { d ->
        calculated(d, DumrfLive.MOSCOW_ZONE).times.mapValues { it.value.toLocalTime() }
    }

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
    fun next(after: ZonedDateTime = ZonedDateTime.now()): Pair<Prayer, ZonedDateTime> =
        nextFrom(after) { date -> forDate(date, after.zone) }

    /** Same selection for UI/alarms; provider keeps the midnight boundary independently testable. */
    internal fun nextFrom(after: ZonedDateTime, timesForDate: (LocalDate) -> PrayerTimes): Pair<Prayer, ZonedDateTime> {
        val date = after.toLocalDate()
        // Summer Isha and manual corrections may carry a prayer past midnight.
        // Compare instants across day boundaries instead of assuming date/enum order.
        val next = (-1L..2L).asSequence().flatMap { offset ->
            val times = timesForDate(date.plusDays(offset))
            Prayer.obligatory.asSequence().map { it to times[it] }
        }.filter { it.second.isAfter(after) }.minByOrNull { it.second.toInstant() }
        if (next != null) return next
        // Сюда не попадаем при нормальных координатах; на всякий случай — фаджр завтра.
        return Prayer.FAJR to timesForDate(date.plusDays(1))[Prayer.FAJR]
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
