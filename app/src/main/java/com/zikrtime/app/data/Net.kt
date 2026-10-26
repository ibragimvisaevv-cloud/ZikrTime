package com.zikrtime.app.data

import java.net.HttpURLConnection
import java.net.URL

/** Загрузка файлов каталога: если файл не менялся (ETag), он не скачивается заново — экономим трафик. */
object Net {
    data class Response(val text: String, val etag: String?)

    /** null — 304. Вызывающий код сохраняет ETag только вместе с успешно разобранными данными. */
    fun getIfChanged(url: String, etag: String?): Response? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        if (!etag.isNullOrBlank()) conn.setRequestProperty("If-None-Match", etag)
        try {
            return when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    check(!etag.isNullOrBlank()) { "304 without a cached response" }
                    null
                }
                HttpURLConnection.HTTP_OK -> {
                    val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    Response(text, conn.getHeaderField("ETag"))
                }
                else -> error("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }
}
