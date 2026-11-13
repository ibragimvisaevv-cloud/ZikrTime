package com.zikrtime.app.prayer

import org.junit.Assert.*
import org.junit.Test

class PrayerSignalsTest {
    @Test fun onlyAnAlreadyDuePrayerInsideFiveMinutesCanNotify() {
        val at = 1_000_000_000L
        assertFalse(PrayerSignals.inWindow(0, at))
        assertFalse(PrayerSignals.inWindow(at, at - 1))
        assertTrue(PrayerSignals.inWindow(at, at))
        assertTrue(PrayerSignals.inWindow(at, at + 300_000))
        assertFalse(PrayerSignals.inWindow(at, at + 300_001))
        assertFalse(PrayerSignals.inWindow(at, at + 900_000))
    }
}
