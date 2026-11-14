@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.zikrtime.app.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.zikrtime.app.MainActivity
import com.zikrtime.app.R
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.alarm.AlarmState
import com.zikrtime.app.prayer.AdhanSound
import java.lang.ref.WeakReference

/** One explicitly selected surah or checked verse clip, with system controls while it plays. */
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
                // Android and Media3 treat SPEECH as a request to pause for notifications.
                // Neutral media attributes let them duck and restore the recitation instead.
                setAudioAttributes(AudioAttributes.DEFAULT, true)
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
                QuranAyahSession.begin(mediaItem)
                exo.playbackParameters = PlaybackParameters(QuranAyahSession.options.speed, 1f)
                val verse = mediaItem?.mediaId?.let(QuranAyahPlaybackPolicy::reference)
                session?.setSessionActivity(openPlayerIntent(verse?.surahId ?: mediaItem?.mediaId?.toIntOrNull(), verse?.ayah))
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_ENDED) return
                val verse = exo.currentMediaItem?.mediaId?.let(QuranAyahPlaybackPolicy::reference) ?: return
                val options = QuranAyahSession.options
                val next = QuranAudioRepository.ayahTiming(verse.surahId, verse.ayah + 1)
                when (QuranAyahPlaybackPolicy.finish(options, QuranAyahSession.iteration,
                    exo.playWhenReady && !AlarmState.ringing, next != null)) {
                    QuranAyahPlaybackPolicy.Finish.REPEAT -> {
                        QuranAyahSession.repeat()
                        exo.seekTo(0L)
                    }
                    QuranAyahPlaybackPolicy.Finish.NEXT -> {
                        mediaItem(next!!.reference.mediaId, options)?.let { item ->
                            exo.setMediaItem(item)
                            exo.prepare()
                            exo.play()
                        }
                    }
                    QuranAyahPlaybackPolicy.Finish.STOP -> {
                        // Finite repetitions stop at the end; no implicit loop or jump to another surah.
                        exo.pause()
                    }
                }
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                // An exclusive interruption (e.g. a call) still requires a manual resume.
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
                    if (trusted(controller)) MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                            .add(SessionCommand(QuranAyahSession.COMMAND_CONFIGURE, Bundle.EMPTY)).build()).build()
                    else MediaSession.ConnectionResult.reject()

                override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
                    if (session.player.playbackState == Player.STATE_ENDED && playerCommand in setOf(
                            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_BACK,
                            Player.COMMAND_SEEK_FORWARD, Player.COMMAND_SEEK_TO_DEFAULT_POSITION)) {
                        // Scrubbing a completed exercise starts a new cycle on the next explicit Play.
                        // The service's own repeat seek never goes through a controller command.
                        QuranAyahSession.resetIteration()
                    }
                    if (playerCommand == Player.COMMAND_PLAY_PAUSE && !session.player.playWhenReady) {
                        if (AlarmState.ringing) return SessionError.ERROR_INVALID_STATE
                        AdhanSound.stopPreview()
                        if (session.player.playbackState == Player.STATE_ENDED) {
                            QuranAyahSession.resetIteration()
                            session.player.seekTo(0L)
                        }
                    }
                    return SessionResult.RESULT_SUCCESS
                }

                override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    val options = QuranAyahSession.fromBundle(args)
                    val requestedId = args.getString("media_id")
                    val valid = trusted(controller) && customCommand.customAction == QuranAyahSession.COMMAND_CONFIGURE &&
                        options != null && requestedId == session.player.currentMediaItem?.mediaId &&
                        requestedId?.let(QuranAyahPlaybackPolicy::reference) != null
                    if (!valid) return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    QuranAyahSession.configure(options!!)
                    exo.playbackParameters = PlaybackParameters(options.speed, 1f)
                    QuranPlaybackMemory.save(this@QuranPlaybackService, exo)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                override fun onAddMediaItems(
                    mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>,
                ): ListenableFuture<List<MediaItem>> = Futures.immediateFuture(
                    mediaItems.take(1).mapNotNull { item ->
                        val options = if (QuranAyahPlaybackPolicy.reference(item.mediaId) != null)
                            QuranAyahSession.fromBundle(item.mediaMetadata.extras) ?: QuranAyahOptions() else QuranAyahOptions()
                        mediaItem(item.mediaId, options)
                    },
                )
            }).build()
        // Restoring metadata and the stop point does not prepare a source or request audio focus.
        QuranPlaybackMemory.read(this)?.let { saved ->
            val id = saved.ayah?.let { QuranAudioAyah(saved.surahId, it).mediaId } ?: saved.surahId.toString()
            mediaItem(id, saved.ayahOptions, saved.repeatIteration)?.let { exo.setMediaItem(it, saved.positionMs) }
        }
    }

    private fun trusted(controller: MediaSession.ControllerInfo): Boolean =
        controller.uid == Process.myUid() || controller.packageName == packageName || controller.isTrusted

    private fun mediaItem(mediaId: String, options: QuranAyahOptions = QuranAyahOptions(), iteration: Int = 1): MediaItem? {
        val verse = QuranAyahPlaybackPolicy.reference(mediaId)
        val surahId = verse?.surahId ?: mediaId.toIntOrNull()?.takeIf { it.toString() == mediaId } ?: return null
        val track = QuranAudioRepository.track(surahId) ?: return null
        val timing = verse?.let { QuranAudioRepository.ayahTiming(it.surahId, it.ayah) ?: return null }
        val localized = Lang.wrap(this)
        val title = QuranAudioTitles.title(this, surahId)
        val builder = MediaItem.Builder().setMediaId(mediaId).setUri(track.url)
            .setCustomCacheKey(track.cacheKey)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(if (verse == null) title else localized.getString(R.string.audio_ayah_title, title, verse.ayah))
                .setArtist(localized.getString(R.string.audio_player_reciter))
                .setAlbumTitle(localized.getString(R.string.audio_player_album))
                .setExtras(QuranAyahSession.bundle(options, iteration))
                .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER).build())
        if (timing != null) builder.setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(timing.startMs).setEndPositionMs(timing.endMs).build())
        return builder.build()
    }

    private fun openPlayerIntent(surahId: Int?, ayah: Int? = null): PendingIntent = PendingIntent.getActivity(
        this, 8271, Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("open_quran_audio", true).putExtra("quran_audio_surah", surahId ?: 0)
            .putExtra("quran_audio_ayah", ayah ?: 0),
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
        internal val hasActiveSession: Boolean get() = active.get()?.session != null

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
