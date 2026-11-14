package com.zikrtime.app.audio

import org.json.JSONObject

/** A verse is always addressed by its checked surah/ayah pair, never by an arbitrary audio URL. */
data class QuranAudioAyah(val surahId: Int, val ayah: Int) {
    val mediaId: String get() = "ayah:$surahId:$ayah"
}

data class QuranAyahOptions(
    /** Total number of readings, including the first. Zero means repeat until the user pauses. */
    val repeatCount: Int = 1,
    val speed: Float = 1f,
    val continueAfter: Boolean = false,
)

data class QuranAyahTiming(val reference: QuranAudioAyah, val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

internal object QuranAyahPlaybackPolicy {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f)
    val repetitions = setOf(0, 1, 3, 5, 10)

    fun options(repeatCount: Int, speed: Float, continueAfter: Boolean): QuranAyahOptions? =
        if (repeatCount in repetitions && speed in speeds) QuranAyahOptions(repeatCount, speed, continueAfter) else null

    fun reference(mediaId: String): QuranAudioAyah? {
        val parts = mediaId.split(':')
        if (parts.size != 3 || parts[0] != "ayah") return null
        val surah = parts[1].toIntOrNull() ?: return null
        val ayah = parts[2].toIntOrNull() ?: return null
        if (surah !in 1..114 || ayah !in 1..286) return null
        val ref = QuranAudioAyah(surah, ayah)
        return ref.takeIf { it.mediaId == mediaId }
    }

    /** A paused/interrupted recording never starts a new repetition in the background. */
    fun finish(options: QuranAyahOptions, iteration: Int, wasPlaying: Boolean, hasNext: Boolean): Finish = when {
        !wasPlaying -> Finish.STOP
        options.repeatCount == 0 || iteration < options.repeatCount -> Finish.REPEAT
        options.continueAfter && hasNext -> Finish.NEXT
        else -> Finish.STOP
    }

    enum class Finish { STOP, REPEAT, NEXT }
}

/** Complete official timing rows for exactly the same MP3Quran recording as the surah catalogue. */
internal class QuranAyahTimingCatalog private constructor(private val verses: Map<QuranAudioAyah, QuranAyahTiming>) {
    fun timing(surahId: Int, ayah: Int): QuranAyahTiming? = verses[QuranAudioAyah(surahId, ayah)]

    private val bySurah = verses.values.groupBy { it.reference.surahId }

    /** First verse whose end is strictly after this position. It may start after an unnumbered interval. */
    fun timingAtOrAfter(surahId: Int, positionMs: Long): QuranAyahTiming? {
        if (positionMs < 0) return null
        val rows = bySurah[surahId] ?: return null
        val result = rows.binarySearchBy(positionMs) { it.endMs }
        val index = if (result < 0) -result - 1 else result + 1
        return rows.getOrNull(index)
    }

    companion object {
        const val SHA256 = "ee480450fff61cf1bd3ab1125adfafd56312288a45804a172d700f1bf53dd40b"

        fun parse(text: String, counts: Map<Int, Int>): QuranAyahTimingCatalog {
            require(counts.keys == (1..114).toSet() && counts.values.sum() == 6236) { "Invalid verse counts" }
            val root = JSONObject(text)
            require(root.getInt("schema_version") == 1 && root.getString("reciter") == "mishary-alafasy" &&
                root.getString("recitation") == "r1") { "Timing recording does not match audio" }
            val surahs = root.getJSONArray("surahs")
            require(surahs.length() == 114) { "Incomplete timing catalogue" }
            val seen = mutableSetOf<Int>()
            val verses = linkedMapOf<QuranAudioAyah, QuranAyahTiming>()
            for (i in 0 until surahs.length()) {
                val surah = surahs.getJSONObject(i)
                val id = surah.getInt("surah")
                require(id in counts && seen.add(id) && surah.getBoolean("complete")) { "Incomplete surah timing" }
                val rows = surah.getJSONArray("ayahs")
                var previousEnd = 0L
                var expectedAyah = 1
                for (j in 0 until rows.length()) {
                    val row = rows.getJSONArray(j)
                    require(row.length() == 3) { "Invalid timing row" }
                    val ayah = row.getInt(0)
                    val start = row.getLong(1)
                    val end = row.getLong(2)
                    require(start >= 0L && end > start && start >= previousEnd) { "Invalid or overlapping timing" }
                    previousEnd = end
                    // Basmala (0) and istiadhah (-1) are not falsely labelled as Quran ayahs.
                    if (ayah in -1..0) continue
                    require(ayah == expectedAyah && ayah <= counts.getValue(id)) { "Missing or mismatched ayah timing" }
                    expectedAyah++
                    val ref = QuranAudioAyah(id, ayah)
                    require(verses.put(ref, QuranAyahTiming(ref, start, end)) == null)
                }
                require(expectedAyah == counts.getValue(id) + 1) { "Missing ayah timing" }
            }
            require(verses.size == 6236)
            return QuranAyahTimingCatalog(verses)
        }
    }
}
