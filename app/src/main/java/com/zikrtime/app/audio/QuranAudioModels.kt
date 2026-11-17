package com.zikrtime.app.audio

import java.net.URI
import org.json.JSONObject

enum class QuranReciter(val id: String, val russian: String, val english: String, internal val timingHash: String) {
    ALAFASY("mishary-alafasy", "Мишари Рашид Аль-Афаси", "Mishary Rashid Alafasy", QuranAyahTimingCatalog.SHA256),
    DOSARI("yasser-dosari", "Ясир Ад-Досари", "Yasser Al-Dosari", "48a77f1967e2d30553e1ebf349972fbf1e4df35fe89d7d87b6777e141b36e977"),
    HUSARY("mahmoud-husary", "Махмуд Халиль Аль-Хусари", "Mahmoud Khalil Al-Husary", "f63f0bbe7987ab7ce5678386b8857f5d959c7d968ae5df26da3518d9c476fa47"),
    MINSHAWI("muhammad-minshawi", "Мухаммад Сиддик Аль-Миншави", "Muhammad Siddiq Al-Minshawi", "6a757d7fd880815fe67b6c1b82938668ea8dc6a71fbe6df221f3f660c902d0db");

    fun nameIn(russianLanguage: Boolean): String = if (russianLanguage) russian else english
    internal val assetDirectory get() = if (this == ALAFASY) "quran_audio" else "quran_audio/$id"
    internal val catalogPath get() = if (this == ALAFASY) "$assetDirectory/alafasy.json" else "$assetDirectory/catalog.json"
    internal val timingPath get() = if (this == ALAFASY) "$assetDirectory/alafasy_ayah_timings.json" else "$assetDirectory/ayah_timings.json"
    internal fun cacheKey(surah: Int) = if (this == ALAFASY) "alafasy-v1-$surah" else "$id-r1-$surah"
    internal fun url(surah: Int) = "https://cdn.mp3quran.net/audio/$id/r1/" + surah.toString().padStart(3, '0') + ".mp3"

    companion object {
        fun fromId(id: String?): QuranReciter? = entries.firstOrNull { it.id == id }
    }
}

data class QuranAudioTrack(val surahId: Int, val url: String, val sizeBytes: Long,
                          val reciter: QuranReciter = QuranReciter.ALAFASY) {
    val cacheKey: String get() = reciter.cacheKey(surahId)
}

enum class QuranAudioDownloadStatus {
    QUEUED, WAITING_FOR_WIFI, WAITING_FOR_NETWORK, DOWNLOADING, PAUSED, COMPLETED, FAILED, REMOVING,
}

enum class QuranAudioStorageError { CATALOG, STORAGE, NOT_ENOUGH_SPACE, NETWORK, START_FAILED }

data class QuranAudioDownload(
    val surahId: Int,
    val status: QuranAudioDownloadStatus,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val error: QuranAudioStorageError? = null,
)

data class QuranAudioStorageState(
    val ready: Boolean = false,
    val downloads: Map<Int, QuranAudioDownload> = emptyMap(),
    val temporaryBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val wifiOnly: Boolean = true,
    val clearingCache: Boolean = false,
    val error: QuranAudioStorageError? = null,
)

/** Only the bundled, reviewed catalogue for this recording can supply media URLs. */
internal data class QuranAudioCatalog(
    val reciter: String,
    val source: String,
    val permissionSource: String,
    val tracks: List<QuranAudioTrack>,
) {
    companion object {
        fun parse(text: String, voice: QuranReciter = QuranReciter.ALAFASY): QuranAudioCatalog {
            val root = JSONObject(text)
            if (voice != QuranReciter.ALAFASY) require(root.getString("reciter_id") == voice.id)
            val entries = root.getJSONArray("tracks")
            require(entries.length() == 114) { "Incomplete audio catalogue" }
            val tracks = (0 until entries.length()).map { index ->
                val item = entries.getJSONObject(index)
                val id = item.getInt("surah_id")
                val url = item.getString("url")
                val size = item.getLong("size_bytes")
                require(id in 1..114 && size > 0) { "Invalid audio entry" }
                val uri = URI(url)
                val expected = voice.url(id)
                require(url == expected && uri.scheme == "https" && uri.userInfo == null &&
                    uri.port == -1 && uri.query == null && uri.fragment == null) { "Untrusted audio URL" }
                QuranAudioTrack(id, url, size, voice)
            }
            require(tracks.map { it.surahId }.toSet() == (1..114).toSet()) { "Duplicate or missing surah" }
            return QuranAudioCatalog(root.getString("reciter"), root.getString("source"),
                root.getString("permission_source"), tracks.sortedBy { it.surahId })
        }
    }
}

/** These rules deliberately distinguish an interrupted/evicted file from an offline download. */
internal object QuranAudioPolicy {
    const val TEMPORARY_CACHE_BYTES = 100L * 1024 * 1024
    const val FREE_SPACE_RESERVE = 16L * 1024 * 1024

    fun isOfflineReady(completed: Boolean, contentLength: Long, cachedBytes: Long,
        expectedLength: Long = contentLength): Boolean =
        completed && contentLength > 0 && contentLength == expectedLength && cachedBytes >= contentLength

    fun enoughSpace(available: Long, remainingBytes: Long, queuedBytes: Long): Boolean {
        if (available < FREE_SPACE_RESERVE || remainingBytes < 0 || queuedBytes < 0) return false
        val usable = available - FREE_SPACE_RESERVE
        return remainingBytes <= usable && queuedBytes <= usable - remainingBytes
    }

    fun isPending(status: QuranAudioDownloadStatus): Boolean = status in setOf(
        QuranAudioDownloadStatus.QUEUED, QuranAudioDownloadStatus.WAITING_FOR_WIFI,
        QuranAudioDownloadStatus.WAITING_FOR_NETWORK, QuranAudioDownloadStatus.DOWNLOADING,
        QuranAudioDownloadStatus.PAUSED,
    )
}
