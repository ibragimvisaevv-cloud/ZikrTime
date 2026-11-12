package com.zikrtime.app.data

import java.util.Locale

/** Localized search fields and the existing daily promotion order are prepared once per catalog. */
internal class ShopSearch(
    catalog: Shop.Catalog,
    lang: String,
    rankedSellers: List<Shop.Seller> = Shop.rankSellers(catalog.sellers),
    rankedProducts: List<Shop.Product> = Shop.rankProducts(catalog.products, catalog),
) {
    private data class Entry<T>(val item: T, val city: String, val text: String)

    private val categories = catalog.categories.associate { it.id to it.name.get(lang) }
    val cities = catalog.sellers.map { it.city.get(lang).trim() }
        .filter { it.isNotEmpty() }
        .distinctBy(::normalize)
        .sortedBy(::normalize)

    private val sellerEntries = rankedSellers.map { seller ->
        val city = seller.city.get(lang)
        val categoryText = seller.categories.mapNotNull(categories::get).joinToString(" ")
        Entry(seller, normalize(city), normalize("${seller.name} ${seller.description.get(lang)} $city $categoryText"))
    }
    private val productEntries = rankedProducts.map { product ->
        val seller = catalog.seller(product.seller)
        val city = seller?.city?.get(lang).orEmpty()
        val text = listOf(product.title.get(lang), product.description.get(lang), seller?.name.orEmpty(),
            categories[product.category].orEmpty(), city).joinToString(" ")
        Entry(product, normalize(city), normalize(text))
    }

    fun products(query: String, categoryId: String?, city: String?, minPrice: Long?, maxPrice: Long?): List<Shop.Product> {
        val words = terms(query)
        val cityKey = normalize(city.orEmpty())
        val priced = minPrice != null || maxPrice != null
        if (words.isEmpty() && categoryId == null && cityKey.isEmpty() && !priced) return emptyList()
        return productEntries.asSequence().filter { (product, sellerCity, text) ->
            val price = product.price
            (categoryId == null || product.category == categoryId) &&
                (cityKey.isEmpty() || cityKey == sellerCity) &&
                (!priced || price != null && product.currency.equals("RUB", ignoreCase = true) &&
                    (minPrice == null || price >= minPrice) && (maxPrice == null || price <= maxPrice)) &&
                words.all(text::contains)
        }.map { it.item }.toList()
    }

    fun sellers(query: String, categoryId: String?, city: String?): List<Shop.Seller> {
        val words = terms(query)
        val cityKey = normalize(city.orEmpty())
        if (words.isEmpty() && categoryId == null && cityKey.isEmpty()) return emptyList()
        return sellerEntries.asSequence().filter { (seller, sellerCity, text) ->
            (categoryId == null || categoryId in seller.categories) &&
                (cityKey.isEmpty() || sellerCity == cityKey) &&
                words.all(text::contains)
        }.map { it.item }.toList()
    }

    private companion object {
        val separators = Regex("[^\\p{L}\\p{M}\\p{N}]+")

        fun normalize(text: String): String =
            text.lowercase(Locale.ROOT).replace('ё', 'е').replace(separators, " ").trim()

        fun terms(query: String): List<String> =
            normalize(query).takeIf { it.isNotEmpty() }?.split(' ').orEmpty()
    }
}
