package com.zikrtime.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.AsrMadhab
import com.zikrtime.app.prayer.CalcMethod
import com.zikrtime.app.prayer.OnlineTimes
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.prayer.PrayerAlarms
import com.zikrtime.app.prayer.PrayerSchedule
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    /** После смены города, метода или мазхаба — загрузить новое время и переставить будильник. */
    fun reload(force: Boolean = false) {
        PrayerAlarms.scheduleNext(context)
        scope.launch {
            refreshing = true
            OnlineTimes.refresh(force)
            refreshing = false
            PrayerAlarms.scheduleNext(context)
        }
    }
    val resume = AppEvents.resumeCount
    var pickingCity by remember { mutableStateOf(false) }

    val notificationsAllowed = remember(resume) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exactAllowed = remember(resume) { PrayerAlarms.canScheduleExact(context) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        AppEvents.resumeCount++
    }

    if (pickingCity) {
        BackHandler { pickingCity = false }
        CityPicker(
            modifier = Modifier.fillMaxSize(),
            onPicked = {
                Store.setLocation(it)
                reload()
                pickingCity = false
            },
            header = {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { pickingCity = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                    Text("Город", style = MaterialTheme.typography.titleLarge)
                }
            },
        )
        return
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)

        SectionCard(title = "Город") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Store.location.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { pickingCity = true }) { Text("Изменить") }
            }
        }

        SectionCard(title = "Метод расчёта") {
            CalcMethod.entries.forEach { m ->
                RadioLine(m.title, selected = Store.method == m) {
                    Store.method = m
                    reload()
                }
            }
            Text(
                "Если время в мечети вашего города отличается, попробуйте другой метод. Разница в несколько минут — это нормально.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        SectionCard(title = "Источник времени") {
            SwitchRow(
                title = "Брать время из интернета",
                subtitle = "Сервис aladhan.com, тот же метод расчёта. Без связи — расчёт на телефоне.",
                checked = Store.onlineTimes,
                onChange = {
                    Store.onlineTimes = it
                    reload()
                },
            )
            if (Store.onlineTimes) {
                Spacer(Modifier.height(8.dp))
                val updated = Store.onlineUpdatedAt
                val online = remember(OnlineTimes.version, Store.location, Store.method, Store.madhab) { PrayerSchedule.isOnline() }
                Text(
                    when {
                        refreshing -> "Загружаем…"
                        online && updated > 0 -> "Загружено " + java.time.Instant.ofEpochMilli(updated)
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM, HH:mm", java.util.Locale("ru")))
                        else -> "Пока не удалось загрузить — показываем расчёт на телефоне."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { reload(force = true) }, enabled = !refreshing) { Text("Обновить сейчас") }
            }
        }

        SectionCard(title = "Поправки, мин") {
            Text(
                "Если в вашей мечети время отличается, сдвиньте его здесь.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Prayer.entries.forEach { p ->
                val adj = Store.adjustment(p)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { Store.setAdjustment(p, adj - 1); PrayerAlarms.scheduleNext(context) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    Text(
                        if (adj > 0) "+$adj" else "$adj",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = { Store.setAdjustment(p, adj + 1); PrayerAlarms.scheduleNext(context) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
            }
        }

        SectionCard(title = "Время аср-намаза") {
            AsrMadhab.entries.forEach { m ->
                RadioLine(m.title, selected = Store.madhab == m) {
                    Store.madhab = m
                    reload()
                }
            }
        }

        SectionCard(title = "Уведомления") {
            SwitchRow(
                title = "Напоминать о времени намаза",
                checked = Store.prayerNotifications,
                onChange = {
                    Store.prayerNotifications = it
                    PrayerAlarms.scheduleNext(context)
                },
            )
            if (Store.prayerNotifications && !notificationsAllowed) {
                Spacer(Modifier.height(8.dp))
                Text("Уведомления для приложения выключены.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                FilledTonalButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text("Разрешить уведомления") }
            }
            if (Store.prayerNotifications && !exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Чтобы уведомление приходило минута в минуту, разрешите точные будильники.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("Разрешить точное время") }
            }
        }

        SectionCard(title = "О приложении") {
            Text("Зикр-тайм 0.2", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Время намаза рассчитывается прямо на телефоне, без интернета. Все данные хранятся только на этом устройстве.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RadioLine(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
