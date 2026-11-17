package com.zikrtime.app.ui

import com.zikrtime.app.audio.QuranReadingPhrase
import com.zikrtime.app.data.QuranTranscriptionAyah
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuranTranscriptionHighlightTest {
    private val arabicPair = QuranReadingPhrase(2, 4, listOf(7..12, 20..25))

    @Test fun usesCyrillicOffsetsAndPreservesTanzilPositions() {
        val verse = QuranTranscriptionAyah("первое второе", mapOf(2 to 0..5, 4 to 7..12))
        assertEquals(QuranReadingPhrase(2, 4, listOf(0..5, 7..12)), arabicPair.inDisplayedText(verse, 4, null))
        assertEquals(arabicPair.inDisplayedText(verse, 2, 4), arabicPair.inDisplayedText(verse, 4, null))
    }

    @Test fun neverHighlightsAnotherWordWhenAlignmentIsMissing() {
        val partial = QuranTranscriptionAyah("один", mapOf(2 to 0..3))
        assertEquals(QuranReadingPhrase(2, 2, listOf(0..3)), arabicPair.inDisplayedText(partial, 2, null))
        assertNull(arabicPair.inDisplayedText(partial, 4, null))
        assertNull(arabicPair.inDisplayedText(partial, 2, 4))
        assertEquals(arabicPair, arabicPair.inDisplayedText(null, 4, null))
    }
}
