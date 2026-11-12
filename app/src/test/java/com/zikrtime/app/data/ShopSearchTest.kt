package com.zikrtime.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopSearchTest {
    private val catalog = Shop.parse(
        """{"categories":[{"id":"scent","names":{"ru":"Духи","en":"Perfume"}},{"id":"cars","names":"Автопрокат"}],
           "sellers":[{"id":"p","name":"ALI.PERFUME","city":"Грозный","categories":["scent"]},
                      {"id":"c","name":"Прокат","city":"Аргун","categories":["cars"]}],
           "products":[{"id":"one","seller":"p","title":{"ru":"Тёмный уд","en":"Dark oud"},"category":"scent","price":2500},
                       {"id":"two","seller":"p","title":"Мускус","description":"Нота тёмного уда","category":"scent"},
                       {"id":"three","seller":"c","title":"Тёмный автомобиль","category":"cars","price":0},
                       {"id":"four","seller":"p","title":"Набор","category":"scent","price":50,"currency":"USD"}]}"""
    )
    private val search = ShopSearch(catalog, "ru", catalog.sellers, catalog.products)

    @Test fun emptySearchDoesNotListTheInventory() {
        assertTrue(search.products("   ", null, null, null, null).isEmpty())
        assertTrue(search.sellers("", null, null).isEmpty())
    }

    @Test fun termsMatchAcrossFieldsWithCaseAndWhitespaceNormalized() {
        assertEquals(listOf("one", "two"), search.products("   Уд   ТЕМН   ali.perfume ", null, null, null, null).map { it.id })
        assertEquals(listOf("c"), search.sellers("Прокат Аргун", null, null).map { it.id })
        val english = ShopSearch(catalog, "en", catalog.sellers, catalog.products)
        assertEquals(listOf("one"), english.products("perfume OUD", null, null, null, null).map { it.id })
    }

    @Test fun filtersCombineInclusivelyAndOnlyCompareRublePrices() {
        assertEquals(listOf("one"), search.products("", "scent", "Грозный", 2500L, 2500L).map { it.id })
        assertEquals(listOf("three"), search.products("", null, null, null, 100L).map { it.id })
        assertTrue(search.products("уд", "scent", "Аргун", null, null).isEmpty())
        assertEquals(listOf("one", "two", "four"), search.products("", "scent", null, null, null).map { it.id })
        assertEquals(listOf("Аргун", "Грозный"), search.cities)
    }
}
