package com.zikrtime.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Экраны второго уровня (поверх вкладок). */
enum class Screen { LANGUAGE, CITY, PRAYER_SETTINGS, ALARM, QIBLA, TASBIH, QUIZ, APPS, ABOUT, SHOP_SELLER, SHOP_PRODUCT, PLACES, RAMADAN, ZAKAT }

/** Вкладки нижней панели. */
enum class Tab { PRAYER, KNOWLEDGE, SHOP, FOCUS, MORE }

/** Простая навигация: текущая вкладка и стопка открытых экранов. */
class Nav {
    var tab by mutableStateOf(Tab.PRAYER)
    val stack = mutableStateListOf<Screen>()

    /** Тема для раунда викторины (null — все темы). */
    var quizCategory by mutableStateOf<String?>(null)

    /** Увеличивается при каждом новом раунде — чтобы экран викторины начинался заново. */
    var quizRound by mutableIntStateOf(0)

    /** Открытый продавец / товар в магазине. */
    var shopSeller by mutableStateOf<String?>(null)
    var shopProduct by mutableStateOf<String?>(null)

    /** Только для проверки в CI: начать раунд сразу с карточек подготовки. */
    var quizStartWithCards = false

    /** С какого шага начинать знакомство (0 — язык, 1 — приветствие, 2 — город). */
    var onboardingStep by mutableIntStateOf(0)

    fun open(screen: Screen) {
        if (stack.lastOrNull() != screen) stack.add(screen)
    }

    fun startQuiz(category: String?) {
        quizCategory = category
        quizRound++
        open(Screen.QUIZ)
    }

    fun back(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav не задан") }
