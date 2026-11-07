package com.zikrtime.app.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdhanSoundTest {
    @Test fun morningRecordingIsRestrictedToMorningAlarm() {
        assertEquals(2, AdhanSound.eligibleVoices(false).size)
        assertFalse(AdhanSound.eligibleVoices(false).any { it.fajrOnly })
        assertTrue(AdhanSound.voice("adhan_fajr").fajrOnly)
        assertFalse(AdhanSound.notificationVoice("athan_khalaf_fajr").fajrOnly)
        assertFalse(AdhanSound.notificationVoice("adhan_fajr").fajrOnly)
    }

    @Test fun oldSettingsResolveToBundledRecordings() {
        listOf("adhan", "adhan_3", "adhan_madinah", "unknown").forEach {
            assertEquals("athan_khalaf_one", AdhanSound.voice(it).id)
        }
        listOf("adhan_2", "adhan_makkah").forEach { assertEquals("athan_khalaf_two", AdhanSound.voice(it).id) }
        assertEquals("athan_khalaf_fajr", AdhanSound.voice("adhan_fajr").id)
    }
}
