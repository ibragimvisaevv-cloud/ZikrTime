package com.zikrtime.app.audio

import android.content.Context
import java.io.DataInputStream
import java.text.Normalizer

private val wordTokens = Regex("\\S+")

/** Original Tanzil token offsets, including detached pause marks in token numbering. */
internal fun quranWordRange(arabic: String, word: Int): IntRange? =
    if (word <= 0) null else wordTokens.findAll(arabic).elementAtOrNull(word - 1)?.range

internal data class QuranWordInterval(val ayah: Int, val word: Int, val startMs: Int, val endMs: Int)

internal data class QuranReadingPhrase(val firstWord: Int, val lastWord: Int, val ranges: List<IntRange>)

internal data class QuranWordCursor(val next: QuranWordInterval, val previousInGap: QuranWordInterval?)

/** Indexes spoken tokens while retaining their original UTF-16 offsets for styling. */
internal class QuranPhraseIndex(arabic: String, private val timedWords: Set<Int>) {
    private data class Token(val position: Int, val range: IntRange)
    private val words = wordTokens.findAll(arabic).mapIndexedNotNull { index, token ->
        // Mirror the importer's lexical predicate; isolated Uthmani signs are never a word.
        val letters = Normalizer.normalize(token.value, Normalizer.Form.NFKD)
        if (letters.any { it.isLetter() && it != '\u0640' && it !in '\u06D6'..'\u06ED' }) {
            Token(index + 1, token.range)
        } else null
    }.toList()
    private val indexByPosition = words.mapIndexed { index, token -> token.position to index }.toMap()

    fun phrase(word: Int, gapNeighbor: Int? = null): QuranReadingPhrase? {
        if (word !in timedWords) return null
        val index = indexByPosition[word] ?: return null
        val firstIndex = index - index % 2
        val companion = words.getOrNull(if (index == firstIndex) index + 1 else firstIndex)
            ?.takeIf { it.position in timedWords }
        if (gapNeighbor != null && gapNeighbor != companion?.position) return null
        if (companion == null) {
            val only = words[index]
            return QuranReadingPhrase(only.position, only.position, listOf(only.range))
        }
        val first = words[firstIndex]
        val last = words[firstIndex + 1]
        return QuranReadingPhrase(first.position, last.position, listOf(first.range, last.range))
    }
}

internal object QuranWordTimings {
    // Bridge only a brief gap when its two actual endpoints belong to one display pair.
    private const val MAX_PAIR_GAP_MS = 300
    private var cachedSurah = 0
    private var intervals = emptyList<QuranWordInterval>()
    private var timedByAyah = emptyMap<Int, Set<Int>>()
    private var indexedAyah = 0
    private var indexedArabic = ""
    private var index: QuranPhraseIndex? = null

    private fun load(context: Context, surah: Int) {
        if (cachedSurah == surah) return
        intervals = runCatching {
            context.assets.open("quran_audio/words/$surah.bin").buffered().use { stream ->
                val input = DataInputStream(stream)
                require(input.readInt() == 0x5A515731 && input.readInt() == surah)
                val count = input.readInt()
                require(count in 1..100_000)
                List(count) {
                    QuranWordInterval(input.readUnsignedShort(), input.readUnsignedShort(),
                        input.readInt(), input.readInt())
                }
            }
        }.getOrDefault(emptyList())
        timedByAyah = intervals.groupBy { it.ayah }.mapValues { (_, rows) -> rows.mapTo(mutableSetOf()) { it.word } }
        cachedSurah = surah
        index = null
    }

    @Synchronized
    fun cursor(context: Context, surah: Int, absoluteMs: Long): QuranWordCursor? {
        load(context, surah)
        return cursorAt(intervals, absoluteMs)
    }

    @Synchronized
    fun hasWords(context: Context, surah: Int, ayah: Int): Boolean {
        load(context, surah)
        return !timedByAyah[ayah].isNullOrEmpty()
    }

    @Synchronized
    fun phrase(context: Context, surah: Int, ayah: Int, arabic: String,
               word: Int, gapNeighbor: Int? = null): QuranReadingPhrase? {
        load(context, surah)
        val timed = timedByAyah[ayah] ?: return null
        if (index == null || ayah != indexedAyah || arabic != indexedArabic) {
            index = QuranPhraseIndex(arabic, timed)
            indexedAyah = ayah
            indexedArabic = arabic
        }
        return index?.phrase(word, gapNeighbor)
    }

    internal fun atOrAfter(intervals: List<QuranWordInterval>, positionMs: Long): QuranWordInterval? =
        cursorAt(intervals, positionMs)?.next

    internal fun cursorAt(intervals: List<QuranWordInterval>, positionMs: Long): QuranWordCursor? {
        var low = 0
        var high = intervals.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (intervals[mid].endMs <= positionMs) low = mid + 1 else high = mid
        }
        val next = intervals.getOrNull(low) ?: return null
        val previous = if (next.startMs > positionMs) intervals.getOrNull(low - 1)?.takeIf {
            it.ayah == next.ayah && next.startMs - it.endMs in 1..MAX_PAIR_GAP_MS
        } else null
        return QuranWordCursor(next, previous)
    }
}
