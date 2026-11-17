package com.zikrtime.app.data

import com.zikrtime.app.audio.isQuranSpokenToken
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranTranscriptionTest {
    private val assets = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }

    private fun read(surah: Int) = QuranTranscription.read(surah) { assets.resolve(it).inputStream() }

    @Test fun allPublishedVersesAreCyrillicAndMappingsAddressTheOriginalTokens() {
        val manifest = JSONObject(assets.resolve("quran/manifest.json").readText()).getJSONArray("surahs")
        val arabic = QuranContent.parseArabic(assets.resolve("quran/sources/quran-uthmani.txt").readBytes())
        val cyrillic = Regex("[А-Яа-яЁё'\\p{M} ]+")
        val basmalaRanges = read(1).first().wordRanges
        assertEquals(setOf(1, 2, 3, 4), basmalaRanges.keys)
        var total = 0
        var mappedWords = 0
        for (surah in 1..114) {
            val rows = read(surah)
            assertEquals(manifest.getJSONObject(surah - 1).getInt("ayahCount"), rows.size)
            if (surah != 9) assertEquals(basmalaRanges, rows.first().wordRanges.filterKeys { it <= 4 })
            for ((index, row) in rows.withIndex()) {
                assertTrue("$surah:${index + 1}", cyrillic.matches(row.text))
                assertTrue(row.wordRanges.isNotEmpty())
                mappedWords += row.wordRanges.size
                val original = arabic.getValue(surah to index + 1).split(Regex("\\s+"))
                var previousEnd = -1
                for ((word, range) in row.wordRanges) {
                    assertTrue(isQuranSpokenToken(original[word - 1]))
                    assertTrue(range.first > previousEnd && range.last < row.text.length)
                    assertTrue(row.text.substring(range).isNotBlank())
                    previousEnd = range.last
                }
            }
            total += rows.size
        }
        assertEquals(6236, total)
        assertEquals(77571, mappedWords)
    }

    @Test fun basmalaAndDisjointLettersKeepTheirVerseNumbers() {
        val basmala = "бисмиллахир рахманир рахиим"
        assertEquals(basmala, read(1).first().text)
        assertEquals("$basmala алиф лам мим", read(2).first().text)
        assertFalse(read(9).first().text.startsWith(basmala, ignoreCase = true))
        assertTrue(read(13).first().text.startsWith("$basmala алиф лам мим ра "))
        assertEquals("$basmala ха мим", read(45).first().text.lowercase())
        assertTrue(read(97).first().text.startsWith("$basmala "))
    }
}
