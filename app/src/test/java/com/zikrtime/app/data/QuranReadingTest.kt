package com.zikrtime.app.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class QuranReadingTest {
    private fun book(): QuranBook = QuranContent.read { name ->
        listOf(File("src/main/assets/quran/$name"), File("app/src/main/assets/quran/$name"))
            .first { it.isFile }.readBytes()
    }

    @Test fun continuousReadingIncludesEveryOriginalVerseInOrder() {
        val book = book()
        for (surah in book.surahs) {
            val verses = book.verses(surah.id)
            val blocks = QuranReading.blocks(verses)
            assertEquals(verses, blocks.flatMap { it.verses })
            assertEquals(1, blocks.first().firstAyah)
            assertEquals(surah.ayahCount, blocks.last().lastAyah)
            // Every code point of the verified source must remain as a substring
            // in the displayed text, including basmala and combining marks.
            blocks.forEach { block ->
                var position = 0
                block.verses.forEach { verse ->
                    val start = block.text.indexOf(verse.arabic, position)
                    assertTrue("Missing or changed ${verse.surah}:${verse.number}", start >= position)
                    position = start + verse.arabic.length
                }
            }
        }
    }

    @Test fun everyJumpAndModeSwitchResolvesToABlockContainingItsVerse() {
        val book = book()
        for (surah in book.surahs) {
            val blocks = QuranReading.blocks(book.verses(surah.id))
            for (number in 1..surah.ayahCount) {
                val block = blocks[QuranReading.blockIndex(blocks, number)]
                assertTrue(number in block.firstAyah..block.lastAyah)
            }
        }
        assertEquals(35, QuranReading.blockIndex(QuranReading.blocks(book.verses(2)), 286))
    }

    @Test fun displayMarkersDoNotTrimOrNormalizeOriginalSigns() {
        val original = "  بِسْمِ ٱللَّهِ الٓمٓ  "
        val block = QuranReading.blocks(listOf(QuranAyah(2, 1, original, ""))).single()
        assertEquals(original + "  ۝١", block.text)
        assertTrue(QuranReading.blocks(emptyList()).isEmpty())
    }

    @Test fun jumpAnchorsKeepAllVersesAndStartExactlyAtTheRequestedAyah() {
        val verses = book().verses(2)
        for (number in listOf(1, 8, 9, 255, 281, 286)) {
            val blocks = QuranReading.blocks(verses, number)
            assertEquals(verses, blocks.flatMap { it.verses })
            assertEquals(number, blocks[QuranReading.blockIndex(blocks, number)].firstAyah)
        }
    }

    @Test fun hitRangesKeepExactArabicAndMapEveryCharacterAndMarkerToItsAyah() {
        val book = book()
        for (surah in book.surahs) for (block in QuranReading.blocks(book.verses(surah.id))) {
            assertEquals(block.verses.size, block.ranges.size)
            block.ranges.zip(block.verses).forEach { (range, verse) ->
                assertEquals(verse.arabic, block.text.substring(range.start, range.arabicEnd))
                for (offset in range.start until range.end) assertEquals(verse.number, block.ayahAt(offset))
            }
            assertNull(block.ayahAt(-1))
        }
    }
}

