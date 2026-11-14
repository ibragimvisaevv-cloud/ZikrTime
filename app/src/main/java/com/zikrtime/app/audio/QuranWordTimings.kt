package com.zikrtime.app.audio

import android.content.Context
import java.io.DataInputStream

private val wordTokens = Regex("\\S+")

/** Positions address the exact whitespace-delimited token in the bundled Tanzil text. */
internal fun quranWordRange(arabic: String, word: Int): IntRange? =
    if (word <= 0) null else wordTokens.findAll(arabic).elementAtOrNull(word - 1)?.range

internal data class QuranWordInterval(val ayah: Int, val word: Int, val startMs: Int, val endMs: Int)

internal object QuranWordTimings {
    private var cachedSurah = 0
    private var intervals = emptyList<QuranWordInterval>()

    @Synchronized
    fun next(context: Context, surah: Int, absoluteMs: Long): QuranWordInterval? {
        if (cachedSurah != surah) {
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
            cachedSurah = surah
        }
        return atOrAfter(intervals, absoluteMs)
    }

    /** The importer removes ambiguous overlaps; end times are strictly increasing. */
    internal fun atOrAfter(intervals: List<QuranWordInterval>, positionMs: Long): QuranWordInterval? {
        var low = 0
        var high = intervals.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (intervals[mid].endMs <= positionMs) low = mid + 1 else high = mid
        }
        return intervals.getOrNull(low)
    }
}
