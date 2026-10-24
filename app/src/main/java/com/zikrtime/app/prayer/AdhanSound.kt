package com.zikrtime.app.prayer

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Записи азана (res/raw, свободные лицензии — см. «О приложении»):
 *  adhan   — «Beautiful adhan», Adam-synagda, CC0;
 *  adhan_2 — «Azan», Andrewler, CC BY-SA 4.0;
 *  adhan_3 — «The Adhan», Aaqib Azeez (запись Atcovi), CC BY-SA 4.0.
 * Ищем по имени, чтобы приложение собиралось и без файлов: тогда используется мелодия будильника телефона.
 */
object AdhanSound {

    /** Идентификатор записи (он же имя файла в res/raw) и длительность в секундах. */
    data class Voice(val id: String, val number: Int, val seconds: Int)

    val voices = listOf(
        Voice("adhan", 1, 154),
        Voice("adhan_2", 2, 183),
        Voice("adhan_3", 3, 84),
    )

    fun voice(id: String): Voice = voices.firstOrNull { it.id == id } ?: voices.first()

    @SuppressLint("DiscouragedApi")
    fun resId(context: Context, id: String = "adhan"): Int = context.resources.getIdentifier(id, "raw", context.packageName)

    fun available(context: Context, id: String = "adhan"): Boolean = resId(context, id) != 0

    fun uri(context: Context, id: String = "adhan"): Uri? =
        resId(context, id).takeIf { it != 0 }?.let { Uri.parse("android.resource://${context.packageName}/$it") }

    // ---------- Прослушивание в настройках (тихо, ~30% громкости) ----------

    /** Какая запись сейчас звучит в предпрослушивании (для кнопки ▶/■). */
    var previewing by mutableStateOf<String?>(null)
        private set
    private var player: MediaPlayer? = null

    fun togglePreview(context: Context, id: String) {
        if (previewing == id) {
            stopPreview()
            return
        }
        stopPreview()
        val res = resId(context, id).takeIf { it != 0 } ?: return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                context.resources.openRawResourceFd(res).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                setVolume(0.3f, 0.3f)
                setOnCompletionListener { stopPreview() }
                prepare()
                start()
            }
        }.getOrNull()
        previewing = if (player != null) id else null
    }

    fun stopPreview() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        previewing = null
    }
}
