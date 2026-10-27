package com.zikrtime.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
    private const val KEY_TAG = "places_etag"
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
        /** Источник записи; отсутствие источника не означает подтверждённую проверку. */
        val source: String = "",
        val women: Boolean? = null,
        val accessConditions: String = "",
        val entrance: String = "",
        val sourceDetails: String = "",
        /** Explicit field only: never infer from created/updated/import time. */
        val confirmedAt: String = "",
    )

    class Data(val places: List<Place>, val sellerBot: String, val donateUrl: String, val correctionsEnabled: Boolean = false)

    var data by mutableStateOf<Data?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshFailed by mutableStateOf(false)
        private set
    var usingSavedCopy by mutableStateOf(false)
        private set

    fun load(): Data {
        data?.let { return it }
        val stored = Store.getRaw(KEY)?.let { runCatching { parse(it) }.getOrNull() } ?: Data(emptyList(), "", "")
        data = stored
        usingSavedCopy = Store.getRaw(KEY) != null
        return stored
    }

    suspend fun refresh(context: Context, force: Boolean = false) {
        load()
        val last = Store.getRaw(KEY_AT)?.toLongOrNull() ?: 0L
        if (!force && System.currentTimeMillis() - last < REFRESH_MS) return
        if (loading) return
        loading = true
        val hasCopy = Store.getRaw(KEY) != null
        val etag = if (hasCopy) Store.getRaw(KEY_TAG) else null
        val result = try {
            withContext(Dispatchers.IO) { runCatching { Net.getIfChanged(REMOTE, etag) } }
        } finally {
            loading = false
        }
        if (result.isSuccess && result.getOrNull() == null) {
            refreshFailed = false
            usingSavedCopy = false
            Store.putRaw(KEY_AT, System.currentTimeMillis().toString())
            return
        }
        val text = result.getOrNull()?.text
        val parsed = text?.let { runCatching { parse(it) }.getOrNull() }
        if (parsed == null) {
            refreshFailed = true
            usingSavedCopy = hasCopy
            return
        }
        refreshFailed = false
        usingSavedCopy = false
        Store.putRaw(KEY, text)
        Store.putRaw(KEY_TAG, result.getOrNull()?.etag.orEmpty())
        Store.putRaw(KEY_AT, System.currentTimeMillis().toString())
        data = parsed
    }

    fun parse(json: String): Data {
        val root = JSONObject(json)
        val arr = root.getJSONArray("places")
        val list = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            if (o.text("id").isBlank() || o.text("name").isBlank()) return@mapNotNull null
            val lat = o.optDouble("lat")
            val lon = o.optDouble("lon")
            if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            Place(
                id = o.text("id"), kind = o.text("kind", "other"), name = o.text("name"),
                lat = lat, lon = lon, address = o.text("address"), hours = o.text("hours"),
                who = o.text("who"), wudu = nullableBoolean(o.opt("wudu")),
                photo = o.text("photo"), note = o.text("note"), source = o.text("src"),
                women = if (o.has("women")) nullableBoolean(o.opt("women")) else when (o.text("who")) {
                    "both", "women" -> true
                    "men" -> false
                    else -> null
                },
                accessConditions = o.text("access_conditions", ""), entrance = o.text("entrance", ""),
                sourceDetails = o.text("source_details", ""), confirmedAt = confirmedDate(o.text("confirmed_at", "")),
            )
        }
        return Data(list.distinctBy { it.id }, root.text("seller_bot").removePrefix("@"), root.text("donate_url"), root.opt("corrections_enabled") == true)
    }

    private fun JSONObject.text(key: String, fallback: String = ""): String = (opt(key) as? String)?.trim() ?: fallback

    /** Strict tri-state: malformed strings/numbers must not become "нет". */
    internal fun nullableBoolean(value: Any?): Boolean? = value as? Boolean

    internal fun confirmedDate(value: String): String = if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) {
        runCatching { java.time.LocalDate.parse(value).toString() }.getOrDefault("")
    } else ""

    fun correctionUrl(placeId: String): String? = if (data?.correctionsEnabled == true) correctionUrl(bot(), placeId) else null

    internal fun correctionUrl(bot: String, placeId: String): String? {
        if (!Regex("[A-Za-z0-9_]{5,32}").matches(bot) || !Regex("[A-Za-z0-9_-]{1,48}").matches(placeId)) return null
        return "https://t.me/$bot?start=fix_$placeId"
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
