package com.zikrtime.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShopOfferTest {
    @Test fun aStoreOfferRequiresBothPricesAndAnActualReduction() {
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
}
