package com.zikrtime.app.screentime

/** A session grants foreground time, never bonus time or a prayer-lock exemption. */
data class PauseSession(
    val startedAt: Long,
    val budgetMillis: Long,
    val consumedMillis: Long = 0,
    val reminderSent: Boolean = false,
    val intention: String = "",
    val expiryHandled: Boolean = false,
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
        // A denied notification must not create a popup loop in the current visit.
        // Legacy sessions stored only reminderSent and keep the same next-visit behavior.
        return (session.reminderSent || session.expiryHandled) && openedAt > previousVisitAt
    }
}
