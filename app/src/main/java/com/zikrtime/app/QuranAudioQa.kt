package com.zikrtime.app

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import com.zikrtime.app.audio.QuranAudioPlayer
import com.zikrtime.app.audio.QuranAudioRepository
import com.zikrtime.app.audio.QuranPlaybackService
import org.json.JSONObject
import java.io.File

/** Read-only diagnostics on an isolated debug install; never starts media or a download. */
internal object QuranAudioQa {
    fun captureIfRequested(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG || !intent.getBooleanExtra("qa_audio_probe", false)) return
        val player = QuranPlaybackService.snapshot() ?: QuranAudioPlayer.snapshot()
        val storage = QuranAudioRepository.state.value
        val downloads = JSONObject()
        storage.downloads.forEach { (id, download) ->
            downloads.put(id.toString(), JSONObject().apply {
                put("surahId", download.surahId)
                put("status", download.status.name)
                put("bytesDownloaded", download.bytesDownloaded)
                put("totalBytes", download.totalBytes)
                put("error", download.error ?: JSONObject.NULL)
            })
        }
        val json = JSONObject().apply {
            put("probeId", intent.getStringExtra("qa_audio_probe_id").orEmpty())
            put("capturedAt", System.currentTimeMillis())
            put("player", JSONObject().apply {
                put("connected", player.connected)
                put("surahId", player.surahId ?: JSONObject.NULL)
                put("isPlaying", player.isPlaying)
                put("isBuffering", player.isBuffering)
                put("playWhenReady", player.playWhenReady)
                put("playbackState", player.playbackState)
                put("positionMs", player.positionMs)
                put("durationMs", player.durationMs)
                put("ended", player.ended)
                put("error", player.error ?: JSONObject.NULL)
            })
            put("storage", JSONObject().apply {
                put("ready", storage.ready)
                put("downloads", downloads)
                put("temporaryBytes", storage.temporaryBytes)
                put("downloadedBytes", storage.downloadedBytes)
                put("wifiOnly", storage.wifiOnly)
                put("clearingCache", storage.clearingCache)
                put("error", storage.error ?: JSONObject.NULL)
            })
        }
        val target = AtomicFile(File(context.filesDir, "qa-audio-state.json"))
        var output: java.io.FileOutputStream? = null
        try {
            output = target.startWrite()
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            target.finishWrite(output)
        } catch (_: Exception) {
            output?.let { target.failWrite(it) }
        }
    }
}
