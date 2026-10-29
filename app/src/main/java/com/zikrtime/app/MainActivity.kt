package com.zikrtime.app

import androidx.compose.foundation.background
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.zikrtime.app.alarm.AlarmActivity
import com.zikrtime.app.alarm.FajrAlarm
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.prayer.OnlineTimes
import com.zikrtime.app.prayer.PrayerAlarms
import com.zikrtime.app.screentime.GuardService
import com.zikrtime.app.ui.AboutScreen
import com.zikrtime.app.ui.AlarmSettingsScreen
import com.zikrtime.app.ui.AppEvents
import com.zikrtime.app.ui.AppsScreen
import com.zikrtime.app.ui.CityScreen
import com.zikrtime.app.ui.FocusScreen
import com.zikrtime.app.ui.HomeScreen
import com.zikrtime.app.ui.KnowledgeScreen
import com.zikrtime.app.ui.LanguageScreen
import com.zikrtime.app.ui.LocalNav
import com.zikrtime.app.ui.MoreScreen
import com.zikrtime.app.ui.Nav
import com.zikrtime.app.ui.OnboardingScreen
import com.zikrtime.app.ui.PrayerSettingsScreen
import com.zikrtime.app.ui.QiblaScreen
import com.zikrtime.app.ui.QuizScreen
import com.zikrtime.app.ui.Screen
import com.zikrtime.app.ui.Tab
import com.zikrtime.app.ui.TasbihScreen
import com.zikrtime.app.ui.theme.ZikrTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val nav = Nav()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        // Названия каналов уведомлений — на текущем языке.
        PrayerAlarms.createChannels(this)
        if (BuildConfig.DEBUG && QaHooks.handle(this, intent, nav)) return
        handleOpen(intent)
        enableEdgeToEdge()
        val showSplash = savedInstanceState == null && com.zikrtime.app.ui.LiveFlags.splash &&
            (intent?.hasExtra("qa_screen") != true || intent?.getBooleanExtra("qa_splash", false) == true)
        setContent {
            val natural = Store.onboarded && (nav.stack.lastOrNull() in setOf(Screen.TASBIH, Screen.AZKAR, Screen.PROGRESS, Screen.REVIEW) || (nav.stack.isEmpty() && nav.tab == Tab.PRAYER))
            ZikrTheme(natural = natural) {
                val tilt = com.zikrtime.app.ui.rememberTilt(enabled = false)
                var splash by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(showSplash) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1_150)
                    splash = false
                }
                CompositionLocalProvider(com.zikrtime.app.ui.LocalTilt provides tilt) {
                    Box(Modifier.fillMaxSize()) {
                        // Переливающийся фон под всеми экранами
                        com.zikrtime.app.ui.HorizonBackground()
                        CompositionLocalProvider(LocalNav provides nav) {
                            if (BuildConfig.DEBUG && QaHooks.widgetPreview) com.zikrtime.app.ui.WidgetPreview() else AppRoot()
                        }
                        // Заставка: полумесяц со звездой
                        androidx.compose.animation.AnimatedVisibility(
                            visible = splash,
                            enter = fadeIn(androidx.compose.animation.core.tween(0)),
                            exit = fadeOut(androidx.compose.animation.core.tween(380)) +
                                androidx.compose.animation.scaleOut(targetScale = 1.08f),
                        ) {
                            com.zikrtime.app.ui.CrescentSplash()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    private fun handleOpen(intent: Intent?) {
        if (intent?.getBooleanExtra("open_focus", false) == true) { nav.stack.clear(); nav.tab = Tab.FOCUS }
        when (intent?.getStringExtra(EXTRA_OPEN)) {
            "alarm" -> nav.open(Screen.ALARM)
            "focus" -> nav.tab = com.zikrtime.app.ui.Tab.FOCUS
        }
    }

    override fun onResume() {
        super.onResume()
        // Экраны перепроверят разрешения — пользователь мог вернуться из системных настроек.
        AppEvents.resumeCount++
        if (Store.onboarded) {
            PrayerAlarms.scheduleNext(this)
            FajrAlarm.schedule(this)
            com.zikrtime.app.prayer.FridayReminders.schedule(this)
            GuardService.sync(this)
            com.zikrtime.app.widget.Widgets.updateAll(this)
            lifecycleScope.launch {
                // Время из интернета: если загрузилось новое — переставляем будильники.
                if (OnlineTimes.refresh()) {
                    PrayerAlarms.scheduleNext(this@MainActivity)
                    FajrAlarm.schedule(this@MainActivity)
                    com.zikrtime.app.widget.Widgets.updateAll(this@MainActivity)
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN = "open"
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    if (!Store.onboarded) {
        OnboardingScreen(onCityChosen = { city ->
            Store.setLocation(city)
            Store.onboarded = true
            PrayerAlarms.scheduleNext(context)
            scope.launch { if (OnlineTimes.refresh()) PrayerAlarms.scheduleNext(context) }
            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        })
        return
    }

    val screensState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    BackHandler(enabled = nav.stack.isNotEmpty()) { nav.back() }
    AnimatedContent(
        targetState = nav.stack.lastOrNull(),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screens",
    ) { screen ->
        screensState.SaveableStateProvider(when (screen) {
            Screen.QUIZ, Screen.REVIEW -> "${screen.name}-${nav.quizRound}"
            Screen.SHOP_PRODUCT -> "product-${nav.shopProduct}"
            Screen.SHOP_SELLER -> "seller-${nav.shopSeller}"
            else -> screen?.name ?: "tabs"
        }) {
        when (screen) {
            null -> Tabs()
            Screen.LANGUAGE -> LanguageScreen()
            Screen.CITY -> CityScreen()
            Screen.PRAYER_SETTINGS -> PrayerSettingsScreen()
            Screen.ALARM -> AlarmSettingsScreen()
            Screen.QIBLA -> QiblaScreen()
            Screen.TASBIH -> TasbihScreen()
            Screen.QUIZ -> QuizScreen()
            Screen.REVIEW -> com.zikrtime.app.ui.ReviewScreen()
            Screen.PROGRESS -> com.zikrtime.app.ui.ProgressScreen()
            Screen.AZKAR -> com.zikrtime.app.ui.AzkarScreen()
            Screen.APPS -> AppsScreen()
            Screen.ABOUT -> AboutScreen()
            Screen.SHOP_SELLER -> com.zikrtime.app.ui.SellerScreen()
            Screen.SHOP_PRODUCT -> com.zikrtime.app.ui.ProductScreen()
            Screen.PLACES -> com.zikrtime.app.ui.PlacesScreen()
            Screen.RAMADAN -> com.zikrtime.app.ui.RamadanScreen()
            Screen.ZAKAT -> com.zikrtime.app.ui.ZakatScreen()
        }
        }
    }
}

@Composable
private fun Tabs() {
    val nav = LocalNav.current
    val tabState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val items = listOf(
        Triple(Tab.PRAYER, R.drawable.ic_nav_prayer, R.string.tab_prayer),
        Triple(Tab.KNOWLEDGE, R.drawable.ic_nav_knowledge, R.string.tab_knowledge),
        Triple(Tab.SHOP, R.drawable.ic_nav_shop, R.string.tab_shop),
        Triple(Tab.FOCUS, R.drawable.ic_nav_focus, R.string.tab_focus),
        Triple(Tab.MORE, R.drawable.ic_nav_more, R.string.tab_more),
    )
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = nav.tab,
            transitionSpec = {
                (fadeIn(androidx.compose.animation.core.tween(260)) + androidx.compose.animation.scaleIn(initialScale = 0.985f)) togetherWith
                    fadeOut(androidx.compose.animation.core.tween(160))
            },
            label = "tabs",
        ) { tab ->
            tabState.SaveableStateProvider(tab.name) {
            Box(Modifier.fillMaxSize().then(if (tab == Tab.PRAYER) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBars))) {
                when (tab) {
                    Tab.PRAYER -> HomeScreen()
                    Tab.KNOWLEDGE -> KnowledgeScreen()
                    Tab.SHOP -> com.zikrtime.app.ui.ShopScreen()
                    Tab.FOCUS -> FocusScreen()
                    Tab.MORE -> MoreScreen()
                }
            }
            }
        }
        com.zikrtime.app.ui.GlassNavBar(
            items = items,
            selected = items.first { it.first == nav.tab },
            onSelect = { nav.tab = it.first },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 0.dp, vertical = 0.dp),
        ) { (_, icon, label), selected ->
            val color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painterResource(icon),
                    contentDescription = stringResource(label),
                    tint = color,
                    modifier = Modifier.size(24.dp),
                )
                if (androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.3f) Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}
