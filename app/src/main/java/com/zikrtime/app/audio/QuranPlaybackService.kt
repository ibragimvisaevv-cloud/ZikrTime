@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zikrtime.app.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.zikrtime.app.MainActivity
import com.zikrtime.app.R
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.alarm.AlarmState
import com.zikrtime.app.prayer.AdhanSound
import java.lang.ref.WeakReference

/** One explicitly selected surah, with a system media notification while it plays. */
class QuranPlaybackService : MediaSessionService() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val rememberProgress = object : Runnable {
        override fun run() {
            val current = player ?: return
            if (current.isPlaying) {
                QuranPlaybackMemory.save(this@QuranPlaybackService, current)
                handler.postDelayed(this, 10_000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val sources = runCatching {
            QuranAudioRepository.init(this)
            QuranAudioRepository.playbackDataSourceFactory()
        }.getOrElse { stopSelf(); return }
        val exo = runCatching {
            ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(sources))
                .setSeekBackIncrementMs(15_000L).setSeekForwardIncrementMs(15_000L).build()
        }.getOrElse { stopSelf(); return }
        if (runCatching {
            exo.apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_LOCAL)
                repeatMode = Player.REPEAT_MODE_OFF
            }
        }.isFailure) { exo.release(); stopSelf(); return }
        player = exo
        active = WeakReference(this)
        exo.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                QuranPlaybackMemory.save(this@QuranPlaybackService, player)
                handler.removeCallbacks(rememberProgress)
                if (player.isPlaying) handler.postDelayed(rememberProgress, 10_000L)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                session?.setSessionActivity(openPlayerIntent(mediaItem?.mediaId?.toIntOrNull()))
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                // Speech pauses rather than ducks. A later focus gain must not unexpectedly resume it.
                if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
                    exo.pause()
                    QuranPlaybackMemory.save(this@QuranPlaybackService, exo)
                }
            }
        })
        session = MediaSession.Builder(this, exo)
            .setSessionActivity(openPlayerIntent(null))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                    if (trusted(controller)) MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
                    else MediaSession.ConnectionResult.reject()

                override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
                    if (playerCommand == Player.COMMAND_PLAY_PAUSE && !session.player.playWhenReady) {
                        if (AlarmState.ringing) return SessionResult.RESULT_ERROR_INVALID_STATE
                        AdhanSound.stopPreview()
                    }
                    return SessionResult.RESULT_SUCCESS
                }

                override fun onAddMediaItems(
                    mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>,
                ): ListenableFuture<List<MediaItem>> = Futures.immediateFuture(
                    mediaItems.take(1).mapNotNull { it.mediaId.toIntOrNull()?.let(::mediaItem) },
                )
            }).build()
        // Restoring metadata and the stop point does not prepare a source or request audio focus.
        QuranPlaybackMemory.read(this)?.let { saved ->
            mediaItem(saved.surahId)?.let { exo.setMediaItem(it, saved.positionMs) }
        }
    }

    private fun trusted(controller: MediaSession.ControllerInfo): Boolean =
        controller.uid == Process.myUid() || controller.packageName == packageName || controller.isTrusted

    private fun mediaItem(surahId: Int): MediaItem? {
        val track = QuranAudioRepository.track(surahId) ?: return null
        val localized = Lang.wrap(this)
        return MediaItem.Builder().setMediaId(surahId.toString()).setUri(track.url)
            .setCustomCacheKey(track.cacheKey)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(QuranAudioTitles.title(this, surahId))
                .setArtist(localized.getString(R.string.audio_player_reciter))
                .setAlbumTitle(localized.getString(R.string.audio_player_album))
                .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER).build()).build()
    }

    private fun openPlayerIntent(surahId: Int?): PendingIntent = PendingIntent.getActivity(
        this, 8271, Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("open_quran_audio", true).putExtra("quran_audio_surah", surahId ?: 0),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (trusted(controllerInfo)) session else null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping away a paused player ends its service. An ongoing recitation keeps its controls.
        if (player?.playWhenReady != true || player?.playbackState == Player.STATE_ENDED || player?.mediaItemCount == 0) {
            player?.let { QuranPlaybackMemory.save(this, it); it.stop(); it.clearMediaItems() }
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.let { QuranPlaybackMemory.save(this, it) }
        session?.release()
        session = null
        player?.release()
        player = null
        if (active.get() === this) active.clear()
        super.onDestroy()
    }

    companion object {
        private var active = WeakReference<QuranPlaybackService>(null)

        /** Does not create or start a service; called before our alarm/adhan starts. */
        @Suppress("UNUSED_PARAMETER")
        fun pauseForInterruption(context: Context) {
            val service = active.get() ?: return
            val pause = Runnable {
                service.player?.let { it.pause(); QuranPlaybackMemory.save(service, it) }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) pause.run() else service.handler.post(pause)
        }

        /** Main-thread diagnostic used by debug QA; never creates playback or changes state. */
        fun snapshot(): QuranPlaybackState? {
            val service = active.get() ?: return null
            return service.player?.let { QuranPlaybackMemory.state(service, it) }
        }
    }
}
