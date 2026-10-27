package com.zikrtime.app.quiz

/** UTC durations, not wall-clock dates: timezone changes cannot make a review due twice. */
data class LearningState(
    val contentVersion: Int = 1,
    val lastCorrect: Boolean? = null,
    val lastAnswerAt: Long = 0,
    val stage: Int = 0,
    val nextReviewAt: Long = 0,
    val explanationAt: Long = 0,
    val lastEventId: String = "",
)

object LearningPolicy {
    const val DAY = 86_400_000L
    private val intervals = listOf(1L, 3L, 7L, 14L, 30L)

    fun answer(previous: LearningState?, version: Int, correct: Boolean, now: Long, eventId: String): LearningState {
        val old = previous?.takeIf { it.contentVersion == version } ?: LearningState(contentVersion = version)
        if (old.lastEventId == eventId) return old
        // A clock rollback does not move stored deadlines backwards.
        val safeNow = maxOf(now, old.lastAnswerAt, old.explanationAt)
        val eligible = correct && (old.lastAnswerAt == 0L || now >= old.nextReviewAt) &&
            (old.explanationAt == 0L || now - old.explanationAt >= DAY)
        val nextStage = when { !correct -> 0; eligible -> (old.stage + 1).coerceAtMost(intervals.size); else -> old.stage }
        val delay = if (correct && eligible) intervals[(nextStage - 1).coerceAtLeast(0)] else 1L
        val due = if (correct && !eligible && old.nextReviewAt > safeNow) old.nextReviewAt else safeNow + delay * DAY
        return old.copy(lastCorrect = correct, lastAnswerAt = safeNow, stage = nextStage,
            nextReviewAt = due, lastEventId = eventId)
    }

    fun explanation(previous: LearningState?, version: Int, now: Long): LearningState {
        val old = previous?.takeIf { it.contentVersion == version } ?: LearningState(contentVersion = version)
        return old.copy(explanationAt = maxOf(now, old.lastAnswerAt, old.explanationAt))
    }

    fun dueIds(states: Map<String, LearningState>, versions: Map<String, Int>, now: Long): List<String> = states.entries
        .filter { (id, state) -> versions[id] == state.contentVersion && state.lastAnswerAt > 0 && state.nextReviewAt <= now && now >= state.lastAnswerAt }
        .sortedWith(compareBy<Map.Entry<String, LearningState>> { it.value.nextReviewAt }.thenBy { it.value.lastCorrect != false }.thenBy { it.key })
        .take(5).map { it.key }
}
