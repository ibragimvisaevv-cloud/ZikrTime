package com.zikrtime.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.zikrtime.app.BuildConfig
import com.zikrtime.app.R
import com.zikrtime.app.alarm.FajrAlarm
import com.zikrtime.app.data.Cities
import com.zikrtime.app.data.City
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.prayer.AdhanSound
import com.zikrtime.app.prayer.AsrMadhab
import com.zikrtime.app.prayer.CalcMethod
import com.zikrtime.app.prayer.OnlineTimes
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.prayer.PrayerAlarms
import com.zikrtime.app.prayer.PrayerSchedule
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Вкладка «Ещё»: все настройки и дополнительные функции. */
@Composable
fun MoreScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    // Ссылку «Поддержать проект» берём из списка мест (обновляется не чаще раза в минуту).
    androidx.compose.runtime.LaunchedEffect(Unit) { com.zikrtime.app.data.Places.refresh(context) }
    val current = Lang.saved(context)
    val languageName = Lang.supported.firstOrNull { it.code == current }?.nativeName ?: stringResource(R.string.language_system)

    HorizonSection(title = stringResource(R.string.more_title),
        subtitle = stringResource(R.string.app_name) + " · " + BuildConfig.VERSION_NAME,
        image = if (com.zikrtime.app.ui.theme.LocalDarkTheme.current) R.drawable.horizon_portrait_night else R.drawable.horizon_portrait_day,
        heroHeight = 260.dp,
        hero = {
            Spacer(Modifier.height(8.dp))
            AppearanceSegments()
        }) {
        Text(stringResource(R.string.group_practice), style = MaterialTheme.typography.headlineSmall)
        val destinations = listOf(
            Triple(R.string.quran_title, R.drawable.ic_nav_knowledge, Screen.QURAN),
            Triple(R.string.tasbih_title, R.drawable.ic_tasbih, Screen.TASBIH),
            Triple(R.string.qibla_title, R.drawable.ic_qibla, Screen.QIBLA),
            Triple(R.string.places_title, R.drawable.ic_mosque, Screen.PLACES),
            Triple(R.string.ramadan_title, R.drawable.ic_prayer_isha, Screen.RAMADAN),
            Triple(R.string.zakat_title, R.drawable.ic_calc, Screen.ZAKAT),
            Triple(R.string.progress_title, R.drawable.ic_nav_knowledge, Screen.PROGRESS),
        )
        val columns = if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) 1 else 2
        destinations.chunked(columns).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (title, icon, screen) ->
                    HorizonDestination(stringResource(title), icon, Modifier.weight(1f), prominent = screen == Screen.TASBIH) { nav.open(screen) }
                }
            }
        }
        SectionLabel(stringResource(R.string.group_application))
        ZCard(padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            SettingRow(
                title = stringResource(R.string.settings_language),
                subtitle = languageName,
                icon = painterResource(R.drawable.ic_language),
                onClick = { nav.open(Screen.LANGUAGE) },
            )
            SettingRow(
                title = stringResource(R.string.settings_city),
                subtitle = Cities.displayName(context, Store.location),
                icon = rememberVectorPainter(Icons.Default.LocationOn),
                onClick = { nav.open(Screen.CITY) },
            )
            SettingRow(
                title = stringResource(R.string.settings_prayer_times),
                subtitle = methodName(Store.method),
                icon = painterResource(R.drawable.ic_calc),
                onClick = { nav.open(Screen.PRAYER_SETTINGS) },
            )
            SettingRow(
                title = stringResource(R.string.alarm_title),
                subtitle = if (Store.alarmEnabled) stringResource(R.string.alarm_status_on, Store.alarmOffsetMinutes)
                else stringResource(R.string.state_off),
                icon = painterResource(R.drawable.ic_alarm),
                onClick = { nav.open(Screen.ALARM) },
            )
        }
        SectionLabel(stringResource(R.string.group_data_help))
        ZCard {
            PersonalProgressSetting()
            TextButton(onClick = { nav.open(Screen.PROGRESS) }) {
                Text(stringResource(R.string.progress_settings))
            }
        }
        DonateCard()
        ZCard(padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            SettingRow(
                title = stringResource(R.string.about_title),
                subtitle = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                icon = rememberVectorPainter(Icons.Default.Info),
                onClick = { nav.open(Screen.ABOUT) },
            )
        }
    }
}

// ---------------- Язык ----------------

@Composable
fun LanguageScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val activity = context as? Activity
    val current = Lang.saved(context)
    ScreenScaffold(title = stringResource(R.string.settings_language), onBack = { nav.back() }) {
        ZCard(padding = PaddingValues(vertical = 6.dp, horizontal = 8.dp)) {
            LanguageList(current = current, onPick = { code -> activity?.let { Lang.set(it, code) } })
        }
        Text(
            stringResource(R.string.language_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LanguageList(current: String, onPick: (String) -> Unit, includeSystem: Boolean = true) {
    val options = buildList {
        if (includeSystem) add("" to stringResource(R.string.language_system))
        Lang.supported.forEach { add(it.code to it.nativeName) }
    }
    options.forEach { (code, name) ->
        RadioLine(text = name, selected = current == code, onClick = { onPick(code) })
    }
}

@Composable
fun RadioLine(text: String, selected: Boolean, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .7f) else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 52.dp).padding(vertical = 6.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------- Город ----------------

@Composable
fun CityScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ScreenScaffold(title = stringResource(R.string.settings_city), onBack = { nav.back() }, scene = R.drawable.horizon_dawn) {
        CityPicker(onPicked = { city ->
            Store.setLocation(city)
            PrayerAlarms.scheduleNext(context)
            FajrAlarm.schedule(context)
            scope.launch {
                OnlineTimes.refresh()
                PrayerAlarms.scheduleNext(context)
                FajrAlarm.schedule(context)
            }
            nav.back()
        })
    }
}

/** Выбор города: геолокация, поиск и список по странам. Используется и при первом запуске. */
@Composable
fun CityPicker(onPicked: (City) -> Unit) {
    val context = LocalContext.current
    val lang = Lang.content(context)
    val (groups, cities) = remember { Cities.load(context) }
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val noLocation = stringResource(R.string.city_locate_failed)
    val noPermission = stringResource(R.string.city_locate_denied)
    val myLocation = stringResource(R.string.city_my_location)

    fun locate() {
        locating = true
        error = null
        LocationHelper.requestOnce(context) { loc ->
            if (loc == null) {
                locating = false
                error = noLocation
            } else {
                LocationHelper.placeName(context, loc.latitude, loc.longitude) { name ->
                    locating = false
                    onPicked(City(null, name ?: myLocation, loc.latitude, loc.longitude))
                }
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else error = noPermission
    }

    TonalButton(
        text = if (locating) stringResource(R.string.city_locating) else stringResource(R.string.city_locate),
        enabled = !locating,
        onClick = {
            if (LocationHelper.hasPermission(context)) locate() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        },
    )
    error?.let { StatePanel(it, icon = R.drawable.ic_pin, error = true) }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        placeholder = { Text(stringResource(R.string.city_search)) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )

    val q = query.trim()
    if (q.isNotEmpty() && cities.none { c -> c.names.values.any { it.contains(q, ignoreCase = true) } }) {
        StatePanel(stringResource(R.string.search_nothing), icon = R.drawable.ic_pin,
            action = stringResource(R.string.action_reset), onAction = { query = "" })
    }
    val currentId = Store.location.id
    groups.forEach { group ->
        val list = cities.filter { it.group == group.id }.filter { c ->
            q.isEmpty() || c.names.values.any { it.contains(q, ignoreCase = true) }
        }
        if (list.isNotEmpty()) {
            SectionLabel(group.names[lang] ?: group.names.getValue("en"))
            ZCard(padding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)) {
                list.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPicked(c.toCity(lang)) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.names[lang] ?: c.names.getValue("en"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (c.id == currentId) {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Время намаза ----------------

@Composable
fun PrayerSettingsScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val resume = AppEvents.resumeCount
    val notificationsAllowed = remember(resume) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exactAllowed = remember(resume) { PrayerAlarms.canScheduleExact(context) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { AppEvents.resumeCount++ }

    fun reload(force: Boolean = false) {
        PrayerAlarms.scheduleNext(context)
        FajrAlarm.schedule(context)
        scope.launch {
            refreshing = true
            OnlineTimes.refresh(force)
            refreshing = false
            PrayerAlarms.scheduleNext(context)
            FajrAlarm.schedule(context)
        }
    }

    ScreenScaffold(title = stringResource(R.string.settings_prayer_times), onBack = { nav.back() }) {
        SectionLabel(stringResource(R.string.prayer_method))
        ZCard(padding = PaddingValues(vertical = 6.dp, horizontal = 8.dp)) {
            CalcMethod.entries.forEach { m ->
                RadioLine(
                    text = methodName(m),
                    subtitle = if (m == CalcMethod.MOSCOW_CATHEDRAL) stringResource(R.string.method_moscow_note)
                    else m.ishaAngle?.let { stringResource(R.string.method_angles, fmtAngle(m.fajrAngle), fmtAngle(it)) }
                        ?: stringResource(R.string.method_angles_minutes, fmtAngle(m.fajrAngle), m.ishaMinutes ?: 0),
                    selected = Store.method == m,
                    onClick = {
                        Store.method = m
                        reload()
                    },
                )
            }
        }
        Text(stringResource(R.string.prayer_method_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionLabel(stringResource(R.string.prayer_asr_section))
        ZCard(padding = PaddingValues(vertical = 6.dp, horizontal = 8.dp)) {
            AsrMadhab.entries.forEach { m ->
                RadioLine(text = madhabName(m), selected = Store.madhab == m, onClick = {
                    Store.madhab = m
                    reload()
                })
            }
        }

        SectionLabel(stringResource(R.string.prayer_source))
        ZCard {
            SwitchRow(
                title = stringResource(R.string.prayer_source_online),
                subtitle = stringResource(R.string.prayer_source_online_hint),
                checked = Store.onlineTimes,
                onChange = {
                    Store.onlineTimes = it
                    reload()
                },
            )
            if (Store.onlineTimes) {
                val updated = Store.onlineUpdatedAt
                val online = remember(OnlineTimes.version, Store.location, Store.method, Store.madhab) { PrayerSchedule.isOnline() }
                val locale = Lang.locale(context)
                Text(
                    when {
                        refreshing -> stringResource(R.string.prayer_source_loading)
                        online && updated > 0 -> stringResource(
                            R.string.prayer_source_updated,
                            Instant.ofEpochMilli(updated).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMMM, HH:mm", locale)),
                        )
                        else -> stringResource(R.string.prayer_source_failed)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
                TextButton(onClick = { reload(force = true) }, enabled = !refreshing) { Text(stringResource(R.string.action_refresh)) }
            }
        }

        SectionLabel(stringResource(R.string.prayer_notifications))
        ZCard {
            SwitchRow(
                title = stringResource(R.string.prayer_notify),
                checked = Store.prayerNotifications,
                icon = rememberVectorPainter(Icons.Default.Notifications),
                onChange = {
                    Store.prayerNotifications = it
                    PrayerAlarms.scheduleNext(context)
                },
            )
            if (Store.prayerNotifications && AdhanSound.available(context)) {
                SwitchRow(
                    title = stringResource(R.string.prayer_notify_adhan),
                    subtitle = stringResource(R.string.prayer_notify_adhan_hint),
                    checked = Store.notificationAdhan,
                    onChange = { Store.notificationAdhan = it },
                )
                if (Store.notificationAdhan) {
                    Text(
                        stringResource(R.string.prayer_notify_voice),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                    AdhanVoiceList(selected = Store.notificationVoice, onSelect = { Store.notificationVoice = it }, includeSystem = false)
                }
            }
            SwitchRow(
                title = stringResource(R.string.friday_notify),
                subtitle = stringResource(R.string.friday_notify_hint),
                checked = Store.fridayReminders,
                onChange = {
                    Store.fridayReminders = it
                    com.zikrtime.app.prayer.FridayReminders.schedule(context)
                },
            )
            if (Store.prayerNotifications && !notificationsAllowed) {
                Text(stringResource(R.string.perm_notifications_off), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text(stringResource(R.string.action_allow)) }
            }
            if (!exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Text(stringResource(R.string.perm_exact_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text(stringResource(R.string.action_allow)) }
            }
        }

        SectionLabel(stringResource(R.string.prayer_corrections))
        ZCard(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                stringResource(R.string.prayer_corrections_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Prayer.entries.forEach { p ->
                val adj = Store.adjustment(p)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(prayerName(p), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        Store.setAdjustment(p, adj - 1)
                        PrayerAlarms.scheduleNext(context)
                        FajrAlarm.schedule(context)
                    }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    Text(
                        if (adj > 0) "+$adj" else "$adj",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = {
                        Store.setAdjustment(p, adj + 1)
                        PrayerAlarms.scheduleNext(context)
                        FajrAlarm.schedule(context)
                    }) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
            }
        }
    }
}

private fun fmtAngle(a: Double): String = if (a % 1.0 == 0.0) a.toInt().toString() else a.toString()

// ---------------- О приложении ----------------

@Composable
fun AboutScreen() {
    val nav = LocalNav.current
    ScreenScaffold(title = stringResource(R.string.about_title), onBack = { nav.back() }, scene = R.drawable.azkar_morning) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AppIconImage(Modifier.size(96.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.slogan), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AboutBlock(stringResource(R.string.about_privacy_title), stringResource(R.string.about_privacy_text))
        AboutBlock(stringResource(R.string.about_times_title), stringResource(R.string.about_times_text))
        AboutBlock(stringResource(R.string.about_quiz_title), stringResource(R.string.about_quiz_text))
        AboutBlock(stringResource(R.string.about_licenses_title), stringResource(R.string.about_licenses_text))
    }
}

@Composable
private fun AboutBlock(title: String, text: String) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LoadingBox() {
    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
}

/** Иконка приложения из двух слоёв (фон и рисунок) — painterResource не умеет адаптивные иконки. */
@Composable
fun AppIconImage(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier.clip(RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // Слои 108×108, видимая часть — центральные 72×72, поэтому увеличиваем в 1,5 раза.
        Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.fillMaxSize().scale(1.5f))
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize().scale(1.5f))
    }
}
