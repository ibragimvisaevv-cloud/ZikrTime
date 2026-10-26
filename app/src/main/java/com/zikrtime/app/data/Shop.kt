package com.zikrtime.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Магазин: продавцы и их товары.
 * Каталог собирает Telegram-бот продавцов (shop-bot) в Yandex Object Storage — обновляется без выпуска приложения;
 * копия встроена в приложение (assets/shop/catalog.json) и сохраняется после каждой загрузки —
 * магазин открывается и без интернета.
 */
object Shop {
    /** Каталог и фото собирает бот продавцов в Yandex Object Storage (быстро в России и Средней Азии). */
    const val CONTENT_BASE = "https://storage.yandexcloud.net/zikrtime-shop/"
    private const val REMOTE = CONTENT_BASE + "catalog.json"
    private const val KEY = "shop_catalog"
    private const val KEY_TAG = "shop_catalog_etag"
    private const val KEY_AT = "shop_catalog_at"
    private const val REFRESH_MS = 60 * 1000L // при каждом открытии магазина, не чаще раза в минуту

    /** Текст на нескольких языках: {"ru": "...", "en": "..."}. */
    class Text(private val values: Map<String, String>) {
        fun get(lang: String): String = values[lang] ?: values["ru"] ?: values["en"] ?: values.values.firstOrNull().orEmpty()
        val isEmpty get() = values.values.all { it.isBlank() }
    }

    class Category(val id: String, val name: Text, val icon: String)

    class Seller(
        val id: String,
        val name: String,
        val partner: Boolean,
        val city: Text,
        val description: Text,
        val address: String,
        val phone: String,
        val telegram: String,
        val whatsapp: String,
        val instagram: String,
        val website: String,
        val lat: Double?,
        val lon: Double?,
        val logo: String,
        val cover: String,
        val categories: List<String>,
        val accent: Long?,
        /** До какого момента (секунды) компания продвигается; 0 — не продвигается. */
        val promoUntil: Long = 0,
    ) {
        val promoted: Boolean get() = promoUntil * 1000 > System.currentTimeMillis()
    }

    class Product(
        val id: String,
        val seller: String,
        val category: String,
        val title: Text,
        val description: Text,
        val price: Long?,
        val oldPrice: Long?,
        val unit: Text,
        val currency: String,
        val images: List<String>,
    )

    class Catalog(
        val demo: Boolean,
        val contactTelegram: String,
        /** Telegram-бот, через который продавцы добавляют свои компании. */
        val sellerBot: String,
        /** Ссылка «Поддержать проект» (CloudTips). */
        val donateUrl: String = "",
        val categories: List<Category>,
        val sellers: List<Seller>,
        val products: List<Product>,
    ) {
        fun seller(id: String?) = sellers.firstOrNull { it.id == id }
        fun product(id: String?) = products.firstOrNull { it.id == id }
        fun category(id: String) = categories.firstOrNull { it.id == id }
    }

    /** Текущий каталог (меняется после загрузки свежей версии). */
    var catalog by mutableStateOf<Catalog?>(null)
        private set
    var offline by mutableStateOf(false)
        private set

    fun load(context: Context): Catalog {
        catalog?.let { return it }
        val stored = Store.getRaw(KEY)?.let { runCatching { parse(it) }.getOrNull() }
        val result = stored ?: parse(context.assets.open("shop/catalog.json").bufferedReader().use { it.readText() })
        catalog = result
        return result
    }

    /** Свежий каталог из сети (не чаще раза в минуту, если не force). */
    suspend fun refresh(context: Context, force: Boolean = false) {
        load(context)
        val last = Store.getRaw(KEY_AT)?.toLongOrNull() ?: 0L
        if (!force && System.currentTimeMillis() - last < REFRESH_MS) return
        val hasCopy = Store.getRaw(KEY) != null
        val etag = if (hasCopy) Store.getRaw(KEY_TAG) else null
        val result = withContext(Dispatchers.IO) { runCatching { Net.getIfChanged(REMOTE, etag) } }
        if (result.isSuccess && result.getOrNull() == null) {
            // Каталог не изменился — ничего не скачивали
            offline = false
            Store.putRaw(KEY_AT, System.currentTimeMillis().toString())
            return
        }
        val text = result.getOrNull()?.text
        val parsed = text?.let { runCatching { parse(it) }.getOrNull() }
        if (parsed == null) {
            // «Нет интернета» — только если не было связи (а не, например, ответ 404).
            offline = result.exceptionOrNull() is java.io.IOException
            return
        }
        offline = false
        Store.putRaw(KEY, text)
        Store.putRaw(KEY_TAG, result.getOrNull()?.etag.orEmpty())
        Store.putRaw(KEY_AT, System.currentTimeMillis().toString())
        catalog = parsed
    }

    /**
     * Порядок в магазине: сначала продвигаемые (за плату), потом остальные — в случайном порядке.
     * Порядок свой у каждого телефона и меняется раз в день, чтобы все продавцы были на виду по очереди.
     */
    private fun daySeed(): Long {
        val device = Store.getRaw("shop_seed")?.toLongOrNull()
            ?: kotlin.random.Random.nextLong(1, Long.MAX_VALUE / 2).also { Store.putRaw("shop_seed", it.toString()) }
        return device + java.time.LocalDate.now().toEpochDay()
    }

    fun rankSellers(sellers: List<Seller>): List<Seller> {
        val seed = daySeed()
        val (promo, rest) = sellers.partition { it.promoted }
        return promo.shuffled(kotlin.random.Random(seed)) + rest.shuffled(kotlin.random.Random(seed + 1))
    }

    fun rankProducts(products: List<Product>, catalog: Catalog): List<Product> {
        val order = rankSellers(catalog.sellers).withIndex().associate { (i, s) -> s.id to i }
        val seed = daySeed()
        val (promo, rest) = products.partition { catalog.seller(it.seller)?.promoted == true }
        return promo.sortedBy { order[it.seller] ?: Int.MAX_VALUE } + rest.shuffled(kotlin.random.Random(seed + 2))
    }

    /** Адрес картинки: полный URL или путь внутри репозитория каталога. */
    fun imageUrl(path: String): String? = when {
        path.isBlank() -> null
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("asset:") -> "file:///android_asset/" + path.removePrefix("asset:")
        else -> CONTENT_BASE + path
    }

    fun parse(json: String): Catalog {
        val root = JSONObject(json)
        fun text(o: JSONObject, key: String): Text {
            val v = o.opt(key)
            return when (v) {
                is JSONObject -> Text(v.keys().asSequence().associateWith { v.optString(it) })
                is String -> Text(mapOf("ru" to v))
                else -> Text(emptyMap())
            }
        }
        fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
        fun objects(name: String): List<JSONObject> = root.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()

        val categories = objects("categories").map { Category(it.getString("id"), text(it, "names"), it.optString("icon", "store")) }
        val sellers = objects("sellers").map {
            Seller(
                id = it.getString("id"),
                name = it.getString("name"),
                partner = it.optBoolean("partner"),
                city = text(it, "city"),
                description = text(it, "description"),
                address = it.optString("address"),
                phone = it.optString("phone"),
                telegram = it.optString("telegram").removePrefix("@"),
                whatsapp = it.optString("whatsapp"),
                instagram = it.optString("instagram").removePrefix("@"),
                website = it.optString("website"),
                lat = it.optDouble("lat").takeIf { d -> !d.isNaN() },
                lon = it.optDouble("lon").takeIf { d -> !d.isNaN() },
                logo = it.optString("logo"),
                cover = it.optString("cover"),
                categories = strings(it.optJSONArray("categories")),
                accent = it.optString("accent").removePrefix("#").takeIf { s -> s.length == 6 }?.toLongOrNull(16),
                promoUntil = it.optLong("promo_until"),
            )
        }
        val products = objects("products").map {
            Product(
                id = it.getString("id"),
                seller = it.getString("seller"),
                category = it.optString("category"),
                title = text(it, "title"),
                description = text(it, "description"),
                price = it.optLong("price", -1).takeIf { p -> p >= 0 },
                oldPrice = it.optLong("old_price", -1).takeIf { p -> p >= 0 },
                unit = text(it, "unit"),
                currency = it.optString("currency", "RUB"),
                images = strings(it.optJSONArray("images")),
            )
        }.filter { p -> sellers.any { it.id == p.seller } }
        return Catalog(
            root.optBoolean("demo"), root.optString("contact_telegram").removePrefix("@"),
            root.optString("seller_bot").removePrefix("@"), root.optString("donate_url"), categories, sellers, products,
        )
    }

    /** «2 500 ₽» */
    fun price(value: Long, currency: String): String {
        val digits = value.toString().reversed().chunked(3).joinToString(" ").reversed()
        val sign = when (currency) {
            "RUB" -> "₽"
            "USD" -> "$"
            "EUR" -> "€"
            "KZT" -> "₸"
            "UZS" -> "сўм"
            "TJS" -> "сом."
            "KGS" -> "сом"
            "AZN" -> "₼"
            "TRY" -> "₺"
            else -> currency
        }
        return "$digits $sign"
    }
}
