package com.zikrtime.app.widget

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class PrayerWidgetModelTest {
    @Test fun defaultSizesAndReflow() {
        assertEquals(PrayerWidgetModel.Layout.COMPACT, PrayerWidgetModel.layout(156,90,1f))
        assertEquals(PrayerWidgetModel.Layout.WIDE, PrayerWidgetModel.layout(340,90,1f))
        assertEquals(PrayerWidgetModel.Layout.SQUARE, PrayerWidgetModel.layout(156,190,1f))
        assertEquals(PrayerWidgetModel.Layout.DAY, PrayerWidgetModel.layout(340,190,1f))
        assertNotEquals(PrayerWidgetModel.Layout.DAY, PrayerWidgetModel.layout(340,190,2f))
        assertEquals(PrayerWidgetModel.Layout.DAY, PrayerWidgetModel.layout(340,380,2f))
    }
    @Test fun midnightUsesSelectedCityZoneIncludingDst() {
        val now=ZonedDateTime.parse("2026-10-25T23:55:00+01:00[Europe/Berlin]")
        assertEquals(ZonedDateTime.parse("2026-10-26T00:00:00+01:00[Europe/Berlin]").toInstant().toEpochMilli(),PrayerWidgetModel.nextRefresh(now,now.plusHours(5)))
    }
    @Test fun eventRefreshDoesNotWaitForPeriodicUpdate() {
        val now=ZonedDateTime.parse("2026-10-30T12:00:00+03:00")
        assertEquals(now.plusSeconds(7).toInstant().toEpochMilli(),PrayerWidgetModel.nextRefresh(now,now.plusSeconds(7)))
    }
    @Test fun timerUsesElapsedClockAndNeverStartsNegative() {
        assertEquals(8000L,PrayerWidgetModel.countdownBase(5000,1000,4000))
        assertEquals(5000L,PrayerWidgetModel.countdownBase(5000,4000,1000))
    }
}
