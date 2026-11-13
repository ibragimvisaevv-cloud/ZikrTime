package com.zikrtime.app.audio

import java.net.URI
import org.json.JSONObject

data class QuranAudioTrack(val surahId: Int, val url: String, val sizeBytes: Long) {
    val cacheKey: String get() = "alafasy-v1-$surahId"
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

/** Only the bundled, reviewed Alafasy/Hafs catalogue can supply media URLs. */
internal data class QuranAudioCatalog(
    val reciter: String,
    val source: String,
    val permissionSource: String,
    val tracks: List<QuranAudioTrack>,
) {
    companion object {
        const val PREFIX = "https://cdn.mp3quran.net/audio/mishary-alafasy/r1/"

        fun parse(text: String): QuranAudioCatalog {
            val root = JSONObject(text)
            val entries = root.getJSONArray("tracks")
            require(entries.length() == 114) { "Incomplete audio catalogue" }
            val tracks = (0 until entries.length()).map { index ->
                val item = entries.getJSONObject(index)
                val id = item.getInt("surah_id")
                val url = item.getString("url")
                val size = item.getLong("size_bytes")
                require(id in 1..114 && size > 0) { "Invalid audio entry" }
                val uri = URI(url)
                val expected = PREFIX + id.toString().padStart(3, '0') + ".mp3"
                require(url == expected && uri.scheme == "https" && uri.userInfo == null &&
                    uri.port == -1 && uri.query == null && uri.fragment == null) { "Untrusted audio URL" }
                QuranAudioTrack(id, url, size)
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
