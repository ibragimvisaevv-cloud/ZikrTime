package com.zikrtime.app.prayer

import com.zikrtime.app.data.Store
import com.zikrtime.app.screentime.PrayerStart
import com.zikrtime.app.ui.CityZones
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object PrayerSchedule {
    private fun locationZone(): ZoneId = CityZones.known(RegionalTimes.cityId()) ?: OnlineTimes.manualZone() ?: OnlineTimes.cachedZone() ?: ZoneId.systemDefault()

    fun zone(): ZoneId {
        val zone = locationZone()
        return CityZones.civil(zone, LocalDate.now(zone))
    }

    /** Priority applies to the exact city and date; a calculation is always labelled as such. */
    private fun published(date: LocalDate): OnlineTimes.Day? {
        RegionalTimes.day(date)?.let { return it }
        if (RegionalTimes.preferred && RegionalTimes.cityId() == "moscow") {
            DumrfLive.official(date)?.let { return OnlineTimes.Day(DumrfLive.MOSCOW_ZONE, it) }
        }
        return null
    }

    fun publisher(date: LocalDate): String? {
        if (published(date) == null) return null
        return RegionalTimes.source()?.name ?: "dumrf.ru"
    }

    fun forDate(date: LocalDate, zone: ZoneId = CityZones.civil(locationZone(), date)): PrayerTimes {
        val loc = Store.location
        val day = published(date) ?: OnlineTimes.day(date)
        val base = if (day == null) {
            PrayerCalculator.calculate(date, loc.latitude, loc.longitude, Store.method, Store.madhab, zone)
        } else fromOnline(date, day, zone)
        val adjusted = base.times.mapValues { (prayer, time) -> time.plusMinutes(Store.adjustment(prayer).toLong()) }
        return base.copy(times = adjusted)
    }

    internal fun fromOnline(date: LocalDate, day: OnlineTimes.Day, zone: ZoneId): PrayerTimes {
        val times = Prayer.entries.associateWith { prayer ->
            val clock = day.times.getValue(prayer)
            val localDate = when {
                prayer == Prayer.FAJR && clock > day.times.getValue(Prayer.SUNRISE) -> date.minusDays(1)
                prayer == Prayer.ISHA && clock < day.times.getValue(Prayer.MAGHRIB) -> date.plusDays(1)
                else -> date
            }
            localDate.atTime(clock).atZone(CityZones.civil(day.zone, localDate)).withZoneSameInstant(zone)
        }
        return PrayerTimes(date, times)
    }

    fun today(): PrayerTimes = forDate(LocalDate.now(zone()))

    fun next(after: ZonedDateTime = ZonedDateTime.now()): Pair<Prayer, ZonedDateTime> {
        val local = after.withZoneSameInstant(zone())
        return nextFrom(local) { date -> forDate(date, local.zone) }
    }

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

    /** Prayer dates follow the selected city even if the OS clock displays another zone. */
    fun recentStarts(now: ZonedDateTime = ZonedDateTime.now()): List<PrayerStart> {
        val local = now.withZoneSameInstant(zone())
        val yesterday = forDate(local.toLocalDate().minusDays(1), local.zone)
        val today = forDate(local.toLocalDate(), local.zone)
        val tomorrow = forDate(local.toLocalDate().plusDays(1), local.zone)
        return listOf(PrayerStart(yesterday.date, Prayer.ISHA, yesterday[Prayer.ISHA].toInstant())) +
            Prayer.obligatory.map { PrayerStart(today.date, it, today[it].toInstant()) } +
            PrayerStart(tomorrow.date, Prayer.FAJR, tomorrow[Prayer.FAJR].toInstant())
    }
}
