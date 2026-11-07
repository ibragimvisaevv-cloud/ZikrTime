package com.zikrtime.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.PrayerCheckPlan
import com.zikrtime.app.prayer.PrayerCheckReminders

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrayerCheckReminderSettingsCard() {
    val context = LocalContext.current
    var permissionUpdate by remember { mutableIntStateOf(0) }
    var enableAfterPermission by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && enableAfterPermission) PrayerCheckReminders.enabled = true
        enableAfterPermission = false
        permissionUpdate++
        PrayerCheckReminders.reschedule(context)
    }
    val blocked = remember(AppEvents.resumeCount, permissionUpdate) {
        !NotificationManagerCompat.from(context).areNotificationsEnabled() ||
            PrayerCheckReminders.channel(context).importance == NotificationManager.IMPORTANCE_NONE
    }
    val title = stringResource(R.string.prayer_check_title)
    ZCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = PrayerCheckReminders.enabled, enabled = Store.personalProgressEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        enableAfterPermission = true
                        request.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        PrayerCheckReminders.enabled = enabled
                        PrayerCheckReminders.reschedule(context)
                    }
                }, modifier = Modifier.testTag("prayer_check_enabled").semantics { contentDescription = title })
        }
        Text(stringResource(R.string.prayer_check_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (PrayerCheckReminders.enabled && Store.personalProgressEnabled) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.prayer_check_delay), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (minutes in PrayerCheckPlan.delays) FilterChip(
                    selected = PrayerCheckReminders.delayMinutes == minutes,
                    onClick = { PrayerCheckReminders.delayMinutes = minutes; PrayerCheckReminders.reschedule(context) },
                    label = { Text(stringResource(R.string.prayer_check_minutes, minutes)) },
                    modifier = Modifier.testTag("prayer_check_delay_$minutes"))
            }
        }
        if (!Store.personalProgressEnabled) {
            Text(stringResource(R.string.prayer_check_progress), style = MaterialTheme.typography.bodySmall)
        } else if (blocked) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.reading_blocked), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                val channelOnly = NotificationManagerCompat.from(context).areNotificationsEnabled()
                val action = if (channelOnly) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS else Settings.ACTION_APP_NOTIFICATION_SETTINGS
                val intent = Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, PrayerCheckReminders.CHANNEL)
                if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
            }) { Text(stringResource(R.string.action_open_settings)) }
        }
    }
}
