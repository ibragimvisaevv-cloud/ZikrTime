package com.zikrtime.app.data

import org.junit.Assert.*
import org.junit.Test

class QuranReadTrackerTest {
    private fun tracker(count: Int = 1) = QuranReadTracker((1..count).map { QuranAyah(1, it, "abcdefghij", "") })
    private fun part(ayah: Int = 1, start: Int = 0, end: Int = 10) = QuranVisibleText(ayah, start, end)
    private fun settle(t: QuranReadTracker, parts: List<QuranVisibleText>, ticks: Int = 4): Set<Int> =
        (1..ticks).flatMap { t.observe(1000, parts) }.toSet()

    @Test fun aVisibleVerseIsAutomaticallyCountedOnceWithoutAnExtraTap() {
        val t = tracker()
        assertEquals(setOf(1), settle(t, listOf(part())))
        assertTrue(settle(t, listOf(part())).isEmpty())
    }

    @Test fun jumpingToTheEndDoesNotMarkSkippedVerses() {
        val t = tracker(8)
        assertEquals(setOf(8), settle(t, listOf(part(8))))
    }

    @Test fun longAyahRequiresEveryPartToHaveBeenVisible() {
        val t = tracker()
        assertTrue(settle(t, listOf(part(end = 5))).isEmpty())
        assertEquals(setOf(1), settle(t, listOf(part(start = 5))))
    }

    @Test fun anUnseenGapInALongAyahIsNotFilledByScrollingToItsEnd() {
        val t = tracker()
        assertTrue(settle(t, listOf(part(end = 4))).isEmpty())
        assertTrue(settle(t, listOf(part(start = 6))).isEmpty())
        assertEquals(setOf(1), settle(t, listOf(part(start = 4, end = 6))))
    }

    @Test fun quickFlicksDoNotCountTextOrReuseTimeFromAPreviousViewport() {
        val t = tracker(3)
        for (ayah in 1..3) {
            assertTrue(t.observe(300, listOf(part(ayah))).isEmpty())
            assertTrue(t.observe(300, listOf(part(ayah))).isEmpty())
            t.pause()
        }
        assertTrue(t.observe(1000, listOf(part(3))).isEmpty())
    }

    @Test fun inactiveIntervalsAndStallsAreExcluded() {
        val t = tracker()
        t.observe(1000, listOf(part()))
        assertTrue(t.observe(0, listOf(part())).isEmpty())
        assertTrue(t.observe(1000, listOf(part())).isEmpty())
        assertTrue(t.observe(20000, listOf(part())).isEmpty())
        assertTrue(t.observe(1000, listOf(part())).isEmpty())
        assertTrue(t.observe(1000, listOf(part())).isEmpty())
        assertEquals(setOf(1), t.observe(1000, listOf(part())))
    }

    @Test fun aScreenfulOfVersesSharesOneElapsedTimeBudget() {
        val t = tracker(3)
        val visible = (1..3).map { part(it) }
        assertTrue(t.observe(1000, visible).isEmpty())
        assertTrue(t.observe(1000, visible).isEmpty())
        assertEquals(setOf(1), t.observe(1000, visible))
        assertEquals(setOf(2), t.observe(1000, visible))
        assertTrue(t.observe(1000, visible).isEmpty())
        assertEquals(setOf(3), t.observe(1000, visible))
    }

    @Test fun invalidGeometryCannotCreditAVerse() {
        val t = tracker()
        val invalid = listOf(part(start = -1), part(end = 20), part(ayah = 2), part(end = 0))
        assertTrue(settle(t, invalid).isEmpty())
    }
}
