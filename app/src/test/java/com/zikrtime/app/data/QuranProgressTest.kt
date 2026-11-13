package com.zikrtime.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuranProgressTest {
    private val book = QuranBook(
        surahs = listOf(
            QuranSurah(1, "", "", "", 7),
            QuranSurah(2, "", "", "", 286),
            QuranSurah(114, "", "", "", 6),
        ),
        ayahs = emptyList(),
    )

    @Test fun readingLocationsAllowFirstAndLastVerses() {
        assertEquals(QuranProgress.Location(1, 1), QuranProgress.parse("1:1", book))
        assertEquals(QuranProgress.Location(1, 7), QuranProgress.parse("1:7", book))
        assertEquals(QuranProgress.Location(2, 286), QuranProgress.parse("2:286", book))
        assertEquals(QuranProgress.Location(114, 6), QuranProgress.parse("114:6", book))
    }

    @Test fun corruptedBookmarksCannotOpenNonexistentVerses() {
        val invalid = listOf(null, "", "1", "1:", ":1", "1:1:1", "hello", "one:1",
            "0:1", "115:1", "3:1", "1:0", "1:-1", "1:8", "2:287", "114:7",
            "2147483648:1", "1:2147483648")
        invalid.forEach { raw -> assertNull("Unexpected valid location: $raw", QuranProgress.parse(raw, book)) }
    }
}
