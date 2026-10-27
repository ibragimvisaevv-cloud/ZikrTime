package com.zikrtime.app

import com.zikrtime.app.data.Streaks
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.screentime.BlockPolicy
import com.zikrtime.app.screentime.BlockReason
import com.zikrtime.app.screentime.PrayerStart
import com.zikrtime.app.screentime.UsageEventLite
import com.zikrtime.app.screentime.UsageMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class LogicTest {

    private val today = LocalDate.of(2026, 10, 24)

    @Test
    fun streaks() {
        assertEquals(0, Streaks.current(emptySet(), today))
        assertEquals(2, Streaks.current(setOf(today, today.minusDays(1)), today))
        // Сегодня ещё не отмечено — серия со вчерашнего дня не обрывается.
        assertEquals(2, Streaks.current(setOf(today.minusDays(1), today.minusDays(2)), today))
        assertEquals(0, Streaks.current(setOf(today.minusDays(2)), today))
        assertEquals(3, Streaks.best(setOf(today, today.minusDays(5), today.minusDays(6), today.minusDays(7))))
    }

    @Test
    fun usageTime() {
        val ev = listOf(
            UsageEventLite("ig", "A", true, 1000), UsageEventLite("ig", "A", false, 5000),
            UsageEventLite("ig", "B", true, 6000), UsageEventLite("ig", "C", true, 7000),
            UsageEventLite("ig", "B", false, 7100), UsageEventLite("ig", "C", false, 9000),
            UsageEventLite("tt", "X", false, 9500), // пауза без начала — пропускаем
            UsageEventLite("tt", "X", true, 10000),
        )
        assertEquals(4000L + 3000L + 2000L, UsageMath.foregroundMillis(ev, 0, 12000))
        assertEquals(2000L + 3000L + 2000L, UsageMath.foregroundMillis(ev, 3000, 12000))
        assertEquals(mapOf("tt" to 10000L), UsageMath.openPackages(ev))
        assertTrue(UsageMath.openPackages(ev.take(2)).isEmpty())
    }

    @Test
    fun blockPolicy() {
        val starts = listOf(PrayerStart(today, Prayer.DHUHR, Instant.parse("2026-10-24T10:00:00Z")))
        val during = Instant.parse("2026-10-24T10:05:00Z")
        val r = BlockPolicy.decide(during, starts, true, 15, 0L, 30)
        assertEquals(BlockReason.PrayerTime(Prayer.DHUHR, today, Instant.parse("2026-10-24T10:15:00Z")), r)
        assertEquals(BlockReason.None, BlockPolicy.decide(during, starts, false, 15, 0L, 30))
        assertEquals(BlockReason.None, BlockPolicy.decide(Instant.parse("2026-10-24T10:15:00Z"), starts, true, 15, 0L, 30))
        val later = Instant.parse("2026-10-24T12:00:00Z")
        assertEquals(BlockReason.LimitReached(30, 30), BlockPolicy.decide(later, starts, true, 15, 30 * 60_000L, 30))
        assertEquals(BlockReason.None, BlockPolicy.decide(later, starts, true, 15, 29 * 60_000L, 30))
        assertEquals(BlockReason.None, BlockPolicy.decide(later, starts, true, 15, null, 30))
    }
}
