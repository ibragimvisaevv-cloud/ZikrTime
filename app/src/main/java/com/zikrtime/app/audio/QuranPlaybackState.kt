package com.zikrtime.app.audio

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import com.zikrtime.app.R
import com.zikrtime.app.data.QuranContent
import com.zikrtime.app.i18n.Lang
import org.json.JSONObject

data class QuranPlaybackState(
    val connected: Boolean = false,
    val surahId: Int? = null,
    val title: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val errorCode: Int? = null,
    val ended: Boolean = false,
    val playWhenReady: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val ayah: Int? = null,
    val repeatCount: Int = 1,
    val repeatIteration: Int = 1,
    val speed: Float = 1f,
    val continueAfter: Boolean = false,
    val clipStartMs: Long? = null,
    val clipEndMs: Long? = null,
)

internal object QuranPlaybackMemory {
    private const val PREFS = "quran_audio_playback_v1"

    fun read(context: Context): QuranPlaybackPosition? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = QuranPlaybackPolicy.restore(prefs.getInt("surah", 0), prefs.getLong("position", 0L), prefs.getLong("duration", 0L)) ?: return null
        val ayah = prefs.getInt("ayah", 0).takeIf { it > 0 }
        if (ayah != null && QuranAudioRepository.ayahTiming(saved.surahId, ayah) == null) return null
        val options = QuranAyahPlaybackPolicy.options(prefs.getInt("repeats", 1), prefs.getFloat("speed", 1f),
            prefs.getBoolean("continue", false)) ?: QuranAyahOptions()
        val iteration = prefs.getInt("iteration", 1).coerceIn(1, options.repeatCount.takeIf { it > 0 } ?: Int.MAX_VALUE)
        return saved.copy(ayah = ayah, ayahOptions = options, repeatIteration = iteration)
    }

    fun save(context: Context, player: Player) {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val verse = QuranAyahPlaybackPolicy.reference(mediaId)
        val surah = verse?.surahId ?: mediaId.toIntOrNull()?.takeIf { it in 1..114 } ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: verse?.let { QuranAudioRepository.ayahTiming(it.surahId, it.ayah)?.durationMs }
            ?: read(context)?.takeIf { it.surahId == surah && it.ayah == verse?.ayah }?.durationMs ?: 0L
        val position = if (player.playbackState == Player.STATE_ENDED) 0L else QuranPlaybackPolicy.clamp(player.currentPosition, duration)
        val options = if (verse != null && QuranAyahSession.reference == verse) QuranAyahSession.options else QuranAyahOptions()
        val iteration = if (verse != null && QuranAyahSession.reference == verse && player.playbackState != Player.STATE_ENDED)
            QuranAyahSession.iteration else 1
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("surah", surah).putInt("ayah", verse?.ayah ?: 0)
            .putLong("position", position).putLong("duration", duration)
            .putInt("repeats", options.repeatCount).putFloat("speed", options.speed)
            .putBoolean("continue", options.continueAfter).putInt("iteration", iteration).apply()
    }

    fun state(context: Context, player: Player): QuranPlaybackState {
        val verse = player.currentMediaItem?.mediaId?.let(QuranAyahPlaybackPolicy::reference)
        val surah = verse?.surahId ?: player.currentMediaItem?.mediaId?.toIntOrNull()?.takeIf { it in 1..114 }
        val timing = verse?.let { QuranAudioRepository.ayahTiming(it.surahId, it.ayah) }
        val options = if (verse != null && QuranAyahSession.reference == verse) QuranAyahSession.options else QuranAyahOptions()
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: timing?.durationMs
            ?: read(context)?.takeIf { it.surahId == surah && it.ayah == verse?.ayah }?.durationMs ?: 0L
        val failure = player.playerError
        return QuranPlaybackState(
            connected = true, surahId = surah, title = player.mediaMetadata.title?.toString().orEmpty(),
            isPlaying = player.isPlaying, isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = QuranPlaybackPolicy.clamp(player.currentPosition, duration), durationMs = duration,
            error = failure?.let { Lang.wrap(context).getString(R.string.audio_player_failure) },
            errorCode = failure?.errorCode, ended = player.playbackState == Player.STATE_ENDED,
            playWhenReady = player.playWhenReady, playbackState = player.playbackState,
            ayah = verse?.ayah, repeatCount = options.repeatCount,
            repeatIteration = if (verse != null && QuranAyahSession.reference == verse) QuranAyahSession.iteration else 1,
            speed = player.playbackParameters.speed, continueAfter = options.continueAfter,
            clipStartMs = timing?.startMs, clipEndMs = timing?.endMs,
        )
    }
}

/** Reads only the existing checked metadata; does not parse or alter any Quran verses. */
internal object QuranAudioTitles {
    private var names: Map<Int, Pair<String, String>>? = null

    fun title(context: Context, surah: Int): String = runCatching {
        val titles = names ?: run {
            val bytes = context.assets.open("quran/manifest.json").use { it.readBytes() }
            require(QuranContent.sha256(bytes) == QuranContent.MANIFEST_SHA256)
            val metadata = JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("surahs")
            (0 until metadata.length()).associate { i ->
                val item = metadata.getJSONObject(i)
                item.getInt("id") to (item.getString("nameRu") to item.getString("nameLatin"))
            }.also { names = it }
        }
        val pair = titles[surah]
        if (pair == null) surah.toString() else if (Lang.content(context) == "ru") pair.first else pair.second
    }.getOrDefault(surah.toString())
}
