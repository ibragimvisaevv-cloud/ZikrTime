package com.zikrtime.app.ui

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class NaturalMotionTest {
    @Test fun periodsUseInstantsAndPrayerBoundaries() {
        val z = ZoneId.of("Asia/Tokyo")
        val d = LocalDate.of(2026, 10, 27)
        fun t(h: Int, m: Int = 0) = d.atTime(h,m).atZone(z).toInstant()
        fun p(i: Instant) = naturalPeriod(i,t(5),t(6),t(17),t(19))
        assertEquals(NaturalPeriod.NIGHT,p(t(4)))
        assertEquals(NaturalPeriod.DAWN,p(t(5)))
        assertEquals(NaturalPeriod.DAY,p(t(6)))
        assertEquals(NaturalPeriod.SUNSET,p(t(16,15)))
        assertEquals(NaturalPeriod.NIGHT,p(t(19)))
        assertEquals(p(t(6)),p(t(6).atZone(ZoneId.of("America/New_York")).toInstant()))
    }
    @Test fun rapidAndCoalescedTapsKeepEveryStepAcrossGoals() {
        val a = BeadTravel(0,0)
        for (n in 1..1000) { assertFalse(a.update(n,0)); assertEquals(n.toLong(),a.steps) }
        val b = BeadTravel(0,0)
        listOf(32,33,34,98,99,100,101,1000).forEach { assertFalse(b.update(it,0)) }
        assertEquals(a.steps,b.steps)
        assertFalse(b.update(1000,0)) // Goal change does not change count/travel.
        assertEquals(1000L,b.steps)
    }
    @Test fun resetAndPhraseSwitchNeverAnimateBackwards() {
        val a = BeadTravel(21,0)
        a.update(40,0)
        assertTrue(a.update(0,0)); assertEquals(0L,a.steps)
        a.update(10,0)
        assertTrue(a.update(0,1)); assertEquals(0L,a.steps)
        assertFalse(a.update(1,1)); assertEquals(1L,a.steps)
    }
    @Test fun cityClockHandlesDateAndDst() {
        val i = Instant.parse("2026-10-26T22:30:00Z")
        assertEquals(LocalDate.of(2026,10,27), i.atZone(CityZones.forCity("moscow")).toLocalDate())
        assertEquals(ZoneId.of("Europe/Moscow"),CityZones.forCity("moscow"))
        val berlin = ZoneId.of("Europe/Berlin")
        assertEquals(2,Instant.parse("2026-10-25T00:30:00Z").atZone(berlin).hour)
        assertEquals(2,Instant.parse("2026-10-25T01:30:00Z").atZone(berlin).hour)
    }
}
