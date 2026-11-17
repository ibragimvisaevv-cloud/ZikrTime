package com.zikrtime.app.data

import android.content.Context
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Word keys are one-based token positions in the original Tanzil string; ranges index this Cyrillic text. */
internal data class QuranTranscriptionAyah(val text: String, val wordRanges: Map<Int, IntRange>)

internal object QuranTranscription {
    private const val PACKS = 16

    suspend fun load(context: Context, surah: Int): List<QuranTranscriptionAyah> = withContext(Dispatchers.IO) {
        read(surah) { context.assets.open(it) }
    }

    internal fun read(surah: Int, open: (String) -> InputStream): List<QuranTranscriptionAyah> {
        require(surah in 1..114)
        val path = "quran_transcription/ru/${(surah - 1) % PACKS}.qtr"
        val json = open(path).use { input -> GZIPInputStream(input).bufferedReader(Charsets.UTF_8).use { it.readText() } }
        val verses = JSONObject(json).getJSONArray(surah.toString())
        return List(verses.length()) { index ->
            val row = verses.getJSONArray(index)
            val text = row.getString(0)
            val words = row.getJSONArray(1)
            val ranges = buildMap {
                for (i in 0 until words.length() step 3) {
                    put(words.getInt(i), words.getInt(i + 1) until words.getInt(i + 2))
                }
            }
            QuranTranscriptionAyah(text, ranges)
        }
    }
}
