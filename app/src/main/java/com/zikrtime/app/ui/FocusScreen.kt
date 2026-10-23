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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.screentime.AppInfo
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
    var showDisclosure by remember { mutableStateOf(false) }

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

    TabColumn(title = stringResource(R.string.focus_title), subtitle = stringResource(R.string.focus_subtitle)) {
        // Сегодня
        ZCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val used = (usedMillis ?: 0L) / 60_000
                val allowed = Store.allowedMinutes()
                ProgressRing(
                    progress = used.toFloat() / allowed.coerceAtLeast(1),
                    modifier = Modifier.size(92.dp),
                    stroke = 9.dp,
                    color = if (used >= allowed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(used.toString(), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.unit_min), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.focus_today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (usedMillis == null) stringResource(R.string.focus_no_access)
                        else stringResource(R.string.focus_used_of, durationText(used), durationText(allowed.toLong())),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val bonus = Store.bonusMinutes()
                    Text(
                        stringResource(R.string.focus_limit_bonus, durationText(Store.dailyLimitMinutes.toLong()), bonus),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (Store.bonusMinutes() < Store.MAX_BONUS_MINUTES_PER_DAY) {
                Spacer(Modifier.height(16.dp))
                TonalButton(stringResource(R.string.focus_earn_button, Store.BONUS_MINUTES_PER_ROUND), onClick = { nav.startQuiz(null) })
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
                onClick = { nav.open(Screen.APPS) },
            )
        }

        SectionLabel(stringResource(R.string.focus_permissions))
        ZCard {
            PermissionRow(
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
            )
        }
    }

    if (showDisclosure) {
        AlertDialog(
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
fun PermissionRow(title: String, text: String, granted: Boolean, onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.state_granted), tint = MaterialTheme.colorScheme.primary)
        } else {
            FilledTonalButton(onClick = onGrant) { Text(stringResource(R.string.action_grant)) }
        }
    }
}

/** Выбор соцсетей для ограничения. */
@Composable
fun AppsScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
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
                Text(stringResource(R.string.search_nothing), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
