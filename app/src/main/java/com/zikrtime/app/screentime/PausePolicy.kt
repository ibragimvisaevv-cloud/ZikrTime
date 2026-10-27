package com.zikrtime.app.screentime

/** A session grants foreground time, never bonus time or a prayer-lock exemption. */
data class PauseSession(
    val startedAt: Long,
    val budgetMillis: Long,
    val consumedMillis: Long = 0,
    val reminderSent: Boolean = false,
    val intention: String = "",
)

object PausePolicy {
    fun budget(requestedMinutes: Int, remainingDayMillis: Long?): Long =
        minOf(requestedMinutes.coerceIn(1, 60) * 60_000L, remainingDayMillis ?: Long.MAX_VALUE).coerceAtLeast(0)

    fun remaining(session: PauseSession, measuredMillis: Long, now: Long): Long {
        if (now < session.startedAt) return 0 // Clock rollback cannot replenish a grant.
        return (session.budgetMillis - maxOf(session.consumedMillis, measuredMillis.coerceAtLeast(0))).coerceAtLeast(0)
    }

    fun needsPause(enabled: Boolean, strict: Boolean, session: PauseSession?, measuredMillis: Long,
                   now: Long, openedAt: Long, previousVisitAt: Long, existingReason: BlockReason): Boolean {
        if (!enabled || existingReason !is BlockReason.None) return false
        if (session == null) return true
        if (remaining(session, measuredMillis, now) > 0) return false
        if (strict || now < session.startedAt) return true
        // Soft mode permits the current visit after a reminder, until the app is opened again.
        return session.reminderSent && openedAt > previousVisitAt
    }
}
