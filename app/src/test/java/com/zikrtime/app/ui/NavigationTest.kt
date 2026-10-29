package com.zikrtime.app.ui

import org.junit.Assert.*
import org.junit.Test

class NavigationTest {
    @Test fun sellerProductSellerBackRestoresTheOriginalEntry() {
        val nav = Nav()
        nav.shopSeller = "first"; nav.open(Screen.SHOP_SELLER)
        val original = nav.stack.last()
        nav.shopProduct = "item"; nav.open(Screen.SHOP_PRODUCT)
        nav.shopSeller = "second"; nav.open(Screen.SHOP_SELLER)
        assertTrue(nav.back())
        assertEquals("item", nav.stack.last().product)
        assertTrue(nav.back())
        assertEquals(original, nav.stack.last())
        assertEquals("first", nav.stack.last().seller)
    }
    @Test fun duplicateDestinationDoesNotGrowStackButDifferentSellerDoes() {
        val nav = Nav()
        nav.shopSeller = "a"; nav.open(Screen.SHOP_SELLER); nav.open(Screen.SHOP_SELLER)
        assertEquals(1, nav.stack.size)
        nav.shopSeller = "b"; nav.open(Screen.SHOP_SELLER)
        assertEquals(2, nav.stack.size)
    }
    @Test fun backAtRootDoesNotConsumeSystemBack() {
        val nav = Nav()
        assertFalse(nav.back())
        nav.open(Screen.CITY)
        assertTrue(nav.back())
        assertFalse(nav.back())
    }
}
