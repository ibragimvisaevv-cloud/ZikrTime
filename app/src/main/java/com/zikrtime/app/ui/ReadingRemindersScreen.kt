package com.zikrtime.app.ui

import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.ReadingKind
import com.zikrtime.app.prayer.ReadingReminders
import java.time.LocalTime

@Composable
fun ReadingRemindersScreen() {
    val context = LocalContext.current
    val nav = LocalNav.current
    val uri = LocalUriHandler.current
    val resumed = AppEvents.resumeCount
    var permissionUpdate by remember { mutableIntStateOf(0) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val kind = ReadingKind.entries.firstOrNull { it.name == pending }
        if (granted && kind != null) {
            Store.setReadingEnabled(kind, true)
            ReadingReminders.schedule(context, kind)
        }
        pending = null
        permissionUpdate++
    }
    val blocked = remember(resumed, permissionUpdate) {
        val channel = ReadingReminders.channel(context)
        !NotificationManagerCompat.from(context).areNotificationsEnabled() || channel.importance == NotificationManager.IMPORTANCE_NONE
    }
    ScreenScaffold(stringResource(R.string.reading_title), { nav.back() }, showHeading = false) {
        Text(stringResource(R.string.reading_hint), style = MaterialTheme.typography.bodyMedium)
        for (kind in ReadingKind.entries) {
            val minute = Store.readingMinute(kind)
            val title = stringResource(ReadingReminders.title(kind))
            val source = if (kind == ReadingKind.BEDTIME) R.string.reading_bukhari else R.string.reading_ifta
            val time = LocalTime.of(minute / 60, minute % 60)
            ZCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    Switch(checked = Store.readingEnabled(kind), onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            pending = kind.name
                            request.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            Store.setReadingEnabled(kind, enabled)
                            ReadingReminders.schedule(context, kind)
                        }
                    }, modifier = Modifier.testTag("reading_" + kind.name).semantics { contentDescription = title })
                }
                Text(stringResource(ReadingReminders.passage(kind)), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        Store.setReadingMinute(kind, h * 60 + m)
                        ReadingReminders.schedule(context, kind)
                    }, time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("reading_time_" + kind.name)) {
                    Text(stringResource(R.string.reading_time, time.format(clockFormatter(context))))
                }
                TextButton({ nav.openReading(kind) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("reading_open_" + kind.name)) {
                    Text(stringResource(R.string.reading_open))
                }
                TextButton(onClick = {
                    val url = if (kind == ReadingKind.BEDTIME) "https://sunnah.com/bukhari/40/11"
                        else "https://www.dar-alifta.org/en/fatwa/details/1964/reciting-quran-before-the-friday-prayer"
                    runCatching { uri.openUri(url) }.onFailure {
                        Toast.makeText(context, R.string.source_link_failed, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(source), style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (blocked) {
            Text(stringResource(R.string.reading_blocked), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {
                val channelOnly = NotificationManagerCompat.from(context).areNotificationsEnabled()
                val action = if (channelOnly) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS else Settings.ACTION_APP_NOTIFICATION_SETTINGS
                val settings = Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, ReadingReminders.CHANNEL)
                if (settings.resolveActivity(context.packageManager) != null) context.startActivity(settings)
            }) { Text(stringResource(R.string.action_open_settings)) }
        }
    }
}
