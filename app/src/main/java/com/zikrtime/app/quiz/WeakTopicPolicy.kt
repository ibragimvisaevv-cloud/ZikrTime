package com.zikrtime.app.quiz

/** A view of answered material, never a score for faith or for unseen questions. */
data class TopicLearning(
    val category: String,
    val total: Int,
    val answered: Int,
    val mistakes: Int,
    val consolidating: Int,
    val retained: Int,
    val due: Int,
) {
    val coverage: Float get() = if (total == 0) 0f else answered.toFloat() / total
    val status: TopicLearningStatus get() = when {
        answered == 0 -> TopicLearningStatus.NEW
        answered < minOf(5, total) -> TopicLearningStatus.LIMITED
        mistakes > 0 -> TopicLearningStatus.PRACTICE
        retained * 5 < answered * 4 -> TopicLearningStatus.BUILDING
        else -> TopicLearningStatus.RETAINED
    }
}
enum class TopicLearningStatus { NEW, LIMITED, PRACTICE, BUILDING, RETAINED }

/** Pure selection policy, shared by the overview and persisted training rounds. */
object WeakTopicPolicy {
    const val ROUND_SIZE = 5

    fun topics(bank: QuizBank, states: Map<String, LearningState>, level: Int,
        now: Long = System.currentTimeMillis()): List<TopicLearning> =
        bank.categories.mapNotNull { (category, _) ->
            val pool = bank.questionsFor(category, level)
            if (pool.isEmpty()) return@mapNotNull null
            val learned = pool.mapNotNull { stateFor(it, states) }
            TopicLearning(category, pool.size, learned.size,
                learned.count { it.lastCorrect == false },
                learned.count { it.lastCorrect == true && it.stage < 2 },
                learned.count { it.lastCorrect == true && it.stage >= 2 },
                learned.count { it.nextReviewAt <= now && it.lastAnswerAt <= now })
        }.sortedWith(compareByDescending<TopicLearning> { it.mistakes > 0 }
            .thenByDescending { it.mistakes }
            .thenByDescending { it.consolidating }
            .thenBy { it.answered == 0 })

    fun trainingIds(bank: QuizBank, states: Map<String, LearningState>, level: Int,
        category: String? = null, now: Long = System.currentTimeMillis()): List<String> =
        bank.questionsFor(category, level)
            // Unknown or revised material is not evidence of a weakness.
            .mapNotNull { q -> stateFor(q, states)?.let { q.id to it } }
            .sortedWith(compareBy<Pair<String, LearningState>> { (_, s) ->
                when {
                    s.lastCorrect == false -> 0
                    s.stage < 2 -> 1
                    s.nextReviewAt <= now && s.lastAnswerAt <= now -> 2
                    else -> 3
                }
            }.thenBy { it.second.lastAnswerAt }.thenBy { it.first })
            .take(ROUND_SIZE).map { it.first }

    private fun stateFor(q: Question, states: Map<String, LearningState>): LearningState? =
        states[q.id]?.takeIf { it.contentVersion == q.contentVersion &&
            it.lastAnswerAt > 0 && it.lastCorrect != null }
}
