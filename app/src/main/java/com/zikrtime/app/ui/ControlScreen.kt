package com.zikrtime.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.zikrtime.app.data.Store
import com.zikrtime.app.screentime.AppInfo
import com.zikrtime.app.screentime.GuardService
import com.zikrtime.app.screentime.ScreenTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ControlScreen() {
    val context = LocalContext.current
    val resume = AppEvents.resumeCount
    val hasUsage = remember(resume) { ScreenTime.hasUsageAccess(context) }
    val hasService = remember(resume) { ScreenTime.isAccessibilityEnabled(context) }
    val hasOverlay = remember(resume) { GuardService.canDrawOverlays(context) }
    // Блокировать умеем двумя способами: через службу специальных возможностей
    // или (если телефон её не даёт включить) через показ поверх других приложений.
    val canBlock = hasService || hasOverlay

    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { ScreenTime.launchableApps(context) }
        ScreenTime.ensureDefaultSelection(loaded)
        apps = loaded
    }

    var pickingApps by remember { mutableStateOf(false) }
    var showDisclosure by remember { mutableStateOf(false) }

    if (pickingApps) {
        AppPicker(apps = apps, onBack = { pickingApps = false })
        return
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Контроль соцсетей", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Когда дневной лимит закончится, соцсеть закроется. Дополнительное время можно заработать ответами на вопросы.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard(title = "Разрешения") {
            PermissionRow(
                title = "Доступ к истории использования",
                text = "Чтобы считать, сколько времени вы провели в соцсетях сегодня.",
                granted = hasUsage,
                onGrant = { ScreenTime.openUsageAccessSettings(context) },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Чтобы закрывать соцсеть, нужен один из двух способов:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            PermissionRow(
                title = "Способ 1. Показ поверх других приложений",
                text = "Проще всего включить. Телефон не считает его опасным. В шторке будет висеть тихое уведомление.",
                granted = hasOverlay,
                onGrant = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
            PermissionRow(
                title = "Способ 2. Служба «Зикр-тайм»",
                text = "Срабатывает мгновенно, но на многих телефонах заблокирована для приложений не из Google Play.",
                granted = hasService,
                onGrant = { showDisclosure = true },
            )
        }

        SectionCard {
            SwitchRow(
                title = "Ограничивать соцсети",
                checked = Store.blockingEnabled,
                onChange = {
                    Store.blockingEnabled = it
                    GuardService.sync(context)
                },
            )
            if (Store.blockingEnabled && !(hasUsage && canBlock)) {
                Text(
                    "Выдайте доступ к истории использования и включите один из двух способов выше, иначе ограничение не сработает.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        SectionCard(title = "Дневной лимит") {
            val limit = Store.dailyLimitMinutes
            Text(formatMinutes(limit.toLong()), style = MaterialTheme.typography.headlineMedium)
            Slider(
                value = limit.toFloat(),
                onValueChange = { Store.dailyLimitMinutes = ((it / 5f).roundToInt() * 5).coerceIn(10, 180) },
                valueRange = 10f..180f,
                steps = 33,
            )
            Text(
                "На все выбранные приложения вместе. Ещё до ${Store.MAX_BONUS_MINUTES_PER_DAY} мин в день можно заработать в разделе «Задачи».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Время намаза") {
            SwitchRow(
                title = "Закрывать соцсети после азана",
                checked = Store.prayerLockEnabled,
                onChange = { Store.prayerLockEnabled = it },
            )
            if (Store.prayerLockEnabled) {
                val minutes = Store.prayerLockMinutes
                Spacer(Modifier.height(8.dp))
                Text("На $minutes мин после начала времени намаза", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { Store.prayerLockMinutes = ((it / 5f).roundToInt() * 5).coerceIn(5, 30) },
                    valueRange = 5f..30f,
                    steps = 4,
                )
                Text(
                    "Если вы уже совершили намаз, нажмите «Я совершил намаз» на экране блокировки — доступ откроется сразу.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "Приложения") {
            val selected = Store.blockedPackages
            val list = apps
            when {
                list == null -> CircularProgressIndicator(Modifier.size(24.dp))
                selected.isEmpty() -> Text("Пока ничего не выбрано.", style = MaterialTheme.typography.bodyMedium)
                else -> {
                    val names = list.filter { it.packageName in selected }.joinToString { it.label }
                    Text(names, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = { pickingApps = true }) {
                Text("Выбрать приложения")
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("Служба специальных возможностей") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "«Зикр-тайм» использует службу специальных возможностей только для одного: узнать, какое приложение " +
                            "сейчас открыто. Если это соцсеть из вашего списка, а лимит исчерпан или идёт время намаза, " +
                            "приложение вернёт вас на главный экран и покажет напоминание.",
                    )
                    Text("Содержимое экрана, сообщения и пароли не читаются. Ничего не отправляется в интернет — всё остаётся на телефоне.")
                    Text(
                        "В настройках откройте «Скачанные приложения» (или «Установленные службы») → «Зикр-тайм» и включите. " +
                            "Если переключатель неактивен, откройте сведения о приложении → меню ⋮ → «Разрешить ограниченные настройки».",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDisclosure = false
                    ScreenTime.openAccessibilitySettings(context)
                }) { Text("Открыть настройки") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("Сведения о приложении") }
            },
        )
    }
}

@Composable
private fun PermissionRow(title: String, text: String, granted: Boolean, onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Выдано", tint = MaterialTheme.colorScheme.primary)
        } else {
            Button(onClick = onGrant) { Text("Выдать") }
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AppPicker(apps: List<AppInfo>?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text("Приложения", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Поиск") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        if (apps == null) {
            CircularProgressIndicator(Modifier.padding(20.dp))
        } else {
            AppList(apps, query)
        }
    }
}

@Composable
private fun AppList(apps: List<AppInfo>, query: String) {
    val context = LocalContext.current
    run {
        val filtered = apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.packageName }) { app ->
                val checked = app.packageName in Store.blockedPackages
                val icon = remember(app.packageName) {
                    runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            Store.blockedPackages = Store.blockedPackages.let { if (checked) it - app.packageName else it + app.packageName }
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Image(icon, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                    } else {
                        Surface(Modifier.size(40.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        if (app.packageName in ScreenTime.KNOWN_SOCIAL) {
                            Text("соцсеть", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Checkbox(checked = checked, onCheckedChange = null)
                }
            }
        }
    }
}
