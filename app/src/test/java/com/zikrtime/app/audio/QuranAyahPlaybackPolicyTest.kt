package com.zikrtime.app.audio

import com.zikrtime.app.data.QuranContent
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QuranAyahPlaybackPolicyTest {
    private fun source(): String = File("src/main/assets/quran_audio/alafasy_ayah_timings.json").readText()
    private fun counts(): Map<Int, Int> {
        val rows = JSONObject(File("src/main/assets/quran/manifest.json").readText()).getJSONArray("surahs")
        return (0 until rows.length()).associate { i ->
            val item = rows.getJSONObject(i)
            item.getInt("id") to item.getInt("ayahCount")
        }
    }

    @Test fun all6236CheckedAyahsUseTheReviewedRecordingTimes() {
        val bytes = File("src/main/assets/quran_audio/alafasy_ayah_timings.json").readBytes()
        assertEquals(QuranAyahTimingCatalog.SHA256, QuranContent.sha256(bytes))
        val counts = counts()
        val catalog = QuranAyahTimingCatalog.parse(source(), counts)
        assertEquals(6236, counts.entries.sumOf { (surah, count) ->
            (1..count).count { catalog.timing(surah, it)?.durationMs?.let { ms -> ms > 0L } == true }
        })
        assertEquals(QuranAyahTiming(QuranAudioAyah(114, 1), 6660L, 13108L), catalog.timing(114, 1))
        assertNull(catalog.timing(114, 7))
        assertNull(catalog.timing(114, 0)) // Separate basmala is not mislabelled as verse 1.
        assertNotNull(catalog.timing(1, 1)) // Al-Fatiha includes its numbered basmala.
        assertEquals(1231466L, catalog.timing(37, 151)?.endMs) // Publisher's adjacent repeated row is preserved.
    }

    @Test fun missingOverlappingOrDifferentRecordingTimingsAreRejected() {
        fun rejected(change: (JSONObject) -> Unit) {
            val json = JSONObject(source()).also(change)
            assertTrue(runCatching { QuranAyahTimingCatalog.parse(json.toString(), counts()) }.isFailure)
        }
        rejected { it.put("recitation", "r2") }
        rejected { it.getJSONArray("surahs").getJSONObject(0).put("complete", false) }
        rejected { it.getJSONArray("surahs").getJSONObject(0).getJSONArray("ayahs").remove(1) }
        rejected { it.getJSONArray("surahs").getJSONObject(0).getJSONArray("ayahs").getJSONArray(1).put(1, 0) }
        rejected { it.getJSONArray("surahs").remove(113) }
    }

    @Test fun finiteRepetitionCountsIncludeTheFirstReadingAndStopExactly() {
        val options = QuranAyahOptions(repeatCount = 3)
        assertEquals(QuranAyahPlaybackPolicy.Finish.REPEAT, QuranAyahPlaybackPolicy.finish(options, 1, true, true))
        assertEquals(QuranAyahPlaybackPolicy.Finish.REPEAT, QuranAyahPlaybackPolicy.finish(options, 2, true, true))
        assertEquals(QuranAyahPlaybackPolicy.Finish.STOP, QuranAyahPlaybackPolicy.finish(options, 3, true, true))
        assertEquals(QuranAyahPlaybackPolicy.Finish.STOP, QuranAyahPlaybackPolicy.finish(QuranAyahOptions(), 1, true, true))
    }

    @Test fun interruptedAndPausedPlaybackNeverStartsAnotherRepeatOrAyah() {
        listOf(QuranAyahOptions(0), QuranAyahOptions(3), QuranAyahOptions(1, continueAfter = true)).forEach { options ->
            assertEquals(QuranAyahPlaybackPolicy.Finish.STOP, QuranAyahPlaybackPolicy.finish(options, 1, false, true))
        }
        assertEquals(QuranAyahPlaybackPolicy.Finish.REPEAT,
            QuranAyahPlaybackPolicy.finish(QuranAyahOptions(0), Int.MAX_VALUE, true, true))
    }

    @Test fun continuationStartsOnlyAfterAllRepeatsAndStaysInsideTheSurah() {
        val options = QuranAyahOptions(3, continueAfter = true)
        assertEquals(QuranAyahPlaybackPolicy.Finish.REPEAT, QuranAyahPlaybackPolicy.finish(options, 2, true, true))
        assertEquals(QuranAyahPlaybackPolicy.Finish.NEXT, QuranAyahPlaybackPolicy.finish(options, 3, true, true))
        assertEquals(QuranAyahPlaybackPolicy.Finish.STOP, QuranAyahPlaybackPolicy.finish(options, 3, true, false))
    }

    @Test fun unsupportedSpeedsRepeatsAndUntrustedMediaIdsCannotBecomeRequests() {
        listOf(-1, 2, 11, Int.MAX_VALUE).forEach { assertNull(QuranAyahPlaybackPolicy.options(it, 1f, false)) }
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, 2f).forEach {
            assertNull(QuranAyahPlaybackPolicy.options(1, it, false))
        }
        QuranAyahPlaybackPolicy.speeds.forEach { assertNotNull(QuranAyahPlaybackPolicy.options(3, it, true)) }
        listOf("ayah:114:0", "ayah:115:1", "ayah:001:1", "ayah:1:1:extra", "https://evil.example/001.mp3").forEach {
            assertNull(QuranAyahPlaybackPolicy.reference(it))
        }
        assertEquals(QuranAudioAyah(114, 6), QuranAyahPlaybackPolicy.reference("ayah:114:6"))
    }
}
