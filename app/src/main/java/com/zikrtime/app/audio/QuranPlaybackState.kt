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
)

internal object QuranPlaybackMemory {
    private const val PREFS = "quran_audio_playback_v1"

    fun read(context: Context): QuranPlaybackPosition? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return QuranPlaybackPolicy.restore(prefs.getInt("surah", 0), prefs.getLong("position", 0L), prefs.getLong("duration", 0L))
    }

    fun save(context: Context, player: Player) {
        val surah = player.currentMediaItem?.mediaId?.toIntOrNull()?.takeIf { it in 1..114 } ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: read(context)?.takeIf { it.surahId == surah }?.durationMs ?: 0L
        val position = if (player.playbackState == Player.STATE_ENDED) 0L else QuranPlaybackPolicy.clamp(player.currentPosition, duration)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("surah", surah).putLong("position", position).putLong("duration", duration).apply()
    }

    fun state(context: Context, player: Player): QuranPlaybackState {
        val surah = player.currentMediaItem?.mediaId?.toIntOrNull()?.takeIf { it in 1..114 }
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: read(context)?.takeIf { it.surahId == surah }?.durationMs ?: 0L
        val failure = player.playerError
        return QuranPlaybackState(
            connected = true, surahId = surah, title = player.mediaMetadata.title?.toString().orEmpty(),
            isPlaying = player.isPlaying, isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = QuranPlaybackPolicy.clamp(player.currentPosition, duration), durationMs = duration,
            error = failure?.let { Lang.wrap(context).getString(R.string.audio_player_failure) },
            errorCode = failure?.errorCode, ended = player.playbackState == Player.STATE_ENDED,
            playWhenReady = player.playWhenReady, playbackState = player.playbackState,
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
