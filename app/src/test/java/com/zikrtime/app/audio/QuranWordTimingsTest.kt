package com.zikrtime.app.audio

import com.zikrtime.app.data.QuranContent
import java.io.DataInputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class QuranWordTimingsTest {
    @Test fun additionalDisplayWindowsAreDisjointAndKeepMeasuredSeekOnsets() {
        val root = File("src/main/assets/quran_audio")
        fun read(file: File, surah: Int) = DataInputStream(file.inputStream().buffered()).use { input ->
            assertEquals(0x5A515731, input.readInt())
            assertEquals(surah, input.readInt())
            List(input.readInt()) {
                QuranWordInterval(input.readUnsignedShort(), input.readUnsignedShort(), input.readInt(), input.readInt())
            }
        }
        val files = requireNotNull(File(root, "word_intervals").listFiles())
        assertTrue(files.isNotEmpty())
        for (file in files) {
            val surah = file.nameWithoutExtension.toInt()
            val old = read(File(root, "words/${file.name}"), surah)
            val extra = read(file, surah)
            val combined = (old + extra).sortedBy { it.startMs }
            var lastEnd = 0
            for (row in combined) {
                assertTrue(row.startMs >= lastEnd && row.endMs > row.startMs)
                assertEquals(row, QuranWordTimings.atOrAfter(combined, row.startMs.toLong()))
                lastEnd = row.endMs
            }
        }
    }

    @Test fun supplementalStartsAllowExactSeekingWithoutInventingAnIntervalEnd() {
        val root = File("src/main/assets/quran_audio")
        val verse = QuranAyahTiming(QuranAudioAyah(2, 10), 115900L, 129665L)
        val extra = DataInputStream(File(root, "word_starts/2.bin").inputStream().buffered()).use {
            QuranWordTimings.readAdditionalStarts(it, 2)
        }
        assertEquals(115900L, extra[10 to 1])
        assertEquals(QuranWordSeek(1, 115900L), QuranWordTimings.resolveSeek(emptyList(), verse, 1, extra))
        val index = QuranPhraseIndex("فِى قُلُوبِهِم", emptySet())
        assertNull(index.phrase(1))
        assertEquals(1, index.phrase(1, additionalWords = setOf(1))?.firstWord)
    }

    @Test fun selectingWordUsesFirstVerifiedOccurrenceOfTheRequestedAyah() {
        val verse = QuranAyahTiming(QuranAudioAyah(5, 2), 45050L, 126966L)
        val rows = listOf(
            QuranWordInterval(1, 4, 2000, 3200),
            QuranWordInterval(2, 4, 40000, 40400),
            QuranWordInterval(2, 4, 50385, 50745),
            QuranWordInterval(2, 4, 57193, 57785),
            QuranWordInterval(2, 12, 126966, 127020),
        )
        val extra = mapOf((2 to 7) to 56400L, (2 to 9) to 130000L, (1 to 8) to 57000L)
        fun seek(word: Int?) = QuranWordTimings.resolveSeek(rows, verse, word, extra)
        assertEquals(QuranWordSeek(4, 50385L), seek(4))
        assertEquals(5335L, seek(4).startMs - verse.startMs)
        assertEquals(QuranWordSeek(7, 56400L), seek(7))
        assertEquals(QuranWordSeek(7, 56400L), seek(8))
        assertEquals(QuranWordSeek(7, 56400L), seek(12))
        assertEquals(QuranWordSeek(4, 50385L), seek(5))
        listOf(null, 0, 3).forEach { word -> assertEquals(QuranWordSeek(null, verse.startMs), seek(word)) }
    }

    @Test fun packagedIntervalsAddressOriginalTextAndStayOrdered() {
        val assets = File("src/main/assets")
        val verses = QuranContent.parseArabic(File(assets, "quran/sources/quran-uthmani.txt").readBytes())
        for (surah in 1..114) {
            DataInputStream(File(assets, "quran_audio/words/$surah.bin").inputStream().buffered()).use { input ->
                assertEquals(0x5A515731, input.readInt())
                assertEquals(surah, input.readInt())
                val count = input.readInt()
                assertTrue(count > 0)
                var lastEnd = 0
                var lastAyah = 0
                var tokenCount = 0
                repeat(count) {
                    val ayah = input.readUnsignedShort()
                    val word = input.readUnsignedShort()
                    val start = input.readInt()
                    val end = input.readInt()
                    if (ayah != lastAyah) {
                        tokenCount = Regex("\\S+").findAll(verses.getValue(surah to ayah)).count()
                        lastAyah = ayah
                    }
                    assertTrue(word in 1..tokenCount && start >= lastEnd && end > start)
                    lastEnd = end
                }
                assertEquals(-1, input.read())
            }
        }
    }

    @Test fun boundarySearchPreservesSilenceAndRepeatedWordOccurrences() {
        val first = QuranWordInterval(1, 2, 1000, 2000)
        val repeated = QuranWordInterval(1, 2, 2200, 3100)
        val entries = listOf(first, repeated)
        assertEquals(first, QuranWordTimings.atOrAfter(entries, 1000L))
        assertEquals(repeated, QuranWordTimings.atOrAfter(entries, 2000L))
        assertEquals(repeated, QuranWordTimings.atOrAfter(entries, 2700L))
        assertNull(QuranWordTimings.atOrAfter(entries, 3100L))
        val original = "بِسْمِ ۞ ٱللَّهِ"
        assertEquals("ٱللَّهِ", original.substring(requireNotNull(quranWordRange(original, 3))))
    }
}
