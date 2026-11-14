package com.zikrtime.app.audio

import android.content.Context
import java.io.DataInputStream
import java.text.Normalizer

private val wordTokens = Regex("\\S+")

/** Mirrors the importer's lexical predicate; detached Uthmani marks have no audio target. */
internal fun isQuranSpokenToken(token: String): Boolean =
    Normalizer.normalize(token, Normalizer.Form.NFKD)
        .any { it.isLetter() && it != '\u0640' && it !in '\u06D6'..'\u06ED' }

/** Original Tanzil token offsets, including detached pause marks in token numbering. */
internal fun quranWordRange(arabic: String, word: Int): IntRange? =
    if (word <= 0) null else wordTokens.findAll(arabic).elementAtOrNull(word - 1)?.range

internal data class QuranWordInterval(val ayah: Int, val word: Int, val startMs: Int, val endMs: Int)

internal data class QuranReadingPhrase(val firstWord: Int, val lastWord: Int, val ranges: List<IntRange>)

internal data class QuranWordCursor(val next: QuranWordInterval, val previousInGap: QuranWordInterval?)
internal data class QuranWordSeek(val word: Int?, val startMs: Long)

/** Indexes spoken tokens while retaining their original UTF-16 offsets for styling. */
internal class QuranPhraseIndex(arabic: String, private val timedWords: Set<Int>) {
    private data class Token(val position: Int, val range: IntRange)
    private val words = wordTokens.findAll(arabic).mapIndexedNotNull { index, token ->
        if (isQuranSpokenToken(token.value)) {
            Token(index + 1, token.range)
        } else null
    }.toList()
    private val indexByPosition = words.mapIndexed { index, token -> token.position to index }.toMap()

    fun phrase(word: Int, gapNeighbor: Int? = null, additionalWords: Set<Int> = emptySet()): QuranReadingPhrase? {
        if (word !in timedWords && word !in additionalWords) return null
        val index = indexByPosition[word] ?: return null
        val firstIndex = index - index % 2
        val companion = words.getOrNull(if (index == firstIndex) index + 1 else firstIndex)
            ?.takeIf { it.position in timedWords || it.position in additionalWords }
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
    private var originalIntervals = emptyList<QuranWordInterval>()
    private var exclusiveWindows = emptySet<QuranWordInterval>()
    private var timedByAyah = emptyMap<Int, Set<Int>>()
    // Occurrence-one starts with no safe interval end are for user-directed seeks only.
    private var additionalStarts = emptyMap<Pair<Int, Int>, Long>()
    private var indexedAyah = 0
    private var indexedArabic = ""
    private var index: QuranPhraseIndex? = null

    private fun load(context: Context, surah: Int) {
        if (cachedSurah == surah) return
        originalIntervals = readIntervals(context, "words", surah)
        // The first safe display moment can be later than a word's actual attack due to
        // overlapping publisher intervals. The seek path must use its independently checked onset.
        val extraIntervals = readIntervals(context, "word_intervals", surah)
        exclusiveWindows = extraIntervals.toSet()
        intervals = if (extraIntervals.isEmpty()) originalIntervals else
            (originalIntervals + extraIntervals).sortedBy { it.startMs }
        timedByAyah = intervals.groupBy { it.ayah }.mapValues { (_, rows) -> rows.mapTo(mutableSetOf()) { it.word } }
        additionalStarts = runCatching {
            context.assets.open("quran_audio/word_starts/$surah.bin").buffered().use { stream ->
                readAdditionalStarts(DataInputStream(stream), surah)
            }
        }.getOrDefault(emptyMap())
        cachedSurah = surah
        index = null
    }

    private fun readIntervals(context: Context, directory: String, surah: Int): List<QuranWordInterval> = runCatching {
        context.assets.open("quran_audio/$directory/$surah.bin").buffered().use { stream ->
            val input = DataInputStream(stream)
            require(input.readInt() == 0x5A515731 && input.readInt() == surah)
            val count = input.readInt()
            require(count in 1..100_000)
            List(count) {
                QuranWordInterval(input.readUnsignedShort(), input.readUnsignedShort(), input.readInt(), input.readInt())
            }
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun cursor(context: Context, surah: Int, absoluteMs: Long): QuranWordCursor? {
        load(context, surah)
        val cursor = cursorAt(intervals, absoluteMs) ?: return null
        // A removed overlap beside a recovered window is not evidence of silence.
        return if (cursor.previousInGap != null &&
            (cursor.next in exclusiveWindows || cursor.previousInGap in exclusiveWindows))
            cursor.copy(previousInGap = null) else cursor
    }

    @Synchronized
    fun hasWords(context: Context, surah: Int, ayah: Int): Boolean {
        load(context, surah)
        return !timedByAyah[ayah].isNullOrEmpty()
    }

    @Synchronized
    fun hasWord(context: Context, surah: Int, ayah: Int, word: Int): Boolean {
        load(context, surah)
        return timedByAyah[ayah]?.contains(word) == true || (ayah to word) in additionalStarts
    }

    /** All timestamps refer to the original surah recording, including those in a repeat. */
    @Synchronized
    fun seekStart(context: Context, timing: QuranAyahTiming, word: Int?): Long {
        load(context, timing.reference.surahId)
        return resolveSeek(originalIntervals, timing, word, additionalStarts).startMs
    }

    /** Prefer the tapped word, then the nearest preceding original token measured in this ayah. */
    internal fun resolveSeek(rows: List<QuranWordInterval>, timing: QuranAyahTiming, word: Int?,
                             additionalStarts: Map<Pair<Int, Int>, Long> = emptyMap()): QuranWordSeek {
        var best = QuranWordSeek(null, timing.startMs)
        if (word == null || word <= 0) return best
        val bounds = timing.startMs until timing.endMs
        val ayah = timing.reference.ayah
        for (row in rows) {
            val start = row.startMs.toLong()
            if (row.ayah != ayah || start !in bounds) continue
            if (row.word == word) return QuranWordSeek(word, start)
            if (row.word < word && row.word > (best.word ?: 0)) best = QuranWordSeek(row.word, start)
        }
        for ((address, start) in additionalStarts) {
            val (verse, token) = address
            if (verse != ayah || start !in bounds) continue
            if (token == word) return QuranWordSeek(word, start)
            if (token < word && token > (best.word ?: 0)) best = QuranWordSeek(token, start)
        }
        return best
    }

    internal fun readAdditionalStarts(input: DataInputStream, surah: Int): Map<Pair<Int, Int>, Long> {
        require(input.readInt() == 0x5A515331 && input.readInt() == surah)
        val count = input.readInt()
        require(count in 0..100_000)
        return buildMap {
            repeat(count) {
                val ayah = input.readUnsignedShort()
                val word = input.readUnsignedShort()
                val startMs = input.readInt()
                require(ayah > 0 && word > 0 && startMs >= 0)
                require(put(ayah to word, startMs.toLong()) == null)
            }
            require(input.read() == -1)
        }
    }

    @Synchronized
    fun phrase(context: Context, surah: Int, ayah: Int, arabic: String,
               word: Int, gapNeighbor: Int? = null): QuranReadingPhrase? {
        load(context, surah)
        return phraseIndex(ayah, arabic).phrase(word, gapNeighbor)
    }

    /** A silent selection is drawn from the text and bundled word map before the audio service is opened. */
    @Synchronized
    fun selectedPhrase(context: Context, surah: Int, ayah: Int, arabic: String, word: Int): QuranReadingPhrase? {
        load(context, surah)
        val extra = additionalStarts.keys.filter { it.first == ayah }.mapTo(mutableSetOf()) { it.second }
        return phraseIndex(ayah, arabic).phrase(word, additionalWords = extra)
    }

    private fun phraseIndex(ayah: Int, arabic: String): QuranPhraseIndex {
        if (index == null || ayah != indexedAyah || arabic != indexedArabic) {
            index = QuranPhraseIndex(arabic, timedByAyah[ayah].orEmpty())
            indexedAyah = ayah
            indexedArabic = arabic
        }
        return requireNotNull(index)
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
