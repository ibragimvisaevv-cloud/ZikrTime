package com.zikrtime.app.prayer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PrayerNextTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val day = LocalDate.of(2026, 6, 21)

    private fun schedule(date: LocalDate, lateIsha: Boolean): PrayerTimes {
        val hours = mapOf(Prayer.FAJR to 3, Prayer.SUNRISE to 5, Prayer.DHUHR to 12,
            Prayer.ASR to 17, Prayer.MAGHRIB to 22, Prayer.ISHA to 23)
        return PrayerTimes(date, hours.mapValues { (prayer, hour) ->
            if (prayer == Prayer.ISHA && lateIsha) date.plusDays(1).atTime(0, 30).atZone(zone)
            else date.atTime(hour, 0).atZone(zone)
        })
    }

    @Test fun previousDaysIshaRemainsNextAfterMidnightUntilItsExactBoundary() {
        val next = PrayerSchedule.nextFrom(day.atTime(0, 10).atZone(zone)) { schedule(it, true) }
        assertEquals(Prayer.ISHA, next.first)
        assertEquals(day.atTime(0, 30).atZone(zone), next.second)
        val afterIsha = PrayerSchedule.nextFrom(day.atTime(0, 30).atZone(zone)) { schedule(it, true) }
        assertEquals(Prayer.FAJR, afterIsha.first)
        assertEquals(day.atTime(3, 0).atZone(zone), afterIsha.second)
    }

    @Test fun ordinaryLateEveningStillSelectsTomorrowAndUsesThePassedLocalDate() {
        val next = PrayerSchedule.nextFrom(day.atTime(23, 45).atZone(zone)) { schedule(it, false) }
        assertEquals(Prayer.FAJR, next.first)
        assertEquals(day.plusDays(1).atTime(3, 0).atZone(zone), next.second)
        // A future carried prayer must not outrank an earlier current-day prayer.
        val earliest = PrayerSchedule.nextFrom(day.atTime(0, 10).atZone(zone)) { date ->
            val times = schedule(date, false)
            if (date == day.minusDays(1)) times.copy(times = times.times +
                (Prayer.ISHA to day.atTime(4, 0).atZone(zone))) else times
        }
        assertEquals(Prayer.FAJR, earliest.first)
        assertEquals(day.atTime(3, 0).atZone(zone), earliest.second)
        val corrected = PrayerSchedule.nextFrom(day.atTime(0, 10).atZone(zone)) { date ->
            val times = schedule(date, true)
            if (date == day.minusDays(1)) times.copy(times = times.times +
                (Prayer.MAGHRIB to day.atTime(0, 20).atZone(zone))) else times
        }
        assertEquals(Prayer.MAGHRIB, corrected.first)
        assertEquals(day.atTime(0, 20).atZone(zone), corrected.second)
    }
}
