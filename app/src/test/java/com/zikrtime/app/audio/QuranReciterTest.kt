package com.zikrtime.app.audio

import com.zikrtime.app.data.QuranContent
import java.io.File
import java.util.zip.ZipFile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QuranReciterTest {
    private fun bytes(voice: QuranReciter, path: String): ByteArray {
        if (voice == QuranReciter.ALAFASY) return File("src/main/assets", path).readBytes()
        val packs = File("recitation-packs").listFiles { f -> f.name.startsWith(voice.id + "-") }!!
        for (pack in packs) ZipFile(pack).use { zip ->
            zip.getEntry(path)?.let { entry -> return zip.getInputStream(entry).use { it.readBytes() } }
        }
        error("Missing asset $path")
    }

    private fun timings(voice: QuranReciter): QuranAyahTimingCatalog {
        val manifest = JSONObject(File("src/main/assets/quran/manifest.json").readText()).getJSONArray("surahs")
        val counts = (0 until manifest.length()).associate { i ->
            val entry = manifest.getJSONObject(i)
            entry.getInt("id") to entry.getInt("ayahCount")
        }
        val data = bytes(voice, voice.timingPath)
        assertEquals(voice.timingHash, QuranContent.sha256(data))
        return QuranAyahTimingCatalog.parse(data.toString(Charsets.UTF_8), counts, voice)
    }

    @Test fun downloadsCannotBeReusedByOtherRecordingsAndOldKeysRemainReadable() {
        val keys = mutableSetOf<String>()
        for (voice in QuranReciter.entries) {
            val text = bytes(voice, voice.catalogPath).toString(Charsets.UTF_8)
            val tracks = QuranAudioCatalog.parse(text, voice).tracks
            for (track in tracks) {
                assertTrue(keys.add(track.cacheKey))
                assertEquals(voice.url(track.surahId), track.url)
                assertEquals(voice, track.reciter)
            }
            for (other in QuranReciter.entries.filter { it != voice }) {
                assertTrue(runCatching { QuranAudioCatalog.parse(text, other) }.isFailure)
            }
        }
        assertEquals(456, keys.size)
        assertTrue("alafasy-v1-5" in keys)
    }

    @Test fun allVoicesUseTheirOwnReviewedTrackTimeline() {
        val times = QuranReciter.entries.map { timings(it).timing(5, 2)!!.startMs }
        assertEquals(4, times.toSet().size)
        val recitation = timings(QuranReciter.DOSARI)
        assertEquals(44_450L, recitation.timing(92, 14)?.startMs)
        assertEquals(49_103L, recitation.timing(92, 15)?.startMs)
        assertEquals(14, recitation.timingAtOrAfter(92, 54_000L)?.reference?.ayah)
        assertEquals(53_943L, recitation.timingAtOrAfter(92, 54_000L)?.startMs)
        assertEquals(15, recitation.timingAtOrAfter(92, 59_023L)?.reference?.ayah)
    }
}
