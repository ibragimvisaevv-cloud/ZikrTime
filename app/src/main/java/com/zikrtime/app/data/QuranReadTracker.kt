package com.zikrtime.app.data

import java.util.BitSet

/** UTF-16 offsets into the text actually laid out on this reading surface. */
data class QuranVisibleText(val ayah: Int, val start: Int, val end: Int)

/** A local estimate of reading, not a claim that a person understood or recited a verse.
 * Only text visible in two consecutive quiet samples receives credit. Long verses
 * need complete coverage; jumps never fill the intervening verses. Visible verses
 * share one time budget, so a screenful is not counted several times in one second.
 */
class QuranReadTracker(verses: List<QuranAyah>, displayedText: (QuranAyah) -> String = { it.arabic }) {
    private class Seen(val length: Int, val required: Long) {
        val covered = BitSet(length)
        var millis = 0L
        var completed = false
    }
    private val seen = verses.associate { ayah ->
        val text = displayedText(ayah)
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        ayah.number to Seen(text.length, (words * 300L).coerceIn(1500L, 15000L))
    }
    private var previous = emptyList<QuranVisibleText>()

    fun pause() { previous = emptyList() }

    fun observe(elapsed: Long, visible: List<QuranVisibleText>): Set<Int> {
        if (elapsed !in 1L..1500L) { pause(); return emptySet() }
        val current = visible.filter { part ->
            val verse = seen[part.ayah]
            verse != null && part.start >= 0 && part.end > part.start && part.end <= verse.length
        }
        val stable = current.flatMap { part -> previous.filter { it.ayah == part.ayah }.mapNotNull { old ->
            val start = maxOf(part.start, old.start)
            val end = minOf(part.end, old.end)
            if (start < end) QuranVisibleText(part.ayah, start, end) else null
        } }
        previous = current
        stable.forEach { seen.getValue(it.ayah).covered.set(it.start, it.end) }
        var budget = elapsed
        stable.map { it.ayah }.distinct().sorted().forEach { number ->
            val verse = seen.getValue(number)
            val add = minOf(budget, (verse.required - verse.millis).coerceAtLeast(0))
            verse.millis += add
            budget -= add
        }
        return stable.map { it.ayah }.distinct().filter { number ->
            val verse = seen.getValue(number)
            val complete = !verse.completed && verse.millis >= verse.required &&
                verse.covered.nextClearBit(0) >= verse.length
            if (complete) verse.completed = true
            complete
        }.toSet()
    }
}
