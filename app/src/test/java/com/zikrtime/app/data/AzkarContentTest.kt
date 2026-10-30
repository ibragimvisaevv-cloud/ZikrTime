package com.zikrtime.app.data

import org.junit.Assert.*
import org.junit.Test

class AzkarContentTest {
    @Test fun collectionsUsePeriodSpecificTextsRatherThanTheTasbihList() {
        val morning = AzkarContent.items("morning")
        val evening = AzkarContent.items("evening")
        assertEquals(9, morning.size)
        assertEquals(9, evening.size)
        assertTrue(morning.first().arabic.startsWith("أَصْبَحْنَا"))
        assertTrue(evening.first().arabic.startsWith("أَمْسَيْنَا"))
        assertTrue(morning.first().arabic.contains("هَذَا الْيَوْمِ"))
        assertTrue(evening.first().arabic.contains("هَذِهِ اللَّيْلَةِ"))
        assertFalse(morning.any { it.id == "evening_perfect_words" })
        assertFalse(evening.any { it.id == "morning_creation_praise" })
    }

    @Test fun repetitionCountsRemainAttachedToTheirSources() {
        val expected = mapOf(
            "ikhlas" to 3, "falaq" to 3, "nas" to 3,
            "sayyid_istighfar" to 1, "bismillah_protection" to 3,
            "subhanallah_100" to 100, "morning_creation_praise" to 3,
            "evening_perfect_words" to 3,
        )
        (AzkarContent.items("morning") + AzkarContent.items("evening")).forEach { entry ->
            expected[entry.id]?.let { assertEquals(entry.id, it, entry.repetitions) }
        }
        for (entry in AzkarContent.items("morning") + AzkarContent.items("evening")) {
            assertEquals(entry.id, entry.repetitions != 1, entry.countIsPrescribed)
        }
        val eveningProtection = AzkarContent.items("evening").last()
        assertEquals("https://sunnah.com/tirmidhi:3604b", eveningProtection.sourceUrl)
        assertEquals("https://sunnah.com/muslim:2692",
            AzkarContent.items("morning").first { it.id == "subhanallah_100" }.sourceUrl)
    }

    @Test fun bikaWordingIsNotMixedWithAnotherNarration() {
        val morning = AzkarContent.items("morning")[1]
        val evening = AzkarContent.items("evening")[1]
        assertEquals("https://sunnah.com/abudawud:5068", evening.sourceUrl)
        assertTrue(morning.arabic.contains("بِكَ أَصْبَحْنَا وَبِكَ أَمْسَيْنَا"))
        assertTrue(evening.arabic.contains("بِكَ أَمْسَيْنَا وَبِكَ نَحْيَا"))
        assertTrue(evening.arabic.endsWith("وَإِلَيْكَ النُّشُورُ."))
    }

    @Test fun everyCardCanBeReadOfflineAndHasStableIdentityAndAttribution() {
        for (period in listOf("morning", "evening")) {
            val entries = AzkarContent.items(period)
            assertEquals(entries.size, entries.map { it.id }.toSet().size)
            for (entry in entries) {
                assertTrue(entry.id.matches(Regex("[a-z0-9_]+")))
                assertTrue(entry.arabic.any { it in '\u0621'..'\u064A' })
                assertTrue(entry.repetitions > 0)
                assertTrue(listOf(entry.titleRu, entry.titleEn, entry.meaningRu, entry.meaningEn,
                    entry.transliterationRu, entry.transliterationEn, entry.sourceLabel).all { it.isNotBlank() })
                assertTrue(entry.sourceUrl.startsWith("https://sunnah.com/"))
            }
        }
        assertEquals(AzkarContent.items("morning"), AzkarContent.items("invalid"))
    }
}
