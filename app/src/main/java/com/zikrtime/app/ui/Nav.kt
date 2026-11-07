package com.zikrtime.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Экраны второго уровня (поверх вкладок). */
enum class Screen { LANGUAGE, CITY, PRAYER_SETTINGS, ALARM, QIBLA, TASBIH, QUIZ, APPS, ABOUT, SHOP_SELLER, SHOP_PRODUCT, PLACES, RAMADAN, ZAKAT, REVIEW, PROGRESS, AZKAR, QURAN, TRAINING, PRAYER_JOURNAL, READING_REMINDERS, READING_BEDTIME, READING_FRIDAY, GUIDE, GUIDE_DESTINATION, QURAN_AUDIO }

/** Вкладки нижней панели. */
enum class Tab { PRAYER, KNOWLEDGE, SHOP, FOCUS, MORE }

data class NavEntry(val screen: Screen, val id: Int, val seller: String? = null, val product: String? = null,
                    val journalDay: String? = null, val journalPrayer: String? = null,
                    val returnTab: Tab? = null, val audioSurah: Int = 0)

/** Простая навигация: текущая вкладка и стопка открытых экранов. */
class Nav {
    var tab by mutableStateOf(Tab.PRAYER)
    val stack = mutableStateListOf<NavEntry>()
    private var nextEntry = 0

    /** Тема для раунда викторины (null — все темы). */
    var quizCategory by mutableStateOf<String?>(null)

    var trainingCategory by mutableStateOf<String?>(null)
    var trainingActive by mutableStateOf(false)

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

    fun openJournal(day: String?, prayer: String?) {
        if (stack.lastOrNull()?.screen == Screen.PRAYER_JOURNAL) back()
        stack.add(NavEntry(Screen.PRAYER_JOURNAL, ++nextEntry, journalDay = day, journalPrayer = prayer))
    }

    /** A notification opens the player without duplicating it or starting playback. */
    fun openQuranAudio(surahId: Int = 0) {
        val surah = surahId.takeIf { it in 1..114 } ?: 0
        val top = stack.lastOrNull()
        if (top?.screen == Screen.QURAN_AUDIO) {
            if (top.audioSurah == surah) return
            stack.removeAt(stack.lastIndex)
        }
        stack.add(NavEntry(Screen.QURAN_AUDIO, ++nextEntry, audioSurah = surah))
    }

    /** Keep the guide entry alive so returning restores its topic and reading position. */
    fun openGuideTab(destination: Tab) {
        val previous = tab
        stack.add(NavEntry(Screen.GUIDE_DESTINATION, ++nextEntry, returnTab = previous))
        tab = destination
    }

    fun openReading(kind: com.zikrtime.app.prayer.ReadingKind) {
        val route = if (kind == com.zikrtime.app.prayer.ReadingKind.BEDTIME) Screen.READING_BEDTIME else Screen.READING_FRIDAY
        if (stack.lastOrNull()?.screen in setOf(Screen.READING_BEDTIME, Screen.READING_FRIDAY)) back()
        open(route)
    }

    fun startQuiz(category: String?) {
        if (com.zikrtime.app.quiz.LearningStore.session("normal")?.stage == "RESULT")
            com.zikrtime.app.quiz.LearningStore.clearSession("normal")
        quizCategory = category
        quizRound++
        open(Screen.QUIZ)
    }

    var azkarPeriod by mutableStateOf("morning")

    fun startReview() {
        if (com.zikrtime.app.quiz.LearningStore.session("review")?.stage == "RESULT")
            com.zikrtime.app.quiz.LearningStore.clearSession("review")
        quizRound++; open(Screen.REVIEW)
    }
    fun resumeLearning(context: android.content.Context) {
        val bank = com.zikrtime.app.quiz.QuestionBank.load(context)
        val pending = com.zikrtime.app.quiz.LearningStore.pendingSession(bank) ?: return
        when(pending.scope) {
            "review" -> startReview()
            "training" -> startTraining(pending.category)
            else -> startQuiz(pending.category)
        }
    }
    fun openTraining() { trainingActive = false; open(Screen.TRAINING) }
    fun startTraining(category: String?) {
        if (com.zikrtime.app.quiz.LearningStore.session("training")?.stage == "RESULT")
            com.zikrtime.app.quiz.LearningStore.clearSession("training")
        trainingCategory = category
        trainingActive = true
        quizRound++
        open(Screen.TRAINING)
    }
    fun openAzkar(period: String) {
        azkarPeriod = if (period == "evening") "evening" else "morning"
        com.zikrtime.app.data.AzkarStore.start(azkarPeriod)
        open(Screen.AZKAR)
    }
    fun resumeAzkar() { com.zikrtime.app.data.AzkarStore.pendingPeriod?.let { openAzkar(it) } }

    fun save(out: android.os.Bundle) {
        out.putString("nav_tab", tab.name)
        out.putStringArrayList("nav_routes", ArrayList(stack.map { "${it.screen.name}|${it.id}|${it.seller.orEmpty()}|${it.product.orEmpty()}|${it.journalDay.orEmpty()}|${it.journalPrayer.orEmpty()}|${it.returnTab?.name.orEmpty()}|${it.audioSurah}" }))
        out.putString("nav_category", quizCategory); out.putInt("nav_round", quizRound)
        out.putString("nav_training_category", trainingCategory); out.putBoolean("nav_training_active", trainingActive)
        out.putString("nav_period", azkarPeriod)
    }
    fun restore(saved: android.os.Bundle?) {
        if (saved == null) return
        tab = runCatching { Tab.valueOf(saved.getString("nav_tab").orEmpty()) }.getOrDefault(Tab.PRAYER)
        saved.getStringArrayList("nav_routes")?.forEach { raw ->
            val fields = raw.split('|')
            runCatching { NavEntry(Screen.valueOf(fields[0]), fields[1].toInt(), fields[2].ifBlank { null }, fields[3].ifBlank { null },
                fields.getOrNull(4)?.ifBlank { null }, fields.getOrNull(5)?.ifBlank { null },
                fields.getOrNull(6)?.takeIf { it.isNotBlank() }?.let { Tab.valueOf(it) },
                fields.getOrNull(7)?.toIntOrNull()?.takeIf { it in 1..114 } ?: 0) }
                .getOrNull()?.let { stack.add(it); nextEntry = maxOf(nextEntry, it.id) }
        }
        quizCategory = saved.getString("nav_category"); quizRound = saved.getInt("nav_round")
        trainingCategory = saved.getString("nav_training_category"); trainingActive = saved.getBoolean("nav_training_active")
        azkarPeriod = saved.getString("nav_period") ?: "morning"
        shopSeller = stack.lastOrNull()?.seller; shopProduct = stack.lastOrNull()?.product
    }

    fun back(): Boolean {
        if (stack.isEmpty()) return false
        val closed = stack.removeAt(stack.lastIndex)
        if (closed.screen == Screen.GUIDE_DESTINATION) closed.returnTab?.let { tab = it }
        return true
    }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav не задан") }
