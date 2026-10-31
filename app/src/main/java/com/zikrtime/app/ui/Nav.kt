package com.zikrtime.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Экраны второго уровня (поверх вкладок). */
enum class Screen { LANGUAGE, CITY, PRAYER_SETTINGS, ALARM, QIBLA, TASBIH, QUIZ, APPS, ABOUT, SHOP_SELLER, SHOP_PRODUCT, PLACES, RAMADAN, ZAKAT, REVIEW, PROGRESS, AZKAR, QURAN }

/** Вкладки нижней панели. */
enum class Tab { PRAYER, KNOWLEDGE, SHOP, FOCUS, MORE }

data class NavEntry(val screen: Screen, val id: Int, val seller: String? = null, val product: String? = null)

/** Простая навигация: текущая вкладка и стопка открытых экранов. */
class Nav {
    var tab by mutableStateOf(Tab.PRAYER)
    val stack = mutableStateListOf<NavEntry>()
    private var nextEntry = 0

    /** Тема для раунда викторины (null — все темы). */
    var quizCategory by mutableStateOf<String?>(null)

    /** Увеличивается при каждом новом раунде — чтобы экран викторины начинался заново. */
    var quizRound by mutableIntStateOf(0)

    /** Открытый продавец / товар в магазине. */
    var shopSeller by mutableStateOf<String?>(null)
    var shopProduct by mutableStateOf<String?>(null)

    /** Только для проверки в CI: начать раунд сразу с карточек подготовки. */
    var quizStartWithCards = false

    /** С какого шага начинать знакомство (0 — приветствие, 1 — язык, 2 — город, 3 — разрешения, 4 — готово). */
    var onboardingStep by mutableIntStateOf(0)

    fun open(screen: Screen) {
        val old = stack.lastOrNull()
        if (old?.screen != screen || (screen == Screen.SHOP_SELLER && old?.seller != shopSeller) ||
            (screen == Screen.SHOP_PRODUCT && old?.product != shopProduct)) {
            stack.add(NavEntry(screen, ++nextEntry, shopSeller, shopProduct))
        }
    }

    fun startQuiz(category: String?) {
        quizCategory = category
        quizRound++
        open(Screen.QUIZ)
    }

    var azkarPeriod by mutableStateOf("morning")

    fun startReview() { quizRound++; open(Screen.REVIEW) }
    fun resumeLearning(context: android.content.Context) {
        val bank = com.zikrtime.app.quiz.QuestionBank.load(context)
        val pending = com.zikrtime.app.quiz.LearningStore.pendingSession(bank) ?: return
        if (pending.scope == "review") startReview() else startQuiz(pending.category)
    }
    fun openAzkar(period: String) {
        azkarPeriod = if (period == "evening") "evening" else "morning"
        com.zikrtime.app.data.AzkarStore.start(azkarPeriod)
        open(Screen.AZKAR)
    }
    fun resumeAzkar() { com.zikrtime.app.data.AzkarStore.pendingPeriod?.let { openAzkar(it) } }

    fun save(out: android.os.Bundle) {
        out.putString("nav_tab", tab.name)
        out.putStringArrayList("nav_routes", ArrayList(stack.map { "${it.screen.name}|${it.id}|${it.seller.orEmpty()}|${it.product.orEmpty()}" }))
        out.putString("nav_category", quizCategory); out.putInt("nav_round", quizRound)
        out.putString("nav_period", azkarPeriod)
    }
    fun restore(saved: android.os.Bundle?) {
        if (saved == null) return
        tab = runCatching { Tab.valueOf(saved.getString("nav_tab").orEmpty()) }.getOrDefault(Tab.PRAYER)
        saved.getStringArrayList("nav_routes")?.forEach { raw ->
            val fields = raw.split('|')
            runCatching { NavEntry(Screen.valueOf(fields[0]), fields[1].toInt(), fields[2].ifBlank { null }, fields[3].ifBlank { null }) }
                .getOrNull()?.let { stack.add(it); nextEntry = maxOf(nextEntry, it.id) }
        }
        quizCategory = saved.getString("nav_category"); quizRound = saved.getInt("nav_round")
        azkarPeriod = saved.getString("nav_period") ?: "morning"
        shopSeller = stack.lastOrNull()?.seller; shopProduct = stack.lastOrNull()?.product
    }

    fun back(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav не задан") }
