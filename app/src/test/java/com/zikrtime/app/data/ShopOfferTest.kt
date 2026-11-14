package com.zikrtime.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ShopOfferTest {
    @Test fun aProductDiscountRequiresBothPricesAndAnActualReduction() {
        val catalog = Shop.parse("""
            {"sellers":[{"id":"s","name":"Store","promo_until":4102444800}],
             "products":[
               {"id":"discount","seller":"s","price":11900,"old_price":15900},
               {"id":"exact","seller":"s","price":71,"old_price":100},
               {"id":"small","seller":"s","price":999,"old_price":1000},
               {"id":"free","seller":"s","price":0,"old_price":300},
               {"id":"equal","seller":"s","price":300,"old_price":300},
               {"id":"increase","seller":"s","price":301,"old_price":300},
               {"id":"missing","seller":"s","price":300},
               {"id":"on-request","seller":"s","old_price":300},
               {"id":"invalid","seller":"s","price":-1,"old_price":300}
             ]}
        """.trimIndent())

        val offers = catalog.products.mapNotNull { product ->
            product.discountPercent?.let { product.id to it }
        }
        assertEquals(listOf("discount" to 25, "exact" to 29, "small" to 0, "free" to 100), offers)
    }

    @Test fun storeOffersRemainVisibleThroughTheirLastDayAndNeverCreateProductDiscounts() {
        val catalog = Shop.parse("""
            {"sellers":[{"id":"s","name":"Cars","offers":[
                {"id":"third","title":"Third day free","conditions":"Rent for two days","valid_until":"2026-11-14"},
                {"id":"past","title":"Past","conditions":"Rental","valid_until":"2026-11-13"},
                {"id":"bad","title":"Impossible date","conditions":"Rental","valid_until":"2026-02-30"},
                {"id":"empty","title":"Missing conditions","conditions":null,"valid_until":"2026-12-01"}]},
                {"id":"old","name":"Cached store"}],
             "products":[{"id":"p","seller":"s","price":3000}]}
        """.trimIndent())
        val seller = requireNotNull(catalog.seller("s"))
        val finalDay = LocalDate.of(2026, 11, 14)
        val offer = seller.activeOffers(finalDay).single()
        assertEquals("third", offer.id)
        assertEquals("Rent for two days", offer.conditions)
        assertEquals(finalDay, offer.validUntil)
        assertEquals(emptyList<Shop.Offer>(), seller.activeOffers(finalDay.plusDays(1)))
        assertEquals(emptyList<Shop.Offer>(), catalog.seller("old")?.offers)
        assertEquals(null, catalog.product("p")?.discountPercent)
    }
}
