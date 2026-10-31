package com.zikrtime.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class QuranReadingHistoryTest {
    private val day = LocalDate.of(2026, 10, 30)

    @Test fun repeatedMarksDoNotInflateQuranCompletionButKeepDailyHistory() {
        val log = QuranReadingHistory().mark(day, 1, 1, 7, 7).mark(day, 1, 1, 7, 7)
            .mark(day.plusDays(1), 1, 3, 7, 7).mark(day, 2, 255, 286, 286)
        assertEquals(39, log.uniqueVerses.size)
        assertEquals(5, log.days.getValue(day.plusDays(1)).verses.size)
        assertEquals(log, QuranReadingHistory.decode(log.encode()))
    }

    @Test fun invalidRangesNeverEnterHistory() {
        for (range in listOf(0 to 2, 4 to 3, 1 to 8)) {
            assertTrue(runCatching { QuranReadingHistory().mark(day, 1, range.first, range.second, 7) }.isFailure)
        }
    }

    @Test fun elapsedReadingIsSplitAtLocalMidnight() {
        val log = QuranReadingHistory().addTime(Instant.parse("2026-10-30T20:59:58Z"), 5000, ZoneId.of("Europe/Moscow"))
        assertEquals(2000L, log.days.getValue(day).millis)
        assertEquals(3000L, log.days.getValue(day.plusDays(1)).millis)
    }

    @Test fun daylightSavingDoesNotAssume24HourDays() {
        val zone = ZoneId.of("Europe/Berlin")
        val start = LocalDate.of(2026, 10, 25).atStartOfDay(zone).toInstant()
        val log = QuranReadingHistory().addTime(start, 25 * 3600000L + 2000L, zone)
        assertEquals(25 * 3600000L, log.days.getValue(LocalDate.of(2026, 10, 25)).millis)
        assertEquals(2000L, log.days.getValue(LocalDate.of(2026, 10, 26)).millis)
    }

    @Test fun suspendedPausedAndFinishedSessionsDoNotAccumulate() {
        val timer = QuranReadingTimer(60000)
        assertEquals(0L, timer.readingMillis(1000, false))
        assertEquals(0L, timer.readingMillis(60000, true))
        assertEquals(0L, timer.readingMillis(-100, true))
        assertEquals(0L, timer.copy(paused = true).readingMillis(1000, true))
        assertEquals(0L, timer.copy(finished = true).readingMillis(1000, true))
        assertEquals(1000L, timer.readingMillis(1000, true))
    }

    @Test fun timerCompletesExactlyOnceWithoutCountingBeyondTarget() {
        val timer = QuranReadingTimer(250)
        val counted = timer.readingMillis(1000, true)
        assertEquals(250L, counted)
        val done = timer.advance(counted)
        assertEquals(0L, done.remaining)
        assertTrue(done.finished)
        assertEquals(done, done.advance(1000))
        assertEquals(0L, done.readingMillis(1000, true))
    }

    @Test fun brokenHistoryDoesNotEraseUnrelatedValidDays() {
        val raw = "{\"2026-10-30\":{\"ms\":1000,\"verses\":[\"1:1\"]},\"bad\":{\"ms\":999}}"
        assertEquals(1, QuranReadingHistory.decode(raw).days.size)
        assertTrue(QuranReadingHistory.decode("broken").days.isEmpty())
    }
}
