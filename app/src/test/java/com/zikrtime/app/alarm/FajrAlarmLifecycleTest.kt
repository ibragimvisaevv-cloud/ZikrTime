package com.zikrtime.app.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class FajrAlarmLifecycleTest {
    private val due = 1_760_000_000_000L

    @Test fun acceptsTheCurrentAlarmAndBriefSystemDelay() {
        assertTrue(validFajrDelivery(true, due, due, due))
        assertTrue(validFajrDelivery(true, due, due, due + 5 * 60_000L))
    }

    @Test fun disposedSnoozesTestsAndOutdatedAlarmsNeverStartAudio() {
        assertFalse(validFajrDelivery(false, due, due, due))
        assertFalse(validFajrDelivery(true, due, 0L, due))
        assertFalse(validFajrDelivery(true, due, due + 86_400_000L, due))
        assertFalse(validFajrDelivery(true, 0L, 0L, due))
    }

    @Test fun substantiallyLateTestsAndSnoozesAndPrematureDeliveriesAreSilent() {
        assertFalse(validFajrDelivery(true, due, due, due + 15 * 60_000L))
        assertFalse(validFajrDelivery(true, due, due, due - 1L))
        assertFalse(validFajrDelivery(true, due, due, due + 5 * 60_000L + 1L))
    }

    @Test fun delayedDailyAlarmWakesUntilFajrPlusThirtyMinutesAndBeforeSunrise() {
        val minute = 60_000L
        val fajr = due + 90 * minute
        val sunrise = fajr + 65 * minute
        assertTrue(validFajrDelivery(true, due, due, fajr - minute, fajr, sunrise))
        assertTrue(validFajrDelivery(true, due, due, fajr + 30 * minute, fajr, sunrise))
        assertFalse(validFajrDelivery(true, due, due, fajr + 31 * minute, fajr, sunrise))
        assertFalse(validFajrDelivery(true, due, due, fajr + 15 * minute, fajr, fajr + 15 * minute))
        assertFalse(validFajrDelivery(false, due, due, fajr, fajr, sunrise))
        assertFalse(validFajrDelivery(true, due, 0L, fajr, fajr, sunrise))
    }

    @Test fun usesCityWeekdayAcrossTheDateLine() {
        val city = ZoneId.of("Pacific/Honolulu")
        val friday = LocalDate.of(2026, 11, 6)
        val phoneTime = friday.atTime(4, 48).atZone(city).withZoneSameInstant(ZoneId.of("Pacific/Kiritimati"))
        assertEquals(DayOfWeek.SATURDAY, phoneTime.dayOfWeek)
        val alarm = nextCityFajr(phoneTime, city, setOf(DayOfWeek.FRIDAY), 10) {
            it.atTime(5, 0).atZone(city)
        }
        assertEquals(friday.atTime(4, 50).atZone(city), alarm)
    }
}
