@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zikrtime.app.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MimeTypes
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.zikrtime.app.data.QuranContent
import org.json.JSONObject

/** One owner per process: playback never opens a second cache for the same directory. */
object QuranAudioRepository {
    const val TEMPORARY_CACHE_BYTES = QuranAudioPolicy.TEMPORARY_CACHE_BYTES
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(QuranAudioStorageState())
    val state: StateFlow<QuranAudioStorageState> = mutableState.asStateFlow()
    var tracks: List<QuranAudioTrack> = emptyList()
        private set
    var sourceUrl: String = "https://www.mp3quran.net/eng/afs"
        private set
    var permissionSourceUrl: String = "https://www.mp3quran.net/eng/contact-us"
        private set
    var reciterName: String = "Mishary Rashid Alafasy"
        private set

    private var app: Context? = null
    private var database: StandaloneDatabaseProvider? = null
    private var temporary: SimpleCache? = null
    private var offline: SimpleCache? = null
    private var manager: DownloadManager? = null
    private val records = mutableMapOf<Int, Download>()
    private val preparing = mutableSetOf<Int>()
    private val failures = mutableMapOf<Int, QuranAudioStorageError>()
    private val publishLater = Runnable { publish() }
    private var ayahTimings: QuranAyahTimingCatalog? = null
    private var ayahTimingsAttempted = false

    /** Call on the main thread, lazily when entering audio or creating a media service. */
    fun init(context: Context) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (app != null) return
        val application = context.applicationContext
        val catalog = runCatching {
            application.assets.open("quran_audio/alafasy.json").bufferedReader(Charsets.UTF_8).use {
                QuranAudioCatalog.parse(it.readText())
            }
        }.getOrElse {
            mutableState.value = mutableState.value.copy(error = QuranAudioStorageError.CATALOG)
            return
        }
        try {
            tracks = catalog.tracks
            sourceUrl = catalog.source
            permissionSourceUrl = catalog.permissionSource
            reciterName = catalog.reciter
            val provider = StandaloneDatabaseProvider(application)
            database = provider
            temporary = SimpleCache(File(application.cacheDir, "quran-audio-stream-v1"),
                LeastRecentlyUsedCacheEvictor(TEMPORARY_CACHE_BYTES), provider)
            // Explicit downloads are excluded from Android backup and are never automatically evicted.
            offline = SimpleCache(File(application.noBackupFilesDir, "quran-audio-downloads-v1"),
                NoOpCacheEvictor(), provider)
            val preferences = application.getSharedPreferences("quran_audio", Context.MODE_PRIVATE)
            val wifiOnly = preferences.getBoolean("wifi_only", true)
            mutableState.value = mutableState.value.copy(wifiOnly = wifiOnly, error = null)
            tracks.forEach { track ->
                runCatching { QuranAudioStorageError.valueOf(preferences.getString("error_${track.surahId}", "")!!) }
                    .getOrNull()?.let { failures[track.surahId] = it }
                temporary!!.addListener(track.cacheKey, cacheListener)
            }
            val downloadManager = DownloadManager(application, provider, offline!!, httpFactory(),
                Executor { it.run() })
            manager = downloadManager
            app = application
            downloadManager.maxParallelDownloads = 1
            downloadManager.minRetryCount = 2
            downloadManager.requirements = requirements(wifiOnly)
            downloadManager.addListener(object : DownloadManager.Listener {
                override fun onInitialized(downloadManager: DownloadManager) {
                    runCatching {
                        downloadManager.downloadIndex.getDownloads().use { cursor ->
                            while (cursor.moveToNext()) {
                                val download = cursor.download
                                idOf(download)?.let { records[it] = download }
                            }
                        }
                    }.onFailure { setError(QuranAudioStorageError.STORAGE) }
                    publish()
                }

                override fun onDownloadChanged(downloadManager: DownloadManager, download: Download,
                    finalException: Exception?) {
                    val id = idOf(download) ?: return
                    records[id] = download
                    preparing.remove(id)
                    if (download.state == Download.STATE_FAILED) {
                        val error = classifyFailure(finalException)
                        failures[id] = error
                        preferences.edit().putString("error_$id", error.name).apply()
                    } else if (download.state == Download.STATE_COMPLETED) {
                        failures.remove(id)
                        preferences.edit().remove("error_$id").apply()
                    }
                    publish()
                }

                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                    val id = idOf(download) ?: return
                    records.remove(id)
                    failures.remove(id)
                    preparing.remove(id)
                    preferences.edit().remove("error_$id").apply()
                    publish()
                }

                override fun onRequirementsStateChanged(downloadManager: DownloadManager,
                    requirements: Requirements, notMetRequirements: Int) = publish()

                override fun onDownloadsPausedChanged(downloadManager: DownloadManager, downloadsPaused: Boolean) = publish()
            })
            publish()
        } catch (_: Exception) {
            manager?.release()
            manager = null
            temporary?.release()
            temporary = null
            offline?.release()
            offline = null
            database?.close()
            database = null
            app = null
            mutableState.value = mutableState.value.copy(ready = false, error = QuranAudioStorageError.STORAGE)
        }
    }

    fun track(surahId: Int): QuranAudioTrack? = tracks.getOrNull(surahId - 1)?.takeIf { it.surahId == surahId }

    /** The same downloaded surah bytes serve clips; no second recording or extra offline copy. */
    fun ayahTiming(surahId: Int, ayah: Int): QuranAyahTiming? {
        if (!ayahTimingsAttempted) {
            val context = app ?: return null
            ayahTimingsAttempted = true
            ayahTimings = runCatching {
                val metadata = context.assets.open("quran/manifest.json").use { it.readBytes() }
                require(QuranContent.sha256(metadata) == QuranContent.MANIFEST_SHA256)
                val surahs = JSONObject(metadata.toString(Charsets.UTF_8)).getJSONArray("surahs")
                val counts = (0 until surahs.length()).associate { i ->
                    val entry = surahs.getJSONObject(i)
                    entry.getInt("id") to entry.getInt("ayahCount")
                }
                val timings = context.assets.open("quran_audio/alafasy_ayah_timings.json").use { it.readBytes() }
                require(QuranContent.sha256(timings) == QuranAyahTimingCatalog.SHA256)
                QuranAyahTimingCatalog.parse(timings.toString(Charsets.UTF_8), counts)
            }.getOrNull()
        }
        return ayahTimings?.timing(surahId, ayah)
    }

    /** Saved downloads are read-only to the player; only the download manager writes them. */
    fun playbackDataSourceFactory(): DataSource.Factory {
        val streamFactory = CacheDataSource.Factory()
            .setCache(checkNotNull(temporary))
            .setUpstreamDataSourceFactory(httpFactory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return CacheDataSource.Factory()
            .setCache(checkNotNull(offline))
            .setCacheWriteDataSinkFactory(null)
            .setUpstreamDataSourceFactory(streamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    internal fun downloadManager(context: Context): DownloadManager {
        init(context)
        return checkNotNull(manager) { "Audio storage is not available" }
    }

    /** Adds exactly one chosen surah. Existing completed or active downloads are never duplicated. */
    fun download(surahId: Int) = onMain {
        val context = app ?: return@onMain
        val item = track(surahId) ?: return@onMain
        val existing = state.value.downloads[surahId]
        if (existing?.status == QuranAudioDownloadStatus.COMPLETED ||
            existing?.status in setOf(QuranAudioDownloadStatus.DOWNLOADING, QuranAudioDownloadStatus.QUEUED,
                QuranAudioDownloadStatus.WAITING_FOR_WIFI, QuranAudioDownloadStatus.WAITING_FOR_NETWORK,
                QuranAudioDownloadStatus.REMOVING) || !preparing.add(surahId)) return@onMain
        dismissError()
        scope.launch {
            val needed = (item.sizeBytes - (existing?.bytesDownloaded ?: 0)).coerceAtLeast(0)
            val queuedIds = (state.value.downloads.values.filter { QuranAudioPolicy.isPending(it.status) }
                .map { it.surahId }.toSet() + preparing) - surahId
            val queued = queuedIds.sumOf { id ->
                ((track(id)?.sizeBytes ?: 0) - (state.value.downloads[id]?.bytesDownloaded ?: 0)).coerceAtLeast(0)
            }
            val enough = withContext(Dispatchers.IO) {
                QuranAudioPolicy.enoughSpace(context.noBackupFilesDir.usableSpace, needed, queued)
            }
            if (!preparing.contains(surahId)) return@launch
            if (!enough) {
                preparing.remove(surahId)
                setError(QuranAudioStorageError.NOT_ENOUGH_SPACE)
                return@launch
            }
            // A completed index entry with missing/wrong-length bytes must not make a retry reuse corrupt data.
            if (existing?.status == QuranAudioDownloadStatus.FAILED && records[surahId]?.state == Download.STATE_COMPLETED) {
                val reset = withContext(Dispatchers.IO) { runCatching { offline?.removeResource(item.cacheKey) } }
                if (reset.isFailure) {
                    preparing.remove(surahId)
                    setError(QuranAudioStorageError.STORAGE)
                    return@launch
                }
                if (!preparing.contains(surahId)) return@launch
            }
            val request = DownloadRequest.Builder(item.cacheKey, android.net.Uri.parse(item.url))
                .setMimeType(MimeTypes.AUDIO_MPEG).setCustomCacheKey(item.cacheKey).build()
            try {
                DownloadService.sendAddDownload(context, QuranDownloadService::class.java, request, true)
            } catch (_: Exception) {
                preparing.remove(surahId)
                setError(QuranAudioStorageError.START_FAILED)
            }
        }
    }

    /** Cancellation also removes partial offline bytes; it never touches other surahs. */
    fun cancel(surahId: Int) = remove(surahId)

    fun remove(surahId: Int) = onMain {
        val context = app ?: return@onMain
        val item = track(surahId) ?: return@onMain
        preparing.remove(surahId)
        try {
            DownloadService.sendRemoveDownload(context, QuranDownloadService::class.java, item.cacheKey, true)
        } catch (_: Exception) { setError(QuranAudioStorageError.START_FAILED) }
    }

    fun setWifiOnly(value: Boolean) = onMain {
        val context = app ?: return@onMain
        context.getSharedPreferences("quran_audio", Context.MODE_PRIVATE).edit().putBoolean("wifi_only", value).apply()
        mutableState.value = mutableState.value.copy(wifiOnly = value)
        manager?.requirements = requirements(value)
        if (records.values.any { it.state in setOf(Download.STATE_QUEUED, Download.STATE_DOWNLOADING) }) {
            runCatching { DownloadService.sendSetRequirements(context, QuranDownloadService::class.java, requirements(value), true) }
                .onFailure { setError(QuranAudioStorageError.START_FAILED) }
        }
        publish()
    }

    /** Removes cache spans through the cache API, never releases a cache that the player is reading. */
    fun clearTemporaryCache() = onMain {
        val cache = temporary ?: return@onMain
        if (state.value.clearingCache) return@onMain
        mutableState.value = state.value.copy(clearingCache = true, error = null)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { cache.keys.toList().forEach(cache::removeResource) }
            }
            mutableState.value = state.value.copy(clearingCache = false,
                error = if (result.isFailure) QuranAudioStorageError.STORAGE else state.value.error)
            publish()
        }
    }

    fun dismissError() = onMain { mutableState.value = mutableState.value.copy(error = null) }

    private fun requirements(wifiOnly: Boolean) = Requirements(
        Requirements.NETWORK or if (wifiOnly) Requirements.NETWORK_UNMETERED else 0)

    private fun httpFactory() = DefaultHttpDataSource.Factory()
        .setUserAgent("ZikrTime-QuranAudio/1")
        .setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
        .setAllowCrossProtocolRedirects(false)
        .setContentTypePredicate { type -> type?.substringBefore(';')?.trim()?.equals("audio/mpeg", ignoreCase = true) == true }

    private fun idOf(download: Download): Int? = download.request.id.removePrefix("alafasy-v1-").toIntOrNull()
        ?.takeIf { id -> track(id)?.let { download.request.id == it.cacheKey && download.request.uri.toString() == it.url } == true }

    private fun publish() {
        val downloads = manager ?: return
        val saved = offline ?: return
        val mapped = records.mapNotNull { (id, download) ->
            val item = track(id) ?: return@mapNotNull null
            val actualLength = download.contentLength.takeIf { it > 0 }
                ?: ContentMetadata.getContentLength(saved.getContentMetadata(item.cacheKey)).takeIf { it > 0 }
                ?: 0L
            val length = actualLength.takeIf { it > 0 } ?: item.sizeBytes
            val cached = saved.getCachedBytes(item.cacheKey, 0, length)
            val completed = QuranAudioPolicy.isOfflineReady(download.state == Download.STATE_COMPLETED, actualLength, cached, item.sizeBytes)
            val status = when {
                completed -> QuranAudioDownloadStatus.COMPLETED
                download.state == Download.STATE_COMPLETED -> QuranAudioDownloadStatus.FAILED
                download.state == Download.STATE_FAILED -> QuranAudioDownloadStatus.FAILED
                download.state == Download.STATE_REMOVING || download.state == Download.STATE_RESTARTING -> QuranAudioDownloadStatus.REMOVING
                download.state == Download.STATE_STOPPED || downloads.downloadsPaused -> QuranAudioDownloadStatus.PAUSED
                download.state == Download.STATE_DOWNLOADING -> QuranAudioDownloadStatus.DOWNLOADING
                downloads.notMetRequirements and Requirements.NETWORK != 0 -> QuranAudioDownloadStatus.WAITING_FOR_NETWORK
                downloads.notMetRequirements and Requirements.NETWORK_UNMETERED != 0 -> QuranAudioDownloadStatus.WAITING_FOR_WIFI
                else -> QuranAudioDownloadStatus.QUEUED
            }
            val error = if (status == QuranAudioDownloadStatus.FAILED)
                (failures[id] ?: if (download.state == Download.STATE_COMPLETED) QuranAudioStorageError.STORAGE else QuranAudioStorageError.NETWORK) else null
            id to QuranAudioDownload(id, status, cached.coerceAtLeast(0), length, error)
        }.toMap()
        mutableState.value = mutableState.value.copy(ready = downloads.isInitialized,
            downloads = mapped, temporaryBytes = temporary?.cacheSpace ?: 0, downloadedBytes = saved.cacheSpace)
        main.removeCallbacks(publishLater)
        if (mapped.values.any { it.status == QuranAudioDownloadStatus.DOWNLOADING }) main.postDelayed(publishLater, 1000)
    }

    private val cacheListener = object : Cache.Listener {
        override fun onSpanAdded(cache: Cache, span: CacheSpan) = schedulePublish()
        override fun onSpanRemoved(cache: Cache, span: CacheSpan) = schedulePublish()
        override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) = Unit
    }

    private fun schedulePublish() {
        main.removeCallbacks(publishLater)
        main.postDelayed(publishLater, 500)
    }

    private fun setError(error: QuranAudioStorageError) { mutableState.value = mutableState.value.copy(error = error) }
    private fun onMain(action: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action) }
    private fun classifyFailure(error: Throwable?): QuranAudioStorageError {
        var cause = error
        var cacheFailure = false
        repeat(8) {
            if (cause?.message?.contains("ENOSPC", ignoreCase = true) == true ||
                cause?.message?.contains("No space left", ignoreCase = true) == true) return QuranAudioStorageError.NOT_ENOUGH_SPACE
            if (cause is Cache.CacheException) cacheFailure = true
            cause = cause?.cause
        }
        return if (cacheFailure) QuranAudioStorageError.STORAGE else QuranAudioStorageError.NETWORK
    }
}
