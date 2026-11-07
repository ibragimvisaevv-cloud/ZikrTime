package com.zikrtime.app.prayer

import com.zikrtime.app.data.PrayerMark
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PrayerCheckPlanTest {
    private val date = LocalDate.of(2026, 11, 7)
    private val zone = ZoneId.of("Europe/Moscow")

    private fun times(day: LocalDate): PrayerTimes = PrayerTimes(day, mapOf(
        Prayer.FAJR to day.atTime(5, 25).atZone(zone),
        Prayer.SUNRISE to day.atTime(6, 37).atZone(zone),
        Prayer.DHUHR to day.atTime(12, 30).atZone(zone),
        Prayer.ASR to day.atTime(14, 42).atZone(zone),
        Prayer.MAGHRIB to day.atTime(16, 45).atZone(zone),
        Prayer.ISHA to day.atTime(18, 14).atZone(zone),
    ))

    @Test fun optedOutOrAlreadyMarkedNeverDelivers() {
        val item = PrayerCheckPlan.occurrence(date, Prayer.DHUHR, 30, ::times)!!
        val now = item.due.plusSeconds(1)
        assertFalse(PrayerCheckPlan.canDeliver(item, item, now, false, null, null))
        for (mark in listOf(PrayerMark.DONE, PrayerMark.MISSED, PrayerMark.MADE_UP)) {
            assertFalse(PrayerCheckPlan.canDeliver(item, item, now, true, mark, null))
        }
        assertTrue(PrayerCheckPlan.canDeliver(item, item, now, true, PrayerMark.UNMARKED, null))
    }

    @Test fun changedTimeEarlyAlarmOrStaleDeliveryIsRejected() {
        val item = PrayerCheckPlan.occurrence(date, Prayer.FAJR, 30, ::times)!!
        val adjusted = PrayerCheckPlan.occurrence(date, Prayer.FAJR, 60, ::times)
        assertFalse(PrayerCheckPlan.canDeliver(item, adjusted, item.due.plusSeconds(60), true, null, null))
        assertFalse(PrayerCheckPlan.canDeliver(item, item, item.due.minusSeconds(1), true, null, null))
        assertFalse(PrayerCheckPlan.canDeliver(item, item, item.expires, true, null, null))
        assertEquals(times(date)[Prayer.SUNRISE].toInstant(), item.expires)
    }

    @Test fun pastMidnightIshaKeepsItsOriginalPrayerDate() {
        fun midnightTimes(day: LocalDate) = times(day).copy(times = times(day).times +
            (Prayer.ISHA to day.plusDays(1).atTime(0, 10).atZone(zone)))
        val now = date.atTime(0, 20).atZone(zone)
        val next = PrayerCheckPlan.next(now, 30, null, { emptyMap() }, ::midnightTimes)!!
        assertEquals(date.minusDays(1), next.day)
        assertEquals(Prayer.ISHA, next.prayer)
        assertEquals(now.withHour(0).withMinute(40).toInstant(), next.due)
    }

    @Test fun fajrBeforeMidnightKeepsNextDaysJournalDate() {
        fun polarTimes(day: LocalDate) = times(day).copy(times = times(day).times +
            (Prayer.FAJR to day.minusDays(1).atTime(23, 40).atZone(zone)))
        val now = date.atTime(23, 50).atZone(zone)
        val next = PrayerCheckPlan.next(now, 15, null, { emptyMap() }, ::polarTimes)!!
        assertEquals(date.plusDays(1), next.day)
        assertEquals(Prayer.FAJR, next.prayer)
        assertEquals(now.withMinute(55).toInstant(), next.due)
    }

    @Test fun dateSelectionUsesCityTimeAndActualInstants() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        fun tokyoTimes(day: LocalDate) = times(day).copy(times = times(day).times.mapValues {
            day.atTime(it.value.toLocalTime()).atZone(tokyo)
        })
        val phoneTime = date.minusDays(1).atTime(21, 0).atZone(ZoneId.of("UTC"))
        val next = PrayerCheckPlan.next(phoneTime.withZoneSameInstant(tokyo), 30, null, { emptyMap() }, ::tokyoTimes)!!
        assertEquals(date, next.day)
        assertEquals(Prayer.DHUHR, next.prayer)
        assertEquals(LocalTime.of(13, 0), next.due.atZone(tokyo).toLocalTime())
    }

    @Test fun noReminderAfterItsPrayerWindowOrForSunrise() {
        assertNull(PrayerCheckPlan.occurrence(date, Prayer.SUNRISE, 30, ::times))
        fun shortMaghrib(day: LocalDate) = times(day).copy(times = times(day).times +
            (Prayer.ISHA to day.atTime(17, 0).atZone(zone)))
        assertNull(PrayerCheckPlan.occurrence(date, Prayer.MAGHRIB, 30, ::shortMaghrib))
    }

    @Test fun rearmingDoesNotRepeatDeliveredOrMarkedPrayers() {
        val item = PrayerCheckPlan.occurrence(date, Prayer.DHUHR, 30, ::times)!!
        val history = PrayerCheckPlan.recordDelivery(null, item)
        assertFalse(PrayerCheckPlan.canDeliver(item, item, item.due, true, null, history))
        val next = PrayerCheckPlan.next(item.due.atZone(zone), 30, history, {
            if (it == date) mapOf(Prayer.ASR to PrayerMark.DONE) else emptyMap()
        }, ::times)!!
        assertEquals(Prayer.MAGHRIB, next.prayer)
        assertEquals(item, PrayerCheckPlan.decode(item.encode()))
        assertNull(PrayerCheckPlan.decode("broken"))
    }
}
