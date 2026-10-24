package com.zikrtime.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Места для намаза (мечети, молельные комнаты, намазханы в ТЦ). Люди предлагают места через Telegram-бота,
 * владелец проверяет их на месте и публикует. Список — places.json в Yandex Object Storage.
 */
object Places {
    private const val REMOTE = Shop.CONTENT_BASE + "places.json"
    private const val KEY = "places_json"
    private const val KEY_AT = "places_at"
    private const val REFRESH_MS = 60 * 1000L

    class Place(
        val id: String,
        val kind: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        val address: String,
        val hours: String,
        val who: String,
        val wudu: Boolean?,
        val photo: String,
        val note: String,
        /** "osm" — место из OpenStreetMap, иначе — предложено через бота и проверено. */
        val source: String = "",
    )

    class Data(val places: List<Place>, val sellerBot: String, val donateUrl: String)

    var data by mutableStateOf<Data?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    fun load(): Data {
        data?.let { return it }
        val stored = Store.getRaw(KEY)?.let { runCatching { parse(it) }.getOrNull() } ?: Data(emptyList(), "", "")
        data = stored
        return stored
    }

    suspend fun refresh(context: Context, force: Boolean = false) {
        load()
        val last = Store.getRaw(KEY_AT)?.toLongOrNull() ?: 0L
        if (!force && System.currentTimeMillis() - last < REFRESH_MS) return
        loading = true
        val text = withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(REMOTE).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                try {
                    if (conn.responseCode != 200) null else conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }
        loading = false
        val parsed = text?.let { runCatching { parse(it) }.getOrNull() } ?: return
        Store.putRaw(KEY, text)
        Store.putRaw(KEY_AT, System.currentTimeMillis().toString())
        data = parsed
    }

    fun parse(json: String): Data {
        val root = JSONObject(json)
        val arr = root.optJSONArray("places")
        val list = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
            val o = arr!!.getJSONObject(i)
            val lat = o.optDouble("lat")
            val lon = o.optDouble("lon")
            if (lat.isNaN() || lon.isNaN()) return@mapNotNull null
            Place(
                id = o.optString("id"), kind = o.optString("kind", "other"), name = o.optString("name"),
                lat = lat, lon = lon, address = o.optString("address"), hours = o.optString("hours"),
                who = o.optString("who"), wudu = if (o.has("wudu") && !o.isNull("wudu")) o.optBoolean("wudu") else null,
                photo = o.optString("photo"), note = o.optString("note"), source = o.optString("src"),
            )
        }
        return Data(list, root.optString("seller_bot").removePrefix("@"), root.optString("donate_url"))
    }

    /** Расстояние в километрах. */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Ссылка на кнопку «Поддержать проект» (из списка мест или каталога магазина). */
    fun donateUrl(): String = data?.donateUrl?.takeIf { it.isNotBlank() } ?: Shop.catalog?.donateUrl.orEmpty()

    /** Telegram-бот (он же кабинет продавцов) — туда отправляем предложения мест. */
    fun bot(): String = data?.sellerBot?.takeIf { it.isNotBlank() } ?: Shop.catalog?.sellerBot.orEmpty()
}
