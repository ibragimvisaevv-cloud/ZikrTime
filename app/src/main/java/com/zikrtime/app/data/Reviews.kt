package com.zikrtime.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Отзывы о компаниях магазина и местах для намаза.
 * Одобренные лежат в reviews.json; новый отзыв приложение кладёт в закрытое хранилище по подписанной форме,
 * владелец проверяет его в Telegram-боте.
 */
object Reviews {
    private const val REMOTE = Shop.CONTENT_BASE + "reviews.json"
    private const val KEY = "reviews_json"
    private const val KEY_TAG = "reviews_etag"
    private const val KEY_AT = "reviews_at"
    private const val KEY_SENT = "reviews_sent"
    private const val KEY_DEVICE = "reviews_device"
    private const val REFRESH_MS = 60 * 1000L

    class Item(val rating: Int, val text: String, val name: String, val date: Long)
    class Summary(val avg: Double, val count: Int, val items: List<Item>)
    class Upload(val url: String, val prefix: String, val max: Int, val expires: Long, val fields: Map<String, String>)
    class Data(val reviews: Map<String, Summary>, val upload: Upload?)

    var data by mutableStateOf<Data?>(null)
        private set

    fun load(): Data {
        data?.let { return it }
        val stored = Store.getRaw(KEY)?.let { runCatching { parse(it) }.getOrNull() } ?: Data(emptyMap(), null)
        data = stored
        return stored
    }

    fun summary(target: String): Summary? = (data ?: load()).reviews[target]

    suspend fun refresh(force: Boolean = false) {
        load()
        val last = Store.getRaw(KEY_AT)?.toLongOrNull() ?: 0L
        if (!force && System.currentTimeMillis() - last < REFRESH_MS) return
        val hasCopy = Store.getRaw(KEY) != null
        val etag = if (hasCopy) Store.getRaw(KEY_TAG) else null
        val result = withContext(Dispatchers.IO) { runCatching { Net.getIfChanged(REMOTE, etag) } }
        if (result.isFailure) return
        if (result.getOrNull() == null) {
            Store.putRaw(KEY_AT, System.currentTimeMillis().toString())
            return
        }
        val text = result.getOrNull()?.text ?: return
        val parsed = runCatching { parse(text) }.getOrNull() ?: return
        Store.putRaw(KEY, text)
        Store.putRaw(KEY_TAG, result.getOrNull()?.etag.orEmpty())
        Store.putRaw(KEY_AT, System.currentTimeMillis().toString())
        data = parsed
    }

    fun parse(json: String): Data {
        val root = JSONObject(json)
        val map = HashMap<String, Summary>()
        root.getJSONObject("reviews").let { r ->
            r.keys().forEach { target ->
                val o = r.getJSONObject(target)
                val arr = o.optJSONArray("items")
                val items = (0 until (arr?.length() ?: 0)).map { i ->
                    val it = arr!!.getJSONObject(i)
                    Item(it.optInt("r"), it.optString("t"), it.optString("n"), it.optLong("d"))
                }
                map[target] = Summary(o.optDouble("avg"), o.optInt("n"), items)
            }
        }
        val upload = root.optJSONObject("upload")?.let { u ->
            val f = u.optJSONObject("fields") ?: return@let null
            Upload(
                url = u.optString("url"), prefix = u.optString("prefix"), max = u.optInt("max", 4096),
                expires = u.optLong("expires"),
                fields = f.keys().asSequence().associateWith { f.getString(it) },
            )
        }
        return Data(map, upload)
    }

    /** Уже оставляли отзыв об этом месте/компании с этого телефона. */
    fun alreadySent(target: String): Boolean = target in (Store.getRaw(KEY_SENT)?.split('\n') ?: emptyList())

    private fun device(): String =
        Store.getRaw(KEY_DEVICE) ?: UUID.randomUUID().toString().replace("-", "").take(20).also { Store.putRaw(KEY_DEVICE, it) }

    enum class SendResult { OK, OFFLINE, ERROR }

    /** Отправить отзыв на проверку. target — «seller:<id>» или «place:<id>». */
    suspend fun send(target: String, rating: Int, text: String, name: String): SendResult {
        var form = (data ?: load()).upload
        if (form == null || form.expires * 1000 < System.currentTimeMillis() + 60_000) {
            refresh(force = true)
            form = data?.upload
        }
        if (form == null || form.expires * 1000 <= System.currentTimeMillis()) return SendResult.ERROR
        val body = JSONObject()
            .put("target", target).put("rating", rating.coerceIn(1, 5))
            .put("text", text.trim().take(600)).put("name", name.trim().take(40))
            .put("device", device()).put("ts", System.currentTimeMillis() / 1000)
            .toString().toByteArray(Charsets.UTF_8)
        if (body.size > form.max) return SendResult.ERROR
        val result = withContext(Dispatchers.IO) { runCatching { post(form, body) } }
        val code = result.getOrNull()
        return when {
            code != null && code in 200..299 -> {
                Store.putRaw(KEY_SENT, ((Store.getRaw(KEY_SENT)?.split('\n') ?: emptyList()) + target).takeLast(500).joinToString("\n"))
                SendResult.OK
            }
            result.exceptionOrNull() is java.io.IOException -> SendResult.OFFLINE
            else -> SendResult.ERROR
        }
    }

    /** S3 POST Object: поля формы, затем файл (multipart/form-data). Возвращает HTTP-код. */
    private fun post(form: Upload, file: ByteArray): Int {
        val boundary = "----zikr" + UUID.randomUUID().toString().replace("-", "")
        val conn = URL(form.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        try {
            DataOutputStream(conn.outputStream).use { out ->
                fun field(name: String, value: String) {
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray(Charsets.UTF_8))
                }
                field("key", form.prefix + UUID.randomUUID().toString() + ".json")
                form.fields.forEach { (k, v) -> field(k, v) }
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"review.json\"\r\nContent-Type: application/json\r\n\r\n".toByteArray(Charsets.UTF_8))
                out.write(file)
                out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            }
            return conn.responseCode
        } finally {
            conn.disconnect()
        }
    }
}
