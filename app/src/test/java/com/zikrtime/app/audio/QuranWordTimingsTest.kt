package com.zikrtime.app.audio

import com.zikrtime.app.data.QuranContent
import java.io.DataInputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class QuranWordTimingsTest {
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
