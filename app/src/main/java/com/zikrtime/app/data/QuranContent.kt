package com.zikrtime.app.data

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

data class QuranSurah(val id: Int, val nameArabic: String, val nameRu: String, val nameLatin: String, val ayahCount: Int)
data class QuranAyah(val surah: Int, val number: Int, val arabic: String, val translation: String, val footnotes: String = "")
data class QuranBook(val surahs: List<QuranSurah>, val ayahs: List<QuranAyah>) {
    private val bySurah by lazy { ayahs.groupBy { it.surah } }
    fun verses(surah: Int): List<QuranAyah> = bySurah[surah].orEmpty()
}

/** Pure parser. The same byte hashes and coverage checks run in tests and on device. */
object QuranContent {
    const val MANIFEST_SHA256 = "0ad3cd6003fda2bcc8ea3dbde94925e67a1594c2b58c01ab62df98dfa17f6b84"

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun utf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    fun parseArabic(bytes: ByteArray): Map<Pair<Int, Int>, String> {
        val rows = linkedMapOf<Pair<Int, Int>, String>()
        utf8(bytes).lineSequence().forEach { line ->
            if (line.isEmpty() || line.startsWith('#')) return@forEach
            val pieces = line.split('|', limit = 3)
            require(pieces.size == 3) { "Malformed Quran source record" }
            val key = pieces[0].toInt() to pieces[1].toInt()
            require(key.first in 1..114 && key.second in 1..286 && pieces[2].isNotBlank())
            require(rows.put(key, pieces[2]) == null) { "Duplicate Quran verse" }
        }
        return rows
    }

    fun requireCoverage(actual: Set<Pair<Int, Int>>, surahs: List<QuranSurah>) {
        require(surahs.map { it.id } == (1..114).toList() && surahs.sumOf { it.ayahCount } == 6236)
        require(surahs.all { it.ayahCount in 1..286 })
        val expected = surahs.flatMap { s -> (1..s.ayahCount).map { s.id to it } }.toSet()
        require(actual == expected) { "Incomplete or mismatched Quran verses" }
    }

    /** [read] receives a relative asset path under quran/. Missing/corrupt data fails closed. */
    fun read(read: (String) -> ByteArray): QuranBook {
        val manifestBytes = read("manifest.json")
        require(sha256(manifestBytes) == MANIFEST_SHA256) { "Quran manifest integrity check failed" }
        val manifest = JSONObject(utf8(manifestBytes))
        require(manifest.getInt("schemaVersion") == 1)
        val hashes = manifest.getJSONObject("files")
        fun checked(path: String): ByteArray {
            val bytes = read(path)
            require(sha256(bytes) == hashes.getString(path)) { "Quran source integrity check failed: $path" }
            return bytes
        }
        val metadata = manifest.getJSONArray("surahs")
        val surahs = (0 until metadata.length()).map { i ->
            val s = metadata.getJSONObject(i)
            QuranSurah(s.getInt("id"), s.getString("nameArabic"), s.getString("nameRu"), s.getString("nameLatin"), s.getInt("ayahCount"))
        }
        require(surahs.map { it.id } == (1..114).toList() && surahs.sumOf { it.ayahCount } == 6236)
        require(surahs.all { it.ayahCount in 1..286 })
        val expected = surahs.flatMap { s -> (1..s.ayahCount).map { s.id to it } }.toSet()
        val arabic = parseArabic(checked("sources/quran-uthmani.txt"))
        requireCoverage(arabic.keys, surahs)
        val verses = mutableListOf<QuranAyah>()
        val seen = mutableSetOf<Pair<Int, Int>>()
        surahs.forEach { s ->
            val response = JSONObject(utf8(checked("sources/russian_rwwad/${s.id}.json")))
            val rows = response.getJSONArray("result")
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val key = row.getInt("sura") to row.getInt("aya")
                require(key in expected && key.first == s.id && seen.add(key)) { "Mismatched translation verse" }
                val translation = row.getString("translation")
                require(translation.isNotBlank()) { "Empty Quran translation" }
                verses += QuranAyah(key.first, key.second, arabic.getValue(key), translation, row.optString("footnotes", ""))
            }
        }
        require(seen == expected) { "Incomplete Quran translation" }
        return QuranBook(surahs, verses.sortedWith(compareBy(QuranAyah::surah, QuranAyah::number)))
    }
}
