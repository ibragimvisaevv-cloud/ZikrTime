package com.zikrtime.app.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdhanSoundTest {
    @Test fun fajrRecordingCannotBeChosenForAllPrayers() {
        val regular = AdhanSound.eligibleVoices(forFajrAlarm = false)
        assertFalse(regular.any { it.fajrOnly || it.id == "adhan_fajr" })
        assertTrue(regular.any { it.id == "adhan_madinah" })
        assertTrue(regular.any { it.id == "adhan_makkah" })
        assertTrue(AdhanSound.eligibleVoices(forFajrAlarm = true).any { it.id == "adhan_fajr" && it.fajrOnly })
    }

    @Test fun existingSavedSoundIdsStillResolveToTheirOriginalRecordings() {
        listOf("adhan", "adhan_2", "adhan_3").forEachIndexed { index, id ->
            assertEquals(id, AdhanSound.voice(id).id)
            assertEquals(index + 1, AdhanSound.voice(id).number)
        }
        assertEquals("adhan", AdhanSound.voice("unknown_saved_id").id)
    }

    @Test fun restoredFajrPreferenceFallsBackToRegularAdhanForNotifications() {
        assertEquals("adhan", AdhanSound.notificationVoice("adhan_fajr").id)
        assertEquals("adhan", AdhanSound.notificationVoice("unknown_saved_id").id)
        assertEquals("adhan_madinah", AdhanSound.notificationVoice("adhan_madinah").id)
        assertEquals("adhan_fajr", AdhanSound.voice("adhan_fajr").id)
    }
}
