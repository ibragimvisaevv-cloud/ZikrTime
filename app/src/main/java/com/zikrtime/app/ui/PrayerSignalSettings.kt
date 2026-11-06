package com.zikrtime.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.prayer.PrayerAlarms
import com.zikrtime.app.prayer.PrayerSignals
import com.zikrtime.app.prayer.AdhanSound
import com.zikrtime.app.prayer.FridayReminders
import com.zikrtime.app.prayer.Prayer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun PrayerSignalSettings() {
    val context = LocalContext.current
    val adhan = Store.notificationAdhan && AdhanSound.available(context, AdhanSound.notificationVoice(Store.notificationVoice).id)
    if (Store.prayerNotifications && !adhan) {
        Column(Modifier.testTag("prayer_brief_tone")) {
            SwitchRow(title = stringResource(R.string.signal_short_title),
                subtitle = stringResource(R.string.signal_short_hint),
                checked = PrayerSignals.brief, onChange = { PrayerSignals.brief = it })
        }
    }
    val resume = AppEvents.resumeCount
    val blocked = androidx.compose.runtime.remember(resume, adhan, Store.notificationVoice) {
        PrayerAlarms.blockedLegacyChannel(context, adhan)
    }
    if (blocked != null && Store.prayerNotifications) {
        androidx.compose.material3.TextButton(onClick = {
            val settings = android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, blocked)
            if (settings.resolveActivity(context.packageManager) != null) context.startActivity(settings)
        }) { Text(stringResource(R.string.signal_blocked)) }
    }
    val event = PrayerSignals.last()
    val detail = event?.detail
    val title = when (event?.kind) {
        "prayer" -> Prayer.obligatory.firstOrNull { it.name == detail }?.let {
            stringResource(R.string.notify_prayer_title, prayerName(it))
        }
        "fajr" -> stringResource(R.string.alarm_title)
        "friday" -> FridayReminders.Slot.entries.firstOrNull { it.name == detail }?.let { stringResource(it.title) }
        else -> null
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("prayer_last_signal")) {
        Text(stringResource(R.string.signal_last_title), style = MaterialTheme.typography.titleSmall)
        if (event != null && title != null) {
            val clock = clockFormatter(context)
            val locale = Lang.locale(context)
            val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            fun formatted(millis: Long): String {
                val local = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                return date.format(local) + " · " + clock.format(local)
            }
            val mode = when (event.mode) {
                "adhan" -> stringResource(R.string.prayer_notify_adhan)
                "brief" -> stringResource(R.string.signal_short_title)
                "quiet" -> stringResource(R.string.signal_quiet)
                else -> null
            }
            Text(if (mode == null) title else "$title · $mode", style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.signal_scheduled, formatted(event.scheduled)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.signal_delivered, formatted(event.delivered)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Text(stringResource(R.string.signal_none), style = MaterialTheme.typography.bodyMedium)
    }
}
