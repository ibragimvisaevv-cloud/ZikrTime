package com.zikrtime.app

import android.app.Activity
import android.content.Intent
import com.zikrtime.app.alarm.AlarmActivity
import com.zikrtime.app.data.Cities
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.screentime.BlockActivity
import com.zikrtime.app.screentime.BlockReason
import com.zikrtime.app.ui.Nav
import com.zikrtime.app.ui.Screen
import com.zikrtime.app.ui.Tab
import java.time.Instant
import java.time.LocalDate

/**
 * Только для отладочной сборки: автоматическая проверка в CI открывает экраны через параметры запуска
 * (qa_lang, qa_demo, qa_screen) и снимает скриншоты. В release-сборке этот код не выполняется.
 */
object QaHooks {

    /** Возвращает true, если экран будет пересоздан (смена языка) и дальше идти не нужно. */
    /** Показать виджеты рабочего стола вместо приложения (для снимков на эмуляторе). */
    var hideScene = false
    var widgetPreview = false
    var skyPreview: com.zikrtime.app.ui.NaturalPeriod? = null

    fun handle(activity: Activity, intent: Intent?, nav: Nav): Boolean {
        intent ?: return false
        val lang = intent.getStringExtra("qa_lang")
        if (lang != null && lang != Lang.saved(activity)) {
            Lang.set(activity, lang)
            return true
        }
        com.zikrtime.app.ui.LiveFlags.tilt = intent.getBooleanExtra("qa_tilt", true)
        com.zikrtime.app.ui.LiveFlags.parallax = intent.getBooleanExtra("qa_parallax", true)
        com.zikrtime.app.ui.LiveFlags.splash = intent.getBooleanExtra("qa_splash", false)
        hideScene = intent.getBooleanExtra("qa_hide_scene", false)
        skyPreview = intent.getStringExtra("qa_period")?.let { runCatching { com.zikrtime.app.ui.NaturalPeriod.valueOf(it) }.getOrNull() }
        val screen = intent.getStringExtra("qa_screen") ?: return false
        nav.stack.clear()
        nav.onboardingStep = 0
        if (intent.getBooleanExtra("qa_onboarded", false)) Store.onboarded = true
        if (intent.getBooleanExtra("qa_demo", false)) seed(activity)
        when (screen) {
            "onboarding" -> Store.onboarded = false
            "onboarding_welcome" -> {
                Store.onboarded = false
                nav.onboardingStep = 0
            }
            "onboarding_perms" -> {
                Store.onboarded = false
                nav.onboardingStep = 3
            }
            "onboarding_city" -> {
                Store.onboarded = false
                nav.onboardingStep = 2
            }
            "onboarding_language" -> { Store.onboarded = false; nav.onboardingStep = 1 }
            "onboarding_finish" -> { Store.onboarded = false; nav.onboardingStep = 4 }
            "home" -> nav.tab = Tab.PRAYER
            "knowledge" -> nav.tab = Tab.KNOWLEDGE
            "focus" -> nav.tab = Tab.FOCUS
            "more" -> nav.tab = Tab.MORE
            "language" -> nav.open(Screen.LANGUAGE)
            "city" -> nav.open(Screen.CITY)
            "prayer_settings" -> nav.open(Screen.PRAYER_SETTINGS)
            "alarm" -> nav.open(Screen.ALARM)
            "qibla" -> nav.open(Screen.QIBLA)
            "tasbih" -> nav.open(Screen.TASBIH)
            "about" -> nav.open(Screen.ABOUT)
            "apps" -> nav.open(Screen.APPS)
            "review" -> nav.startReview()
            "progress" -> nav.open(Screen.PROGRESS)
            "azkar" -> nav.openAzkar("morning")
            "quiz" -> nav.startQuiz(null)
            "quiz_cards" -> {
                nav.quizStartWithCards = true
                nav.startQuiz(null)
            }
            "shop" -> nav.tab = Tab.SHOP
            "widgets" -> widgetPreview = true
            "places" -> nav.open(Screen.PLACES)
            "ramadan" -> nav.open(Screen.RAMADAN)
            "zakat" -> nav.open(Screen.ZAKAT)
            "shop_seller" -> {
                nav.tab = Tab.SHOP
                nav.shopSeller = com.zikrtime.app.data.Shop.load(activity).sellers.firstOrNull()?.id ?: "demo_perfume"
                nav.open(Screen.SHOP_SELLER)
            }
            "shop_product" -> {
                nav.tab = Tab.SHOP
                nav.shopProduct = com.zikrtime.app.data.Shop.load(activity).products.firstOrNull()?.id ?: "p1"
                nav.open(Screen.SHOP_PRODUCT)
            }
            "block_limit" -> activity.startActivity(BlockActivity.intent(activity, BlockReason.LimitReached(42, 40)))
            "block_prayer" -> activity.startActivity(
                BlockActivity.intent(activity, BlockReason.PrayerTime(Prayer.DHUHR, LocalDate.now(), Instant.now().plusSeconds(12 * 60))),
            )
            "alarm_ring" -> activity.startActivity(AlarmActivity.intent(activity, test = true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return false
    }

    private fun seed(activity: Activity) {
        if (!Store.onboarded) {
            val moscow = Cities.load(activity).second.first { it.id == "moscow" }
            Store.setLocation(moscow.toCity(Lang.content(activity)))
        }
        Store.onboarded = true
        Store.blockingEnabled = true
        Store.appsInitialized = true
        Store.blockedPackages = setOf("com.android.chrome", "com.google.android.youtube")
        Store.alarmEnabled = true
        Store.setPrayed(LocalDate.now(), Prayer.FAJR, true)
        if (Store.tasbihCount == 0) repeat(21) { Store.addTasbih() }
    }
}
