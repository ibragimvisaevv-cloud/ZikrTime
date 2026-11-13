package com.zikrtime.app.prayer

import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class ReadingPlanTest {
    @Test fun fridayDoesNotRepeatWhenRearmedAtDeliveryTime() {
        val thursday = ZonedDateTime.parse("2026-11-05T22:00:00+03:00[Europe/Moscow]")
        val friday = ReadingPlan.next(ReadingKind.FRIDAY, 9 * 60, thursday)
        assertEquals(thursday.plusDays(1).withHour(9), friday)
        assertEquals(friday.plusWeeks(1), ReadingPlan.next(ReadingKind.FRIDAY, 9 * 60, friday))
    }

    @Test fun resumeCanRecoverAnInexactAlarmWithoutReplayingStaleOrRescheduledTimes() {
        val due = ZonedDateTime.parse("2026-11-06T21:30:00+03:00[Europe/Moscow]")
        val instant = due.toInstant()
        assertEquals(due.toLocalDate(), ReadingPlan.dueDate(ReadingKind.BEDTIME, 1290, instant, due.plusMinutes(12)))
        assertNull(ReadingPlan.dueDate(ReadingKind.BEDTIME, 1290, instant, due.plusMinutes(91)))
        assertNull(ReadingPlan.dueDate(ReadingKind.BEDTIME, 1290, instant, due.minusMinutes(1)))
        assertNull(ReadingPlan.dueDate(ReadingKind.BEDTIME, 1300, instant, due.plusMinutes(12)))
        val abroad = due.withZoneSameInstant(java.time.ZoneId.of("Europe/Berlin")).plusMinutes(12)
        assertNull(ReadingPlan.dueDate(ReadingKind.BEDTIME, 1290, instant, abroad))
    }

    @Test fun returningAcrossTheDateLineDoesNotRepeatAnAlreadyDeliveredDate() {
        val friday = java.time.LocalDate.of(2026, 11, 6)
        val saturday = friday.plusDays(1)
        val saved = ReadingPlan.recordDelivery(ReadingPlan.recordDelivery(null, friday), saturday)
        assertTrue(ReadingPlan.delivered(saved, friday))
        assertTrue(ReadingPlan.delivered(saved, saturday))
        assertFalse(ReadingPlan.delivered(saved, saturday.plusDays(1)))
    }

    @Test fun bedtimeUsesWallClockAcrossDstAndAvoidsSecondFallBackOccurrence() {
        val spring = ZonedDateTime.parse("2026-03-28T22:00:00+01:00[Europe/Berlin]")
        assertEquals(21, ReadingPlan.next(ReadingKind.BEDTIME, 21 * 60, spring).hour)
        val fall = ZonedDateTime.parse("2026-10-25T02:45:00+02:00[Europe/Berlin]")
        assertEquals(fall.toLocalDate().plusDays(1), ReadingPlan.next(ReadingKind.BEDTIME, 150, fall).toLocalDate())
    }
}
