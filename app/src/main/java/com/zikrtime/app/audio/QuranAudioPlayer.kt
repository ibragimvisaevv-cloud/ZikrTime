@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zikrtime.app.audio

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.zikrtime.app.R
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.alarm.AlarmState
import com.zikrtime.app.prayer.AdhanSound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI connection only. The service owns sound and can continue with the screen off. */
object QuranAudioPlayer {
    private val mutableState = MutableStateFlow(QuranPlaybackState())
    val state: StateFlow<QuranPlaybackState> = mutableState.asStateFlow()
    private var app: Context? = null
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var connections = 0
    private var generation = 0
    private var requestedSurah: Int? = null
    private val handler = Handler(Looper.getMainLooper())
    private val updatePosition = object : Runnable {
        override fun run() {
            update()
            if (connections > 0 && controller?.isPlaying == true) handler.postDelayed(this, 1_000L)
        }
    }
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            update()
            schedulePosition()
        }
    }

    /** Pair with disconnect in the screen's ON_STOP, not just composition disposal. */
    fun connect(context: Context) {
        check(Looper.myLooper() == Looper.getMainLooper())
        connections++
        app = context.applicationContext
        if (controller != null || future != null) { update(); return }
        openConnection()
    }

    private fun openConnection() {
        val context = app ?: return
        if (runCatching {
                QuranAudioRepository.init(context)
                // The download index loads asynchronously; validate cache availability directly.
                QuranAudioRepository.playbackDataSourceFactory()
            }.isFailure) {
            fail(R.string.audio_player_catalog_failure)
            return
        }
        val saved = QuranPlaybackMemory.read(context)
        mutableState.value = mutableState.value.copy(
            connected = false, surahId = saved?.surahId, positionMs = saved?.positionMs ?: 0L,
            durationMs = saved?.durationMs ?: 0L, isPlaying = false, playWhenReady = false,
            title = saved?.let { QuranAudioTitles.title(context, it.surahId) }.orEmpty(), error = null,
        )
        val token = SessionToken(context, ComponentName(context, QuranPlaybackService::class.java))
        val currentGeneration = ++generation
        val connecting = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(disconnected: MediaController) {
                    if (controller === disconnected) {
                        controller = null
                        future = null
                        handler.removeCallbacks(updatePosition)
                        mutableState.value = mutableState.value.copy(connected = false, isPlaying = false,
                            isBuffering = false, playWhenReady = false)
                    }
                }
            }).buildAsync()
        future = connecting
        connecting.addListener({
            if (currentGeneration != generation || connections == 0) return@addListener
            runCatching { connecting.get() }.onSuccess { connected ->
                future = null
                controller = connected
                connected.addListener(listener)
                update()
                schedulePosition()
                requestedSurah?.also { requestedSurah = null; play(it) }
            }.onFailure {
                future = null
                requestedSurah = null
                fail(R.string.audio_player_connect_failure)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        check(Looper.myLooper() == Looper.getMainLooper())
        connections = (connections - 1).coerceAtLeast(0)
        if (connections > 0) return
        generation++
        requestedSurah = null
        handler.removeCallbacks(updatePosition)
        controller?.let { update(); it.removeListener(listener); it.release() }
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
        mutableState.value = mutableState.value.copy(connected = false)
    }

    /** The only automatic connection follow-up allowed is this explicit user play request. */
    fun play(surahId: Int) {
        val context = app ?: return
        if (QuranAudioRepository.track(surahId) == null) return
        if (AlarmState.ringing) {
            mutableState.value = mutableState.value.copy(error = Lang.wrap(context).getString(R.string.audio_player_alarm_active))
            return
        }
        AdhanSound.stopPreview()
        val connected = controller
        if (connected == null) {
            if (connections == 0) return
            requestedSurah = surahId
            if (future == null) openConnection()
            return
        }
        if (connected.currentMediaItem?.mediaId == surahId.toString() && connected.playbackState != Player.STATE_ENDED) {
            if (connected.playbackState == Player.STATE_IDLE) connected.prepare()
            connected.play()
        } else {
            val saved = QuranPlaybackMemory.read(context)?.takeIf { it.surahId == surahId }
            val position = if (connected.currentMediaItem?.mediaId == surahId.toString()) 0L else saved?.positionMs ?: 0L
            // The service replaces this ID with the trusted source URL and verified metadata.
            connected.setMediaItem(MediaItem.Builder().setMediaId(surahId.toString()).build(), position)
            connected.prepare()
            connected.play()
        }
        update()
    }

    fun togglePlayPause() {
        val current = controller
        if (current?.playWhenReady == true) pause()
        else (current?.currentMediaItem?.mediaId?.toIntOrNull() ?: mutableState.value.surahId)?.let(::play)
    }

    fun pause() { requestedSurah = null; controller?.pause(); update() }

    fun seekTo(positionMs: Long) {
        val current = controller ?: return
        current.seekTo(QuranPlaybackPolicy.clamp(positionMs, mutableState.value.durationMs))
        update()
    }

    fun skipBy(deltaMs: Long) {
        val current = controller ?: return
        seekTo(QuranPlaybackPolicy.offset(current.currentPosition, deltaMs, mutableState.value.durationMs))
    }

    fun retry() {
        val surah = mutableState.value.surahId ?: requestedSurah ?: return
        play(surah)
    }

    fun stop() {
        requestedSurah = null
        controller?.apply { pause(); stop(); clearMediaItems() }
        update()
    }

    fun snapshot(): QuranPlaybackState {
        update()
        return mutableState.value
    }

    private fun update() {
        val context = app ?: return
        val current = controller ?: return
        mutableState.value = QuranPlaybackMemory.state(context, current)
    }

    private fun schedulePosition() {
        handler.removeCallbacks(updatePosition)
        if (connections > 0 && controller?.isPlaying == true) handler.postDelayed(updatePosition, 1_000L)
    }

    private fun fail(message: Int) {
        val context = app ?: return
        mutableState.value = mutableState.value.copy(connected = false, isPlaying = false, isBuffering = false,
            playWhenReady = false, error = Lang.wrap(context).getString(message))
    }
}
