package com.zikrtime.app.data

import java.net.HttpURLConnection
import java.net.URL

/** Загрузка файлов каталога: если файл не менялся (ETag), он не скачивается заново — экономим трафик. */
object Net {
    /** Текст файла; null — не изменился с прошлого раза (ответ 304). Ошибки сети и HTTP — исключением. */
    fun getIfChanged(url: String, tagKey: String, hasCopy: Boolean): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        val tag = Store.getRaw(tagKey)
        if (hasCopy && !tag.isNullOrBlank()) conn.setRequestProperty("If-None-Match", tag)
        try {
            return when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> null
                HttpURLConnection.HTTP_OK -> {
                    val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    conn.getHeaderField("ETag")?.let { Store.putRaw(tagKey, it) }
                    text
                }
                else -> error("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }
}
