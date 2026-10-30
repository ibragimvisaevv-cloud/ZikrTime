package com.zikrtime.app.data

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QuranContentTest {
    private fun asset(name: String): File = listOf(File("src/main/assets/quran/$name"), File("app/src/main/assets/quran/$name"))
        .first { it.isFile }

    private val counts = listOf(7,286,200,176,120,165,206,75,129,109,123,111,43,52,99,128,111,110,98,135,112,78,118,64,77,227,93,88,69,60,34,30,73,54,45,83,182,88,75,85,54,53,89,59,37,35,38,29,18,45,60,49,62,55,78,96,29,22,24,13,14,11,11,18,12,12,30,52,52,44,28,28,20,56,40,31,50,40,46,42,29,19,36,25,22,17,19,26,30,20,15,21,11,8,8,19,5,8,8,11,11,8,3,9,5,4,7,3,6,3,5,4,5,6)
    private val surahs get() = counts.mapIndexed { i, n -> QuranSurah(i + 1, "", "", "", n) }

    @Test fun officialArabicSnapshotIsByteIdenticalAndComplete() {
        val bytes = asset("sources/quran-uthmani.txt").readBytes()
        assertEquals("bf4f57b968d03f4131c070b1e285da9be0e0a108a21c910e872801ca273312c8", QuranContent.sha256(bytes))
        val rows = QuranContent.parseArabic(bytes)
        assertEquals(6236, rows.size)
        QuranContent.requireCoverage(rows.keys, surahs)
        assertEquals(286, rows.keys.count { it.first == 2 })
        assertEquals(6, rows.keys.count { it.first == 114 })
        assertTrue(String(bytes, Charsets.UTF_8).contains("CHANGING IT IS NOT ALLOWED"))
    }

    @Test fun parserPreservesCodepointsWhitespaceAndSourceBasmala() {
        val original = "  بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ الٓمٓ  "
        val result = QuranContent.parseArabic("# source notice\n2|1|$original\n".toByteArray(Charsets.UTF_8))
        assertEquals(original, result.getValue(2 to 1))
        val official = QuranContent.parseArabic(asset("sources/quran-uthmani.txt").readBytes())
        assertTrue(official.getValue(2 to 1).startsWith(official.getValue(1 to 1)))
        assertFalse(official.getValue(9 to 1).startsWith(official.getValue(1 to 1)))
    }

    @Test fun duplicateOrMalformedArabicIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { QuranContent.parseArabic("1|1|ا\n1|1|ب".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { QuranContent.parseArabic("1|1|".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { QuranContent.parseArabic("115|1|ا".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { QuranContent.parseArabic("1|ا".toByteArray()) }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) { QuranContent.parseArabic(byteArrayOf(0xc3.toByte(), 0x28)) }
    }

    @Test fun missingOrMismatchedVerseIsRejectedEvenAtSameRecordCount() {
        val rows = QuranContent.parseArabic(asset("sources/quran-uthmani.txt").readBytes())
        assertThrows(IllegalArgumentException::class.java) { QuranContent.requireCoverage(rows.keys - (2 to 255), surahs) }
        val changed = rows.keys - (2 to 255) + (1 to 8)
        assertEquals(6236, changed.size)
        assertThrows(IllegalArgumentException::class.java) { QuranContent.requireCoverage(changed, surahs) }
    }

    @Test fun missingOrTamperedManifestFailsWithoutReadingText() {
        assertThrows(java.io.FileNotFoundException::class.java) { QuranContent.read { throw java.io.FileNotFoundException(it) } }
        var reads = 0
        assertThrows(IllegalArgumentException::class.java) { QuranContent.read { reads++; "{}".toByteArray() } }
        assertEquals(1, reads)
    }

    @Test fun completeBundledReaderPreservesEverySourceField() {
        val book = QuranContent.read { asset(it).readBytes() }
        assertEquals(114, book.surahs.size)
        assertEquals(6236, book.ayahs.size)
        assertTrue(book.surahs.all { it.nameRu.any { c -> c in '\u0400'..'\u04ff' } })
        val arabic = QuranContent.parseArabic(asset("sources/quran-uthmani.txt").readBytes())
        var notes = 0
        book.surahs.forEach { surah ->
            val source = JSONObject(asset("sources/russian_rwwad/${surah.id}.json").readText(Charsets.UTF_8)).getJSONArray("result")
            val actual = book.verses(surah.id)
            assertEquals(surah.ayahCount, actual.size)
            assertEquals(surah.ayahCount, source.length())
            for (i in 0 until source.length()) {
                val row = source.getJSONObject(i)
                val verse = actual.single { it.number == row.getInt("aya") }
                assertEquals(arabic.getValue(verse.surah to verse.number), verse.arabic)
                assertEquals(row.getString("translation"), verse.translation)
                assertEquals(row.getString("footnotes"), verse.footnotes)
                if (verse.footnotes.isNotEmpty()) notes++
            }
        }
        assertEquals(73, notes)
        assertEquals(286, book.verses(2).last().number)
        assertEquals(6, book.verses(114).last().number)
    }

    @Test fun missingOrChangedBundledSourceCannotProducePartialBook() {
        assertThrows(java.io.FileNotFoundException::class.java) {
            QuranContent.read { path ->
                if (path == "sources/russian_rwwad/114.json") throw java.io.FileNotFoundException(path)
                asset(path).readBytes()
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            QuranContent.read { path ->
                val bytes = asset(path).readBytes()
                if (path == "sources/russian_rwwad/2.json") bytes + 0 else bytes
            }
        }
    }
}
