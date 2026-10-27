package com.zikrtime.app.data

import java.time.LocalDate

data class ProgressEvent(val id: String, val day: LocalDate, val kind: String, val detail: String = "")
data class ProgressSummary(val topics: Int, val sessions: Int, val reviewed: Int, val activeDays: Int)
data class ProgressLedger(val events: List<ProgressEvent> = emptyList(), val seenIds: Set<String> = emptySet()) {
    fun record(enabled: Boolean, event: ProgressEvent): ProgressLedger =
        if (!enabled || event.id.isBlank() || event.id in seenIds) this
        else copy(events = events + event, seenIds = seenIds + event.id)

    // Keep only idempotency markers: clearing the visible history must not re-credit a restored session.
    fun clear(): ProgressLedger = copy(events = emptyList())

    fun summary(today: LocalDate, days: Int): ProgressSummary {
        val start = today.minusDays(days.coerceAtLeast(1).toLong() - 1)
        val period = events.filter { it.day >= start && it.day <= today }
        return ProgressSummary(
            topics = period.filter { it.kind == "complete" && it.detail.isNotBlank() }.map { it.detail }.distinct().size,
            sessions = period.count { it.kind == "complete" || it.kind == "review_complete" || it.kind == "material_complete" },
            reviewed = period.filter { it.kind == "review" }.map { it.detail }.distinct().size,
            activeDays = period.map { it.day }.distinct().size,
        )
    }
}

object ProgressMigration {
    const val VERSION = 1
    fun additions(existing: Map<String, *>): Map<String, Any> = buildMap {
        if ((existing["personal_progress_schema"] as? Int ?: 0) < VERSION) {
            if (existing["personal_progress_enabled"] !is Boolean) put("personal_progress_enabled", true)
            put("personal_progress_schema", VERSION)
        }
    }
    fun isHistoricalKey(key: String): Boolean = key in setOf("total_correct", "total_answered", "quiz_days", "prayer_days") ||
        key.startsWith("cat_correct_") || key.startsWith("tasbih_day_")
}

data class QuizRewardLedger(
    val sessions: Set<String> = emptySet(),
    val questionTimes: Map<String, Long> = emptyMap(),
    val highWaterDay: LocalDate? = null,
    val highWaterTime: Long = 0,
) {
    /** Daily rewards also require 24 hours since these questions last earned minutes. Clock rollback never creates another reward. */
    fun eligible(sessionId: String, ids: Set<String>, day: LocalDate, nowMillis: Long, required: Int): Boolean =
        sessionId.isNotBlank() && sessionId !in sessions && nowMillis >= highWaterTime &&
            (highWaterDay == null || day >= highWaterDay) &&
            ids.count { id -> id.isNotBlank() && (questionTimes[id]?.let { nowMillis - it >= 86_400_000L } ?: true) } >= required

    fun awarded(sessionId: String, ids: Set<String>, day: LocalDate, nowMillis: Long): QuizRewardLedger = copy(
        sessions = sessions + sessionId,
        questionTimes = questionTimes + ids.associateWith { nowMillis },
        highWaterDay = if (highWaterDay == null || day > highWaterDay) day else highWaterDay,
        highWaterTime = maxOf(nowMillis, highWaterTime),
    )
}
