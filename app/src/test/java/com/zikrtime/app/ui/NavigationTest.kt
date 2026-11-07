package com.zikrtime.app.ui

import org.junit.Assert.*
import org.junit.Test

class NavigationTest {
    @Test fun openingAFeatureFromTheGuidePreservesItsEntry() {
        val nav = Nav()
        nav.tab = Tab.MORE
        nav.open(Screen.GUIDE)
        val guide = nav.stack.last()
        nav.open(Screen.QURAN)
        assertTrue(nav.back())
        assertEquals(guide, nav.stack.last())
        assertEquals(Tab.MORE, nav.tab)
    }

    @Test fun visitingATabAndItsChildReturnsToTheSameGuide() {
        val nav = Nav()
        nav.tab = Tab.MORE
        nav.open(Screen.GUIDE)
        val guide = nav.stack.last()
        nav.openGuideTab(Tab.FOCUS)
        assertEquals(Tab.FOCUS, nav.tab)
        nav.open(Screen.APPS)
        assertTrue(nav.back())
        assertEquals(Screen.GUIDE_DESTINATION, nav.stack.last().screen)
        assertTrue(nav.back())
        assertEquals(guide, nav.stack.last())
        assertEquals(Tab.MORE, nav.tab)
    }

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
