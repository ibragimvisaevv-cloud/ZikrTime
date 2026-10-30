@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.zikrtime.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.graphics.drawable.toBitmap
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.screentime.AppInfo
import com.zikrtime.app.screentime.FocusPause
import com.zikrtime.app.screentime.BlockerService
import com.zikrtime.app.screentime.GuardService
import com.zikrtime.app.screentime.ScreenTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Загруженный список приложений (общий для вкладки и экрана выбора). */
object AppsCache {
    var apps by mutableStateOf<List<AppInfo>?>(null)
}

@Composable
fun FocusScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val resume = AppEvents.resumeCount
    val hasUsage = remember(resume) { ScreenTime.hasUsageAccess(context) }
    val hasService = remember(resume) { ScreenTime.isAccessibilityEnabled(context) }
    val hasOverlay = remember(resume) { GuardService.canDrawOverlays(context) }
    val canBlock = hasService || hasOverlay
    val pauseRevision = FocusPause.version
    val hasPauseNotifications = remember(resume,pauseRevision) { FocusPause.notificationsAvailable(context) }
    val pauseEnabled = remember(pauseRevision) { FocusPause.enabled(context) }
    val pauseStrict = remember(pauseRevision) { FocusPause.strict(context) }
    val pausePhrase = remember(pauseRevision) { FocusPause.phrase(context) }
    val running = BlockerService.running || GuardService.running
    var showDisclosure by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(resume, pauseEnabled) { GuardService.sync(context) }
    LaunchedEffect(Unit) {
        if (AppsCache.apps == null) {
            val loaded = withContext(Dispatchers.IO) { ScreenTime.launchableApps(context) }
            ScreenTime.ensureDefaultSelection(loaded)
            AppsCache.apps = loaded
        }
    }
    var usedMillis by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(resume, Store.blockedPackages) {
        usedMillis = withContext(Dispatchers.IO) { ScreenTime.usedTodayMillis(context) }
    }

    val used = (usedMillis ?: 0L) / 60_000
    val allowed = Store.allowedMinutes()
    val remaining = (allowed - used).coerceAtLeast(0)
    HorizonSection(title = stringResource(R.string.focus_title), subtitle = stringResource(R.string.focus_subtitle),
        image = R.drawable.focus_courtyard, heroHeight = 390.dp,
        hero = {
            Spacer(Modifier.height(8.dp))
            ProgressRing(progress = if (usedMillis == null) 0f else remaining.toFloat() / allowed.coerceAtLeast(1),
                modifier = Modifier.align(Alignment.CenterHorizontally).size(if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) 246.dp else 188.dp),
                stroke = 3.dp, color = Sand, track = Porcelain.copy(alpha = .24f)) {
                if (!hasUsage) Icon(painterResource(R.drawable.ic_nav_focus), null,
                    tint = Sand, modifier = Modifier.size(52.dp))
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (usedMillis == null) "—" else remaining.toString(), color = Porcelain,
                        style = MaterialTheme.typography.displayLarge, fontSize = 64.sp)
                    Text(stringResource(R.string.unit_min), color = Sand, style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(if (!hasUsage || usedMillis == null) stringResource(R.string.focus_no_access) else stringResource(R.string.focus_remaining),
                style = MaterialTheme.typography.titleMedium, color = Porcelain,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (!hasUsage) GoldButton(stringResource(R.string.pause_settings), onClick = { showSettings = true },
                modifier = Modifier.testTag("focus_setup").semantics { testTagsAsResourceId = true })
        }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.focus_daily_limit), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(durationText(Store.dailyLimitMinutes.toLong()), style = MaterialTheme.typography.headlineSmall)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.focus_today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (usedMillis == null) "—" else durationText(used), style = MaterialTheme.typography.headlineSmall)
            }
        }
        if (Store.bonusMinutes() > 0) Text(
            stringResource(R.string.focus_limit_bonus, durationText(Store.dailyLimitMinutes.toLong()), Store.bonusMinutes()),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SwitchRow(title = stringResource(R.string.pause_title),
            subtitle = stringResource(if (pauseStrict) R.string.pause_strict else R.string.pause_soft),
            checked = pauseEnabled, onChange = { FocusPause.setEnabled(context, it) })
        SwitchRow(title = stringResource(R.string.focus_enable), subtitle = stringResource(R.string.focus_enable_hint),
            checked = Store.blockingEnabled, onChange = {
                Store.blockingEnabled = it; GuardService.sync(context); BlockerService.recheck()
            })
        if (!hasUsage || !canBlock) StatusLine(stringResource(R.string.focus_need_permissions))
        PrimaryButton(stringResource(R.string.pause_settings), onClick = { showSettings = true },
            modifier = Modifier.testTag("focus_settings_open").semantics { testTagsAsResourceId = true })
        DestinationRow(stringResource(R.string.focus_apps), R.drawable.ic_nav_more,
            AppsCache.apps?.filter { it.packageName in Store.blockedPackages }?.joinToString { it.label }
                ?.takeIf { it.isNotBlank() } ?: stringResource(R.string.focus_apps_none)) { nav.open(Screen.APPS) }
        if (Store.bonusMinutes() < Store.MAX_BONUS_MINUTES_PER_DAY) {
            TonalButton(stringResource(R.string.focus_earn_button, Store.BONUS_MINUTES_PER_ROUND), onClick = { nav.startQuiz(null) })
        }
    }
    if (showSettings) HorizonSheet(stringResource(R.string.pause_settings), { showSettings = false }) {
        if (!hasUsage) ZCard {
            PermissionRow(title = stringResource(R.string.perm_usage_title),
                text = stringResource(R.string.perm_usage_text), granted = false,
                onGrant = { ScreenTime.openUsageAccessSettings(context) })
        }
        SectionLabel(stringResource(R.string.pause_title))
        ZCard {
            SwitchRow(
                title = stringResource(R.string.pause_title),
                subtitle = stringResource(R.string.pause_enable_hint),
                checked = pauseEnabled,
                onChange = { FocusPause.setEnabled(context,it) },
            )
            // Both modes are explained before opt-in, not after intercepting another app.
            Text(stringResource(R.string.pause_soft_hint),style=MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.pause_strict_hint),style=MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(when {
                    !pauseEnabled -> R.string.pause_inactive
                    !hasUsage || !running || FocusPause.deliveryFailed -> R.string.pause_unavailable
                    else -> R.string.pause_running
                }),
                style=MaterialTheme.typography.bodySmall,
                color=if(pauseEnabled && (!hasUsage || !running || FocusPause.deliveryFailed)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pauseEnabled) {
                FilterChip(selected=!pauseStrict,onClick={FocusPause.setStrict(context,false)},label={Text(stringResource(R.string.pause_soft))})
                FilterChip(selected=pauseStrict,onClick={FocusPause.setStrict(context,true)},label={Text(stringResource(R.string.pause_strict))})
                Text(stringResource(R.string.pause_zikr),style=MaterialTheme.typography.titleSmall)
                listOf(R.string.zikr_1_name,R.string.zikr_2_name,R.string.zikr_3_name).forEachIndexed { index,label ->
                    FilterChip(selected=pausePhrase==index,onClick={FocusPause.setPhrase(context,index)},label={Text(stringResource(label))})
                }
                Text(stringResource(R.string.pause_foreground),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if (!hasPauseNotifications) {
                    Text(stringResource(R.string.perm_notifications_off),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                    TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}) { Text(stringResource(R.string.pause_settings)) }
                }
            }
        }

        ZCard {
            SwitchRow(
                title = stringResource(R.string.focus_enable),
                subtitle = stringResource(R.string.focus_enable_hint),
                checked = Store.blockingEnabled,
                onChange = {
                    Store.blockingEnabled = it
                    GuardService.sync(context)
                    BlockerService.recheck()
                },
            )
            if (Store.blockingEnabled && !(hasUsage && canBlock)) {
                Text(
                    stringResource(R.string.focus_need_permissions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
        }

        SectionLabel(stringResource(R.string.focus_daily_limit))
        ZCard {
            SliderRow(
                title = stringResource(R.string.focus_daily_limit),
                valueText = durationText(Store.dailyLimitMinutes.toLong()),
                value = Store.dailyLimitMinutes,
                range = 10..180,
                step = 5,
                onChange = { Store.dailyLimitMinutes = it },
            )
            Text(
                stringResource(R.string.focus_daily_limit_hint, Store.MAX_BONUS_MINUTES_PER_DAY),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ZCard {
            SwitchRow(
                title = stringResource(R.string.focus_prayer_lock),
                subtitle = stringResource(R.string.focus_prayer_lock_hint),
                checked = Store.prayerLockEnabled,
                onChange = { Store.prayerLockEnabled = it },
            )
            if (Store.prayerLockEnabled) {
                SliderRow(
                    title = stringResource(R.string.focus_prayer_lock_duration),
                    valueText = durationText(Store.prayerLockMinutes.toLong()),
                    value = Store.prayerLockMinutes,
                    range = 5..30,
                    step = 5,
                    onChange = { Store.prayerLockMinutes = it },
                )
            }
        }

        ZCard {
            val apps = AppsCache.apps
            val selected = Store.blockedPackages
            SettingRow(
                title = stringResource(R.string.focus_apps),
                subtitle = when {
                    apps == null -> "…"
                    selected.isEmpty() -> stringResource(R.string.focus_apps_none)
                    else -> apps.filter { it.packageName in selected }.joinToString { it.label }
                },
                icon = androidx.compose.ui.res.painterResource(R.drawable.ic_nav_more),
                onClick = { showSettings = false; nav.open(Screen.APPS) },
            )
        }

        SectionLabel(stringResource(R.string.focus_permissions))
        ZCard {
            if (hasUsage) PermissionRow(
                title = stringResource(R.string.perm_usage_title),
                text = stringResource(R.string.perm_usage_text),
                granted = hasUsage,
                onGrant = { ScreenTime.openUsageAccessSettings(context) },
            )
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(R.string.perm_block_ways), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            PermissionRow(
                title = stringResource(R.string.perm_overlay_title),
                text = stringResource(R.string.perm_overlay_text),
                granted = hasOverlay,
                onGrant = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
            Spacer(Modifier.height(14.dp))
            PermissionRow(
                title = stringResource(R.string.perm_service_title),
                text = stringResource(R.string.perm_service_text),
                granted = hasService,
                onGrant = { showDisclosure = true },
                actionTag = "focus_accessibility_disclosure",
            )
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
            // Экономия батареи останавливает защиту — просим разрешить работу в фоне.
            val power = context.getSystemService(android.os.PowerManager::class.java)
            val hasBattery = remember(resume) { power.isIgnoringBatteryOptimizations(context.packageName) }
            PermissionRow(
                title = stringResource(R.string.perm_battery_title),
                text = stringResource(R.string.perm_battery_text),
                granted = hasBattery,
                onGrant = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
            // Xiaomi / Redmi / POCO: отдельное разрешение «Всплывающие окна в фоне» и автозапуск.
            if (android.os.Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")) {
                Spacer(Modifier.height(12.dp))
                PermissionRow(
                    title = stringResource(R.string.perm_xiaomi_title),
                    text = stringResource(R.string.perm_xiaomi_text),
                    granted = false,
                    onGrant = {
                        val miui = Intent("miui.intent.action.APP_PERM_EDITOR")
                            .putExtra("extra_pkgname", context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(miui) }.onFailure { runCatching { context.startActivity(details) } }
                    },
                )
            }
        }

        // Проверка: на минуту соцсети закрываются, как во время намаза.
        if (Store.blockingEnabled) {
            var testStarted by remember { mutableStateOf(false) }
            ZCard {
                Text(stringResource(R.string.focus_test_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(if (testStarted) R.string.focus_test_running else R.string.focus_test_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (testStarted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                TonalButton(stringResource(R.string.focus_test_button), onClick = {
                    Store.testLockUntil = System.currentTimeMillis() + 60_000
                    testStarted = true
                    GuardService.sync(context, checkNow = true)
                })
            }
        }
    }

    if (showDisclosure) {
        HorizonDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.disclosure_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.disclosure_text_1))
                    Text(stringResource(R.string.disclosure_text_2))
                    Text(stringResource(R.string.disclosure_text_3), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDisclosure = false
                    ScreenTime.openAccessibilitySettings(context)
                }) { Text(stringResource(R.string.action_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text(stringResource(R.string.action_app_info)) }
            },
        )
    }
}

@Composable
fun PermissionRow(title: String, text: String, granted: Boolean, actionTag: String = "permission_grant", onGrant: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (granted) Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.state_granted), tint = MaterialTheme.colorScheme.primary)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!granted) FilledTonalButton(onClick = onGrant, modifier = Modifier.heightIn(min = 48.dp).testTag(actionTag)) { Text(stringResource(R.string.action_grant)) }
    }
}

/** Выбор соцсетей для ограничения. */
@Composable
fun AppsScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        if (AppsCache.apps == null) {
            val loaded = withContext(Dispatchers.IO) { ScreenTime.launchableApps(context) }
            ScreenTime.ensureDefaultSelection(loaded)
            AppsCache.apps = loaded
        }
    }
    ScreenScaffold(title = stringResource(R.string.focus_apps), onBack = { nav.back() }) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.action_search)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        val apps = AppsCache.apps
        if (apps == null) {
            CircularProgressIndicator(Modifier.padding(20.dp))
        } else {
            val filtered = apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
            if (filtered.isEmpty()) {
                StatePanel(stringResource(R.string.search_nothing), icon = R.drawable.ic_nav_focus, action = stringResource(R.string.action_reset), onAction = { query = "" })
            }
            ZCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 8.dp)) {
                filtered.forEach { app -> AppRow(app) }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo) {
    val context = LocalContext.current
    val checked = app.packageName in Store.blockedPackages
    val icon = remember(app.packageName) {
        runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                Store.blockedPackages = Store.blockedPackages.let { if (checked) it - app.packageName else it + app.packageName }
                FocusPause.selectionChanged(context)
                GuardService.sync(context)
                BlockerService.recheck()
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
        } else {
            Surface(Modifier.size(40.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge)
            if (app.packageName in ScreenTime.KNOWN_SOCIAL) {
                Text(stringResource(R.string.focus_social_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Checkbox(checked = checked, onCheckedChange = null)
    }
}
