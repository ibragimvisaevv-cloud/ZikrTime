package com.zikrtime.app

import com.zikrtime.app.data.ProgressEvent
import com.zikrtime.app.data.ProgressLedger
import com.zikrtime.app.data.ProgressMigration
import com.zikrtime.app.data.ProgressSummary
import com.zikrtime.app.data.QuizRewardLedger
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ProgressLogicTest {
    private val day = LocalDate.of(2026, 10, 27)

    @Test fun migrationIsAdditiveAndIdempotent() {
        val old = mapOf<String, Any>(
            "total_answered" to 32, "tasbih_count" to 14, "bonus_$day" to 20,
            "prayed_$day" to setOf("FAJR"), "city" to "Moscow",
        )
        val first = old + ProgressMigration.additions(old)
        assertEquals(true, first["personal_progress_enabled"])
        old.forEach { (key, value) -> assertEquals(value, first[key]) }
        assertTrue(ProgressMigration.additions(first).isEmpty())
        val explicitOff = old + ("personal_progress_enabled" to false)
        assertEquals(false, (explicitOff + ProgressMigration.additions(explicitOff))["personal_progress_enabled"])
        assertFalse(first.containsKey("personal_progress_ledger")) // Old results aren't invented as dated events.
    }

    @Test fun disablingPreservesHistoryAndDoesNotBackfill() {
        val before = ProgressLedger().record(true, ProgressEvent("a", day.minusDays(1), "complete", "faith"))
        val during = before.record(false, ProgressEvent("b", day, "complete", "prayer"))
        assertEquals(before, during)
        val after = during.record(true, ProgressEvent("c", day.plusDays(1), "complete", "history"))
        assertEquals(listOf("a", "c"), after.events.map { it.id })
        assertEquals(2, after.summary(day.plusDays(1), 7).sessions)
    }

    @Test fun restoredEventsRemainIdempotentEvenAfterClear() {
        val event = ProgressEvent("stable-session-id", day, "complete", "faith")
        val once = ProgressLedger().record(true, event)
        assertEquals(once, once.record(true, event))
        val cleared = once.clear()
        assertTrue(cleared.events.isEmpty())
        assertEquals(cleared, cleared.record(true, event))
        assertEquals(1, cleared.record(true, event.copy(id = "new-session")).events.size)
    }

    @Test fun summariesUseInclusivePeriodUniqueQuestionsAndHonestActivityCounts() {
        val events = listOf(
            ProgressEvent("old", day.minusDays(7), "complete", "old"),
            ProgressEvent("start", day.minusDays(6), "complete", "faith"),
            ProgressEvent("again", day, "complete", "faith"),
            ProgressEvent("review1", day, "review", "q1"),
            ProgressEvent("review2", day, "review", "q1"),
            ProgressEvent("review3", day, "review", "q2"),
            ProgressEvent("review-done", day, "review_complete"),
            ProgressEvent("reading-done", day, "material_complete"),
            ProgressEvent("future", day.plusDays(1), "complete", "future"),
        )
        val ledger = events.fold(ProgressLedger()) { state, event -> state.record(true, event) }
        assertEquals(ProgressSummary(topics = 1, sessions = 4, reviewed = 2, activeDays = 2), ledger.summary(day, 7))
        assertEquals(1, ledger.summary(day, 1).activeDays)
        assertEquals(ProgressSummary(0, 0, 0, 0), ledger.clear().summary(day, 30))
    }

    @Test fun statisticsResetCannotEraseWorkingState() {
        listOf("total_correct", "total_answered", "quiz_days", "prayer_days", "cat_correct_faith", "tasbih_day_$day").forEach {
            assertTrue(it, ProgressMigration.isHistoricalKey(it))
        }
        listOf("prayed_$day", "bonus_$day", "tasbih_count", "tasbih_phrase", "tasbih_target", "recent_questions", "learning_state_v1", "quiz_reward_guard_v1", "personal_progress_enabled", "city", "blocked_packages", "ramadan_fasts").forEach {
            assertFalse(it, ProgressMigration.isHistoricalKey(it))
        }
    }

    @Test fun rewardsRequireUniqueQuestionsAndDeduplicateSessions() {
        val ids = setOf("a", "b", "c")
        val now = 1_800_000_000_000L
        val empty = QuizRewardLedger()
        assertFalse(empty.eligible("s1", setOf("a", "a", "b"), day, now, 3))
        assertFalse(empty.eligible("", ids, day, now, 3))
        assertFalse(empty.eligible("s1", setOf("a", "b", ""), day, now, 3))
        assertTrue(empty.eligible("s1", ids, day, now, 3))
        val earned = empty.awarded("s1", ids, day, now)
        assertFalse(earned.eligible("s1", setOf("d", "e", "f"), day.plusDays(2), now + 172_800_000, 3))
        assertTrue(earned.eligible("s2", setOf("d", "e", "f"), day, now + 1, 3))
    }

    @Test fun clockRollbackOrMidnightCannotFarmTheSameQuestions() {
        val ids = setOf("a", "b", "c")
        val now = 1_800_000_000_000L
        val earned = QuizRewardLedger().awarded("s1", ids, day, now)
        assertFalse(earned.eligible("s2", ids, day.plusDays(1), now + 1_000, 3))
        assertFalse(earned.eligible("s2", setOf("d", "e", "f"), day.minusDays(1), now + 86_400_000, 3))
        assertFalse(earned.eligible("s2", setOf("d", "e", "f"), day, now - 1, 3))
        assertFalse(earned.eligible("s2", ids, day.plusDays(1), now + 86_399_999, 3))
        assertTrue(earned.eligible("s2", ids, day.plusDays(1), now + 86_400_000, 3))
    }
}
