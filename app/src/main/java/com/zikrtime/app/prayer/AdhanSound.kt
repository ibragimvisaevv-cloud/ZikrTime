package com.zikrtime.app.prayer

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zikrtime.app.R

/** Credits and transformation details accompany each recording in the sound picker. */
object AdhanSound {

    /** Идентификатор записи (он же имя файла в res/raw) и длительность в секундах. */
    data class Voice(
        val id: String,
        val number: Int,
        val seconds: Int,
        val titleRes: Int,
        val credit: String,
        val sourceUrl: String,
        val licenseUrl: String,
        val fajrOnly: Boolean = false,
    )

    private const val SOURCE = "https://github.com/nandxorandor/Athan/blob/ad2bf1799add51cca4aaf1f123b1c212378c7786/"
    private const val LICENSE = "https://creativecommons.org/licenses/by/4.0/"
    private const val AUTHOR = "Ahmed Khalaf · CC BY 4.0"
    val voices = listOf(
        Voice("athan_khalaf_one", 1, 143, R.string.adhan_khalaf_one, AUTHOR,
            SOURCE + "app/src/main/assets/athan/developer/Developer_Athan-1.mp3", LICENSE),
        Voice("athan_khalaf_two", 2, 131, R.string.adhan_khalaf_two, AUTHOR,
            SOURCE + "app/src/main/assets/athan/developer/Developer_Athan-2.mp3", LICENSE),
        Voice("athan_khalaf_fajr", 3, 99, R.string.adhan_khalaf_fajr, AUTHOR,
            SOURCE + "app/src/main/assets/athan/fajr/Developer_Athan-3_Fajr.mp3", LICENSE, fajrOnly = true),
    )

    fun voice(id: String): Voice = when (id) {
        "adhan_2", "adhan_makkah" -> voices[1]
        "adhan_fajr" -> voices[2]
        else -> voices.firstOrNull { it.id == id } ?: voices.first()
    }

    @SuppressLint("DiscouragedApi")
    fun resId(context: Context, id: String = voices.first().id): Int =
        context.resources.getIdentifier(voice(id).id, "raw", context.packageName)

    fun available(context: Context, id: String = voices.first().id): Boolean = resId(context, id) != 0

    fun uri(context: Context, id: String): Uri? = if (available(context, id))
        Uri.parse("android.resource://${context.packageName}/raw/${voice(id).id}") else null

    fun eligibleVoices(forFajrAlarm: Boolean): List<Voice> = voices.filter { forFajrAlarm || !it.fajrOnly }

    fun notificationVoice(id: String): Voice = voice(id).takeUnless { it.fajrOnly } ?: voices.first()

    // Preview follows the device's media volume; it never changes the system volume.

    /** Какая запись сейчас звучит в предпрослушивании (для кнопки ▶/■). */
    var previewing by mutableStateOf<String?>(null)
        private set
    private var player: MediaPlayer? = null
    var previewFailed by mutableStateOf(false)
        private set

    fun togglePreview(context: Context, id: String) {
        if (previewing == id) {
            stopPreview()
            return
        }
        stopPreview()
        previewFailed = false
        com.zikrtime.app.audio.QuranPlaybackService.pauseForInterruption(context)
        val res = resId(context, id).takeIf { it != 0 }
        if (res == null) {
            previewFailed = true
            return
        }
        val candidate = runCatching { MediaPlayer() }.getOrElse {
            previewFailed = true
            return
        }
        player = candidate
        previewing = id
        runCatching {
            candidate.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                context.resources.openRawResourceFd(res).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                setVolume(1f, 1f)
                setOnPreparedListener { prepared ->
                    if (player === prepared) {
                        runCatching { prepared.start() }.onFailure {
                            stopPreview()
                            previewFailed = true
                        }
                    }
                }
                setOnCompletionListener { completed -> if (player === completed) stopPreview() }
                setOnErrorListener { failed, _, _ ->
                    if (player === failed) {
                        stopPreview()
                        previewFailed = true
                    }
                    true
                }
                prepareAsync()
            }
        }.onFailure {
            stopPreview()
            previewFailed = true
        }
    }

    fun stopPreview() {
        val previous = player
        player = null
        previewing = null
        previous?.runCatching {
            setOnPreparedListener(null)
            setOnCompletionListener(null)
            setOnErrorListener(null)
            release()
        }
    }
}
