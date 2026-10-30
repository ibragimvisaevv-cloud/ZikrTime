@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.zikrtime.app

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
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
        nav.restore(savedInstanceState)
        // Названия каналов уведомлений — на текущем языке.
        PrayerAlarms.createChannels(this)
        if (BuildConfig.DEBUG && savedInstanceState == null && QaHooks.handle(this, intent, nav)) return
        handleOpen(intent)
        enableEdgeToEdge()
        val showSplash = savedInstanceState == null && com.zikrtime.app.ui.LiveFlags.splash &&
            (intent?.hasExtra("qa_screen") != true || intent?.getBooleanExtra("qa_splash", false) == true)
        setContent {
            val natural = Store.onboarded && (nav.stack.lastOrNull()?.screen in setOf(Screen.TASBIH, Screen.AZKAR, Screen.QURAN, Screen.PROGRESS, Screen.REVIEW) || (nav.stack.isEmpty() && nav.tab == Tab.PRAYER))
            com.zikrtime.app.ui.ThemeRevealHost {
                ZikrTheme(natural = natural) {
                    val tilt = com.zikrtime.app.ui.rememberTilt(enabled = false)
                    var splash by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(showSplash) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(650)
                        splash = false
                    }
                    CompositionLocalProvider(com.zikrtime.app.ui.LocalTilt provides tilt) {
                        Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                            // Переливающийся фон под всеми экранами
                            com.zikrtime.app.ui.HorizonBackground()
                            CompositionLocalProvider(LocalNav provides nav) {
                                if (BuildConfig.DEBUG && QaHooks.widgetPreview) com.zikrtime.app.ui.WidgetPreview() else AppRoot()
                            }
                            // Заставка: полумесяц со звездой
                            androidx.compose.animation.AnimatedVisibility(
                                visible = splash,
                                enter = fadeIn(androidx.compose.animation.core.tween(0)),
                                exit = fadeOut(androidx.compose.animation.core.tween(if (com.zikrtime.app.ui.motionEnabled()) 380 else 0)),
                            ) {
                                com.zikrtime.app.ui.HorizonSplash()
                            }
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
            "prayer" -> { nav.stack.clear(); nav.tab = Tab.PRAYER }
            "city" -> { nav.stack.clear(); nav.tab = Tab.PRAYER; if (Store.onboarded) nav.open(Screen.CITY) }
            "alarm" -> nav.open(Screen.ALARM)
            "focus" -> { nav.stack.clear(); nav.tab = com.zikrtime.app.ui.Tab.FOCUS }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        nav.save(outState)
        super.onSaveInstanceState(outState)
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
        })
        return
    }

    val motion = com.zikrtime.app.ui.motionEnabled()
    val screensState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    BackHandler(enabled = nav.stack.isNotEmpty()) { nav.back() }
    AnimatedContent(
        targetState = nav.stack.lastOrNull(),
        transitionSpec = {
            val forward = (targetState?.id ?: 0) > (initialState?.id ?: 0)
            val duration = if (motion) 280 else 0
            (fadeIn(androidx.compose.animation.core.tween(duration)) +
                androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(duration)) { if (motion) it / (if (forward) 8 else -12) else 0 }) togetherWith
                (fadeOut(androidx.compose.animation.core.tween(if (motion) 160 else 0)) +
                    androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(duration)) { if (motion) it / (if (forward) -12 else 8) else 0 })
        },
        label = "screens",
    ) { entry ->
        val screen = entry?.screen
        screensState.SaveableStateProvider(if (screen == Screen.QUIZ || screen == Screen.REVIEW) "${entry?.id}-${nav.quizRound}" else entry?.id?.toString() ?: "tabs") {
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
            Screen.QURAN -> com.zikrtime.app.ui.QuranScreen()
            Screen.APPS -> AppsScreen()
            Screen.ABOUT -> AboutScreen()
            Screen.SHOP_SELLER -> com.zikrtime.app.ui.SellerScreen(entry?.seller)
            Screen.SHOP_PRODUCT -> com.zikrtime.app.ui.ProductScreen(entry?.product)
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
    val motion = com.zikrtime.app.ui.motionEnabled()
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
                (fadeIn(androidx.compose.animation.core.tween(if (motion) 260 else 0)) + androidx.compose.animation.scaleIn(initialScale = if (motion) 0.985f else 1f)) togetherWith
                    fadeOut(androidx.compose.animation.core.tween(if (motion) 160 else 0))
            },
            label = "tabs",
        ) { tab ->
            tabState.SaveableStateProvider(tab.name) {
            Box(Modifier.fillMaxSize()) {
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
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to androidx.compose.ui.graphics.Color.Transparent,
                    .35f to MaterialTheme.colorScheme.background.copy(alpha = .96f),
                    1f to MaterialTheme.colorScheme.background))
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
