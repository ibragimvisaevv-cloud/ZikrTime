package com.zikrtime.app.data

import com.zikrtime.app.prayer.Prayer
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PrayerJournalTest {
    @Test fun restoresRetainedPartialMarksAndOldCompleteDates() {
        val today = LocalDate.of(2026, 11, 6)
        val old = today.minusDays(80)
        val history = PrayerJournal.legacy(mapOf(
            "prayer_days" to setOf(old.toString(), today.toString(), "broken"),
            "prayed_$today" to setOf("FAJR", "SUNRISE", "unknown")))
        assertEquals(Prayer.obligatory.toSet(), PrayerJournal.decode(history.getValue(old)).keys)
        assertEquals(mapOf(Prayer.FAJR to PrayerMark.DONE), PrayerJournal.decode(history.getValue(today)))
        assertFalse(history.containsKey(today.minusDays(1)))
    }

    @Test fun upgradeWhilePausedRetainsKnownHistoricalDaysWithoutImportingOperationalChecks() {
        val previous = LocalDate.of(2026, 9, 10)
        val recent = previous.plusMonths(1)
        val marks = PrayerJournal.legacy(mapOf("prayer_days" to setOf(previous.toString()),
            "prayed_$recent" to setOf("FAJR")), includeRecentChecks = false)
        assertEquals(setOf(previous), marks.keys)
        assertEquals(5, marks.getValue(previous).size)
    }

    @Test fun changingMissedToMadeUpReplacesTheEntry() {
        val marks = mutableMapOf(Prayer.ASR to PrayerMark.MISSED, Prayer.ISHA to PrayerMark.UNMARKED)
        assertEquals(setOf("ASR:missed"), PrayerJournal.encode(marks))
        marks[Prayer.ASR] = PrayerMark.MADE_UP
        assertEquals(setOf("ASR:made_up"), PrayerJournal.encode(marks))
        assertEquals(mapOf(Prayer.ASR to PrayerMark.MADE_UP), PrayerJournal.decode(PrayerJournal.encode(marks)))
    }
}
