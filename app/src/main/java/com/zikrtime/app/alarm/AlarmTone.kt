package com.zikrtime.app.alarm

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import com.zikrtime.app.data.Store
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A specific ringtone selected on this phone, or the current Android alarm default. */
object AlarmTone {
    private const val KEY = "fajr_selected_tone_v1"
    private const val MAX_BYTES = 64L * 1024 * 1024
    data class Choice(val file: String, val title: String, val originalUri: String)

    fun selected(): Choice? = Store.getRaw(KEY)?.let { raw ->
        runCatching {
            val obj = JSONObject(raw)
            Choice(obj.getString("file"), obj.getString("title"), obj.getString("uri"))
                .takeIf { it.file.matches(Regex("[a-f0-9-]{36}\\.tone")) }
        }.getOrNull()
    }

    private fun directory(context: Context) = File(context.filesDir, "selected-alarm-tones")

    /** Read the picker grant while it is valid; some OEM pickers grant access for this launch only. */
    fun copySelection(context: Context, uri: Uri): Choice {
        require(uri.scheme == "content")
        val resolver = context.contentResolver
        val title = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf(String::isNotBlank) ?: runCatching {
            ringtoneTitle(context, uri)
        }.getOrNull().orEmpty()
        val dir = directory(context)
        check(dir.isDirectory || dir.mkdirs())
        val target = File(dir, UUID.randomUUID().toString() + ".tone")
        try {
            val input = requireNotNull(resolver.openInputStream(uri))
            input.use { stream -> target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= MAX_BYTES)
                    output.write(buffer, 0, n)
                }
                require(total > 0)
                output.fd.sync()
            } }
            // Reject images, empty files and documents that cannot be used as alarm audio.
            val decoder = android.media.MediaPlayer()
            try {
                decoder.setDataSource(target.absolutePath)
                decoder.prepare()
                require(decoder.trackInfo.any { it.trackType == android.media.MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO })
            } finally { decoder.release() }
            return Choice(target.name, title, uri.toString())
        } catch (failure: Exception) {
            target.delete()
            throw failure
        }
    }

    private fun ringtoneTitle(context: Context, uri: Uri): String? =
        RingtoneManager.getRingtone(context, uri)?.let { tone ->
            try { tone.getTitle(context) } finally { tone.stop() }
        }

    fun defaultTitle(context: Context): String? = runCatching {
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)?.let { ringtoneTitle(context, it) }
    }.getOrNull()

    fun discard(context: Context, choice: Choice) {
        if (selected()?.file != choice.file) File(directory(context), choice.file).delete()
    }

    fun select(context: Context, choice: Choice?) {
        val previous = selected()?.file
        val raw = choice?.let { JSONObject().put("file", it.file).put("title", it.title).put("uri", it.originalUri).toString() }
        Store.putRaw(KEY, raw.orEmpty())
        Store.alarmSound = "system"
        // Keep the previous file through the preferences write and allow concurrent imports to finish.
        val yesterday = System.currentTimeMillis() - 86_400_000L
        directory(context).listFiles()?.filter {
            it.name != previous && it.name != choice?.file && it.name.endsWith(".tone") && it.lastModified() < yesterday
        }
            ?.forEach { it.delete() }
    }

    /** Try each URI on open/decoder error. Uses only alarm tones for the system fallback. */
    fun candidates(context: Context): List<Uri> = buildList {
        selected()?.let { selection ->
            val local = File(directory(context), selection.file)
            if (local.isFile) add(Uri.fromFile(local))
        }
        runCatching { RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM) }
            .getOrNull()?.let(::add)
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let(::add)
        runCatching {
            val manager = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
            manager.cursor?.use { cursor -> if (cursor.moveToFirst()) manager.getRingtoneUri(0) else null }
        }.getOrNull()?.let(::add)
    }.distinct()
}
