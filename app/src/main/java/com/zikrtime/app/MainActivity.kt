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
import androidx.compose.ui.platform.testTag
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
import com.zikrtime.app.ui.AppGuideScreen
import com.zikrtime.app.ui.GuideTopic
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
    private var qaHandled = false
    private var lastReadingDelivery = Long.MIN_VALUE

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        nav.restore(savedInstanceState)
        lastReadingDelivery = savedInstanceState?.getLong("last_reading_delivery", Long.MIN_VALUE) ?: Long.MIN_VALUE
        // Названия каналов уведомлений — на текущем языке.
        PrayerAlarms.createChannels(this)
        qaHandled = savedInstanceState?.getBoolean("qa_handled") ?: false
        if (BuildConfig.DEBUG && !qaHandled) {
            // Language selection recreates before applying the requested QA route.
            // Rotation after that must preserve the reader instead of reopening its index.
            if (QaHooks.handle(this, intent, nav)) return
            qaHandled = true
        }
        handleOpen(intent)
        enableEdgeToEdge()
        val showSplash = savedInstanceState == null && com.zikrtime.app.ui.LiveFlags.splash &&
            (intent?.hasExtra("qa_screen") != true || intent?.getBooleanExtra("qa_splash", false) == true)
        setContent {
            val natural = Store.onboarded && (nav.stack.lastOrNull()?.screen in setOf(Screen.TASBIH, Screen.AZKAR, Screen.QURAN, Screen.QURAN_AUDIO, Screen.READING_BEDTIME, Screen.READING_FRIDAY, Screen.PROGRESS, Screen.REVIEW, Screen.TRAINING) || (nav.stack.isEmpty() && nav.tab == Tab.PRAYER))
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
        if (BuildConfig.DEBUG) {
            if (intent.hasExtra("qa_screen")) QaHooks.handle(this, intent, nav)
            else QuranAudioQa.captureIfRequested(this, intent)
        }
        handleOpen(intent)
    }

    private fun handleOpen(intent: Intent?) {
        if (intent != null && (intent.getBooleanExtra("open_quran_audio", false) || intent.action == "com.zikrtime.app.OPEN_QURAN_AUDIO")) {
            nav.openQuranAudio(intent.getIntExtra("quran_audio_surah", 0))
            intent.removeExtra("open_quran_audio")
            if (intent.action == "com.zikrtime.app.OPEN_QURAN_AUDIO") intent.action = null
            return
        }
        if (intent?.getBooleanExtra("open_focus", false) == true) { nav.stack.clear(); nav.tab = Tab.FOCUS }
        val reading = com.zikrtime.app.prayer.ReadingKind.entries.firstOrNull { it.route == intent?.getStringExtra(EXTRA_OPEN) }
        if (reading != null) {
            val delivery = intent?.getLongExtra("reading_delivery", Long.MIN_VALUE) ?: Long.MIN_VALUE
            if (delivery == Long.MIN_VALUE || delivery != lastReadingDelivery) nav.openReading(reading)
            lastReadingDelivery = delivery
            intent?.removeExtra(EXTRA_OPEN)
            return
        }
        when (intent?.getStringExtra(EXTRA_OPEN)) {
            com.zikrtime.app.prayer.PrayerCheckReminders.ROUTE -> {
                val day = runCatching { java.time.LocalDate.parse(intent?.getStringExtra(com.zikrtime.app.prayer.PrayerCheckReminders.EXTRA_DAY)) }.getOrNull()
                val prayer = com.zikrtime.app.prayer.Prayer.obligatory.firstOrNull {
                    it.name == intent?.getStringExtra(com.zikrtime.app.prayer.PrayerCheckReminders.EXTRA_PRAYER)
                }
                nav.openJournal(day?.toString(), prayer?.name)
                intent?.removeExtra(EXTRA_OPEN)
            }
            "prayer" -> { nav.stack.clear(); nav.tab = Tab.PRAYER }
            "city" -> { nav.stack.clear(); nav.tab = Tab.PRAYER; if (Store.onboarded) nav.open(Screen.CITY) }
            "alarm" -> nav.open(Screen.ALARM)
            "focus" -> { nav.stack.clear(); nav.tab = com.zikrtime.app.ui.Tab.FOCUS }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("qa_handled", qaHandled)
        outState.putLong("last_reading_delivery", lastReadingDelivery)
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
            com.zikrtime.app.prayer.ReadingReminders.reschedule(this)
            com.zikrtime.app.prayer.PrayerCheckReminders.reschedule(this)
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

    if (!Store.guideDismissed) {
        AppGuideScreen(firstRun = true, onFinish = { Store.guideDismissed = true }, onOpenTopic = {})
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
            Screen.TRAINING -> com.zikrtime.app.ui.TrainingScreen()
            Screen.PROGRESS -> com.zikrtime.app.ui.ProgressScreen()
            Screen.AZKAR -> com.zikrtime.app.ui.AzkarScreen()
            Screen.QURAN -> com.zikrtime.app.ui.QuranScreen(entry?.quranSurah ?: 0, entry?.quranAyah ?: 1)
            Screen.QURAN_AUDIO -> com.zikrtime.app.ui.QuranAudioScreen(entry?.audioSurah ?: 0)
            Screen.PRAYER_JOURNAL -> com.zikrtime.app.ui.PrayerJournalScreen(entry?.journalDay, entry?.journalPrayer)
            Screen.READING_REMINDERS -> com.zikrtime.app.ui.ReadingRemindersScreen()
            Screen.READING_BEDTIME -> com.zikrtime.app.ui.QuranScreen(2, 255)
            Screen.READING_FRIDAY -> com.zikrtime.app.ui.QuranScreen(18, 1)
            Screen.APPS -> AppsScreen()
            Screen.ABOUT -> AboutScreen()
            Screen.SHOP_SELLER -> com.zikrtime.app.ui.SellerScreen(entry?.seller)
            Screen.SHOP_PRODUCT -> com.zikrtime.app.ui.ProductScreen(entry?.product)
            Screen.PLACES -> com.zikrtime.app.ui.PlacesScreen()
            Screen.RAMADAN -> com.zikrtime.app.ui.RamadanScreen()
            Screen.ZAKAT -> com.zikrtime.app.ui.ZakatScreen()
            Screen.GUIDE -> AppGuideScreen(
                firstRun = false,
                onFinish = { nav.back() },
                onOpenTopic = { topic ->
                    when (topic) {
                        GuideTopic.WELCOME -> nav.openGuideTab(Tab.PRAYER)
                        GuideTopic.PRAYER -> nav.open(Screen.PRAYER_SETTINGS)
                        GuideTopic.JOURNAL -> nav.open(Screen.PRAYER_JOURNAL)
                        GuideTopic.QURAN -> nav.open(Screen.QURAN)
                        GuideTopic.LIMITS, GuideTopic.PERMISSIONS -> nav.openGuideTab(Tab.FOCUS)
                        GuideTopic.KNOWLEDGE -> nav.openGuideTab(Tab.KNOWLEDGE)
                        GuideTopic.PLACES -> nav.open(Screen.PLACES)
                        GuideTopic.TOOLS -> nav.openGuideTab(Tab.MORE)
                        GuideTopic.SHOP -> nav.openGuideTab(Tab.SHOP)
                    }
                },
            )
            Screen.GUIDE_DESTINATION -> Column(Modifier.fillMaxSize()) {
                Surface {
                    androidx.compose.material3.TextButton(
                        onClick = { nav.back() },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 12.dp)
                            .semantics { testTagsAsResourceId = true }
                            .testTag("guide_return"),
                    ) { Text(stringResource(R.string.guide_return)) }
                }
                Box(Modifier.weight(1f)) { Tabs() }
            }
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
        ) { (tab, icon, label), selected ->
            val color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            val title = when (tab) {
                Tab.FOCUS -> com.zikrtime.app.ui.clarityCopy(com.zikrtime.app.ui.UiClarity0825.Limits)
                Tab.MORE -> com.zikrtime.app.ui.clarityCopy(com.zikrtime.app.ui.UiClarity0825.Settings)
                else -> stringResource(label)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    minLines = if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) 2 else 1,
                    maxLines = 2,
                )
            }
        }
    }
}
