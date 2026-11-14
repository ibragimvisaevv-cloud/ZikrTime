package com.zikrtime.app.audio

import com.zikrtime.app.data.QuranContent
import java.io.DataInputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class QuranReadingPhraseTest {
    @Test fun pairsIgnoreDetachedSignsAndDoNotCrossMissingSpokenWords() {
        val text = "آمنوا ۞ بالله ۦ واليوم الآخر"
        val index = QuranPhraseIndex(text, setOf(1, 3, 5))
        val pair = requireNotNull(index.phrase(1))
        assertEquals(pair, index.phrase(3))
        assertEquals(listOf("آمنوا", "بالله"), pair.ranges.map { text.substring(it) })
        assertEquals(pair, index.phrase(1, 3))
        assertNull(index.phrase(3, 5))
        assertNull(index.phrase(6))
        assertEquals(5, index.phrase(5)?.lastWord)
        assertEquals(1, index.phrase(5)?.ranges?.size)
    }

    @Test fun onlyBriefNaturalGapsInsideOnePairMayHoldItsStyle() {
        val entries = listOf(QuranWordInterval(1, 1, 10, 55), QuranWordInterval(1, 2, 100, 150),
            QuranWordInterval(1, 3, 700, 780), QuranWordInterval(2, 1, 800, 900))
        val gap = requireNotNull(QuranWordTimings.cursorAt(entries, 60))
        assertEquals(1, gap.previousInGap?.word)
        assertEquals(2, gap.next.word)
        assertNull(QuranWordTimings.cursorAt(entries, 0)?.previousInGap)
        assertNull(QuranWordTimings.cursorAt(entries, 150)?.previousInGap)
        assertNull(QuranWordTimings.cursorAt(entries, 780)?.previousInGap)
        assertNull(QuranPlaybackState(connected = true, isPlaying = false, positionWord = 2).playingPhraseWord)
    }

    @Test fun maidaStartsAfterBasmalaAndFollowsTheRecordedRepetitions() {
        val assets = File("src/main/assets")
        val verses = QuranContent.parseArabic(File(assets, "quran/sources/quran-uthmani.txt").readBytes())
        val rows = DataInputStream(File(assets, "quran_audio/words/5.bin").inputStream().buffered()).use { input ->
            assertEquals(0x5A515731, input.readInt())
            assertEquals(5, input.readInt())
            List(input.readInt()) {
                QuranWordInterval(input.readUnsignedShort(), input.readUnsignedShort(), input.readInt(), input.readInt())
            }
        }
        val byAyah = rows.groupBy { it.ayah }
        val indexes = (1..7).associateWith { ayah ->
            QuranPhraseIndex(verses.getValue(5 to ayah), byAyah.getValue(ayah).map { it.word }.toSet())
        }
        fun phraseAt(ms: Long): QuranReadingPhrase? {
            val cursor = QuranWordTimings.cursorAt(rows, ms) ?: return null
            val word = cursor.next
            if (ms >= word.startMs) return indexes[word.ayah]?.phrase(word.word)
            val before = cursor.previousInGap ?: return null
            return indexes[before.ayah]?.phrase(before.word, word.word)
        }

        // 7265..13430 ms is the separate basmala. First spoken words use Tanzil tokens 5,6.
        assertNull(phraseAt(7265))
        assertNull(phraseAt(14389))
        assertEquals(5, phraseAt(14390)?.firstWord)
        assertEquals(6, phraseAt(17585)?.lastWord)
        assertEquals(7, phraseAt(18665)?.firstWord)
        assertNull(phraseAt(24900))
        assertNull(phraseAt(44758))
        assertEquals(1, phraseAt(45050)?.firstWord)
        // The Alafasy recording repeats 5:2 word 4 after word 7.
        assertEquals(7, phraseAt(55000)?.firstWord)
        assertEquals(3, phraseAt(57193)?.firstWord)
        assertNull(phraseAt(199000))
        for (ayah in 1..7) {
            for (row in byAyah.getValue(ayah)) {
                val phrase = requireNotNull(phraseAt(row.startMs.toLong()))
                assertTrue(row.word == phrase.firstWord || row.word == phrase.lastWord)
                assertTrue(phrase.ranges.size in 1..2)
            }
        }
    }
}
