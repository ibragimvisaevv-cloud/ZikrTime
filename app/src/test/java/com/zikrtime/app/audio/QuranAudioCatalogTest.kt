package com.zikrtime.app.audio

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QuranAudioCatalogTest {
    private fun source(): String = File("src/main/assets/quran_audio/alafasy.json").readText()

    @Test fun verifiedCatalogueHasExactly114UniqueAlafasyTracksAndActualSizes() {
        val catalog = QuranAudioCatalog.parse(source())
        assertEquals((1..114).toList(), catalog.tracks.map { it.surahId })
        assertTrue(catalog.tracks.all { it.sizeBytes > 0 })
        assertEquals(837734L, catalog.tracks.first().sizeBytes)
        assertEquals(120641555L, catalog.tracks[1].sizeBytes)
        assertEquals("alafasy-v1-114", catalog.tracks.last().cacheKey)
    }

    @Test fun urlsCannotEscapeTheReviewedReaderAndHost() {
        listOf("http://cdn.mp3quran.net/audio/mishary-alafasy/r1/001.mp3",
            "https://evil.example/001.mp3", "https://cdn.mp3quran.net/audio/other/r1/001.mp3",
            "https://cdn.mp3quran.net/audio/mishary-alafasy/r1/001.mp3?override=1").forEach { url ->
            val json = JSONObject(source())
            json.getJSONArray("tracks").getJSONObject(0).put("url", url)
            assertTrue(runCatching { QuranAudioCatalog.parse(json.toString()) }.isFailure)
        }
    }

    @Test fun duplicateSurahsAndIncompleteCataloguesAreRejected() {
        val duplicate = JSONObject(source())
        val tracks = duplicate.getJSONArray("tracks")
        tracks.put(1, tracks.getJSONObject(0))
        assertTrue(runCatching { QuranAudioCatalog.parse(duplicate.toString()) }.isFailure)
        tracks.remove(113)
        assertTrue(runCatching { QuranAudioCatalog.parse(duplicate.toString()) }.isFailure)
    }
}
