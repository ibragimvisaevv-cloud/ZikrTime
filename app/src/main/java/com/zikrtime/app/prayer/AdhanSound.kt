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

/**
 * Записи азана (res/raw, свободные лицензии — см. «О приложении»):
 *  adhan   — «Beautiful adhan», Adam-synagda, CC0;
 *  adhan_2 — «Azan», Andrewler, CC BY-SA 4.0;
 *  adhan_3 — «The Adhan», Aaqib Azeez (запись Atcovi), CC BY-SA 4.0.
 * Ищем по имени, чтобы приложение собиралось и без файлов: тогда используется мелодия будильника телефона.
 */
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

    val voices = listOf(
        Voice("adhan", 1, 154, R.string.adhan_name_original,
            "Adam-synagda · CC0 1.0", "https://commons.wikimedia.org/wiki/File:Beautiful_adhan.ogg",
            "https://creativecommons.org/publicdomain/zero/1.0/"),
        Voice("adhan_2", 2, 183, R.string.adhan_name_andrewler,
            "Andrewler · CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:Azan.ogg",
            "https://creativecommons.org/licenses/by-sa/4.0/"),
        Voice("adhan_3", 3, 84, R.string.adhan_name_aaqib,
            "Aaqib Azeez / Atcovi · CC BY-SA 4.0", "https://commons.wikimedia.org/wiki/File:The_Adhan_-_Muslim_Call_to_Prayer_-_Aaqib_Azeez.mp3",
            "https://creativecommons.org/licenses/by-sa/4.0/"),
        Voice("adhan_madinah", 4, 187, R.string.adhan_name_madinah,
            "ejaz215 · CC BY 3.0", "https://commons.wikimedia.org/wiki/File:33937_ejaz215_call-to-prayer-from-the-prophet-s-mo.ogg",
            "https://creativecommons.org/licenses/by/3.0/"),
        Voice("adhan_makkah", 5, 197, R.string.adhan_name_makkah,
            "Seyfula Islam · CC BY 3.0", "https://commons.wikimedia.org/wiki/File:Adhan,_Great_Mosque_of_Mecca_-_Jan_21,_2013.webm",
            "https://creativecommons.org/licenses/by/3.0/"),
        Voice("adhan_fajr", 6, 248, R.string.adhan_name_fajr,
            "Besim Azemi / Islamic Center Malmö · CC BY 3.0", "https://commons.wikimedia.org/wiki/File:Eid_al-Fitr_Fajr_azan_at_Malm%C3%B6_Mosque_-_19_August_2012.webm",
            "https://creativecommons.org/licenses/by/3.0/", fajrOnly = true),
    )

    fun voice(id: String): Voice = voices.firstOrNull { it.id == id } ?: voices.first()

    @SuppressLint("DiscouragedApi")
    fun resId(context: Context, id: String = "adhan"): Int = context.resources.getIdentifier(id, "raw", context.packageName)

    fun available(context: Context, id: String = "adhan"): Boolean = resId(context, id) != 0

    fun uri(context: Context, id: String = "adhan"): Uri? =
        resId(context, id).takeIf { it != 0 }?.let { Uri.parse("android.resource://${context.packageName}/$it") }

    /** Fajr includes the extra morning call and is offered only for the Fajr alarm. */
    fun eligibleVoices(forFajrAlarm: Boolean): List<Voice> = voices.filter { forFajrAlarm || !it.fajrOnly }

    /** Preferences restored from backups must not route a Fajr-only recording to every prayer. */
    fun notificationVoice(id: String): Voice = eligibleVoices(forFajrAlarm = false)
        .firstOrNull { it.id == id } ?: voices.first()

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
