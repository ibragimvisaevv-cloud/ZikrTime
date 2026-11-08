package com.zikrtime.app.audio

/** Saved progress never implies a request to start sound. */
data class QuranPlaybackPosition(
    val surahId: Int,
    val positionMs: Long,
    val durationMs: Long,
    val ayah: Int? = null,
    val ayahOptions: QuranAyahOptions = QuranAyahOptions(),
    val repeatIteration: Int = 1,
)

object QuranPlaybackPolicy {
    fun restore(surahId: Int, positionMs: Long, durationMs: Long): QuranPlaybackPosition? {
        if (surahId !in 1..114) return null
        val duration = durationMs.coerceAtLeast(0L)
        return QuranPlaybackPosition(surahId, clamp(positionMs, duration), duration)
    }

    fun clamp(positionMs: Long, durationMs: Long): Long =
        if (durationMs > 0L) positionMs.coerceIn(0L, durationMs) else positionMs.coerceAtLeast(0L)

    fun offset(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
        val safePosition = positionMs.coerceAtLeast(0L)
        val sum = if (deltaMs > 0L && safePosition > Long.MAX_VALUE - deltaMs) Long.MAX_VALUE
            else if (deltaMs < 0L && safePosition < -deltaMs && deltaMs != Long.MIN_VALUE) 0L
            else if (deltaMs == Long.MIN_VALUE) 0L else safePosition + deltaMs
        return clamp(sum, durationMs)
    }
}
