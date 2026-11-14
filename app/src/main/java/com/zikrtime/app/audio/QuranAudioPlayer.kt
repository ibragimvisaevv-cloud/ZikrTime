@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zikrtime.app.audio

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
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
    private data class Request(val surahId: Int, val ayah: Int? = null,
        val options: QuranAyahOptions = QuranAyahOptions(), val autoplay: Boolean = true) {
        val mediaId: String get() = ayah?.let { QuranAudioAyah(surahId, it).mediaId } ?: surahId.toString()
    }
    private var requested: Request? = null
    private val handler = Handler(Looper.getMainLooper())
    private val updatePosition = object : Runnable {
        override fun run() {
            update()
            schedulePosition()
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
            title = saved?.let {
                val title = QuranAudioTitles.title(context, it.surahId)
                if (it.ayah == null) title else Lang.wrap(context).getString(R.string.audio_ayah_title, title, it.ayah)
            }.orEmpty(), error = null, ayah = saved?.ayah, repeatCount = saved?.ayahOptions?.repeatCount ?: 1,
            repeatIteration = saved?.repeatIteration ?: 1, speed = saved?.ayahOptions?.speed ?: 1f,
            continueAfter = saved?.ayahOptions?.continueAfter ?: false, ended = false,
            clipStartMs = saved?.ayah?.let { QuranAudioRepository.ayahTiming(saved.surahId, it)?.startMs },
            clipEndMs = saved?.ayah?.let { QuranAudioRepository.ayahTiming(saved.surahId, it)?.endMs },
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
                requested?.also { requested = null; playRequest(it) }
            }.onFailure {
                future = null
                requested = null
                fail(R.string.audio_player_connect_failure)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        check(Looper.myLooper() == Looper.getMainLooper())
        connections = (connections - 1).coerceAtLeast(0)
        if (connections > 0) return
        generation++
        requested = null
        handler.removeCallbacks(updatePosition)
        controller?.let { update(); it.removeListener(listener); it.release() }
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
        mutableState.value = mutableState.value.copy(connected = false)
    }

    /** Every sound start originates in an explicit user request; opening a reader only connects. */
    fun play(surahId: Int) = playRequest(Request(surahId))

    fun playAyah(surahId: Int, ayah: Int, repeatCount: Int = 1, speed: Float = 1f,
        continueAfter: Boolean = false) {
        val options = QuranAyahPlaybackPolicy.options(repeatCount, speed, continueAfter) ?: return
        playRequest(Request(surahId, ayah, options))
    }

    private fun playRequest(request: Request) {
        val context = app ?: return
        if (QuranAudioRepository.track(request.surahId) == null) return
        if (request.ayah != null && QuranAudioRepository.ayahTiming(request.surahId, request.ayah) == null) {
            mutableState.value = mutableState.value.copy(error = Lang.wrap(context).getString(R.string.audio_ayah_unavailable))
            return
        }
        if (request.autoplay && AlarmState.ringing) {
            mutableState.value = mutableState.value.copy(error = Lang.wrap(context).getString(R.string.audio_player_alarm_active))
            return
        }
        if (request.autoplay) AdhanSound.stopPreview()
        val connected = controller
        if (connected == null) {
            if (connections == 0) return
            requested = request
            if (future == null) openConnection()
            return
        }
        requested = null
        if (connected.currentMediaItem?.mediaId == request.mediaId && connected.playbackState != Player.STATE_ENDED) {
            if (request.ayah != null) configureAyah(request.options.repeatCount, request.options.speed, request.options.continueAfter)
            if (request.autoplay) {
                if (connected.playbackState == Player.STATE_IDLE) connected.prepare()
                connected.play()
            } else connected.pause()
        } else {
            val saved = QuranPlaybackMemory.read(context)?.takeIf { it.surahId == request.surahId && it.ayah == request.ayah }
            val position = if (connected.currentMediaItem?.mediaId == request.mediaId) 0L else saved?.positionMs ?: 0L
            // The service discards arbitrary URLs/clips and rebuilds this checked ID from its catalogue.
            val item = MediaItem.Builder().setMediaId(request.mediaId).setMediaMetadata(MediaMetadata.Builder()
                .setExtras(QuranAyahSession.bundle(request.options)).build()).build()
            connected.pause()
            connected.setMediaItem(item, position)
            if (request.autoplay) {
                connected.prepare()
                connected.play()
            }
        }
        update()
    }

    fun togglePlayPause() {
        val current = controller
        if (current?.playWhenReady == true && current.playbackState != Player.STATE_ENDED) pause()
        else {
            val state = mutableState.value
            val surah = state.surahId ?: return
            if (state.ayah != null) playAyah(surah, state.ayah, state.repeatCount, state.speed, state.continueAfter)
            else play(surah)
        }
    }

    /** Options change the current exercise without restarting audio or acquiring focus. */
    fun configureAyah(repeatCount: Int, speed: Float, continueAfter: Boolean) {
        val options = QuranAyahPlaybackPolicy.options(repeatCount, speed, continueAfter) ?: return
        val context = app ?: return
        val current = controller ?: return
        val id = current.currentMediaItem?.mediaId ?: return
        if (QuranAyahPlaybackPolicy.reference(id) == null) return
        val args = QuranAyahSession.bundle(options).apply { putString("media_id", id) }
        val result = current.sendCustomCommand(SessionCommand(QuranAyahSession.COMMAND_CONFIGURE, Bundle.EMPTY), args)
        result.addListener({
            if (controller === current && current.currentMediaItem?.mediaId == id &&
                runCatching { result.get().resultCode == SessionResult.RESULT_SUCCESS }.getOrDefault(false)) update()
        }, ContextCompat.getMainExecutor(context))
    }

    fun setSpeed(speed: Float) {
        if (speed !in QuranAyahPlaybackPolicy.speeds) return
        val current = mutableState.value
        if (current.ayah != null) configureAyah(current.repeatCount, speed, current.continueAfter)
        else controller?.setPlaybackSpeed(speed)
        update()
    }

    fun nextAyah() = moveAyah(1)
    fun previousAyah() = moveAyah(-1)

    private fun moveAyah(offset: Int) {
        val state = snapshot()
        val surah = state.surahId ?: return
        val ayah = state.ayah ?: return
        if (QuranAudioRepository.ayahTiming(surah, ayah + offset) == null) return
        playRequest(Request(surah, ayah + offset, QuranAyahOptions(state.repeatCount, state.speed, state.continueAfter),
            autoplay = state.playWhenReady && !state.ended))
    }

    fun pause() { requested = null; controller?.pause(); update() }

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
        requested?.let { playRequest(it); return }
        val state = mutableState.value
        val surah = state.surahId ?: return
        if (state.ayah != null) playAyah(surah, state.ayah, state.repeatCount, state.speed, state.continueAfter)
        else play(surah)
    }

    fun stop() {
        requested = null
        controller?.apply {
            app?.let { QuranPlaybackMemory.save(it, this) }
            pause()
            stop()
            clearMediaItems()
        }
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
        val state = mutableState.value
        if (connections == 0 || !state.isPlaying) return
        // Keep the normal once-per-second timer, but wake exactly at verified verse boundaries.
        val delayMs = state.nextAyahBoundaryMs?.let { boundary ->
            kotlin.math.ceil((boundary - state.positionMs) / state.speed.toDouble()).toLong().coerceIn(1L, 1_000L)
        } ?: 1_000L
        handler.postDelayed(updatePosition, delayMs)
    }

    private fun fail(message: Int) {
        val context = app ?: return
        mutableState.value = mutableState.value.copy(connected = false, isPlaying = false, isBuffering = false,
            playWhenReady = false, error = Lang.wrap(context).getString(message))
    }
}
