package com.zikrtime.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.zikrtime.app.R
import com.zikrtime.app.alarm.AlarmState
import com.zikrtime.app.alarm.FajrAlarm
import com.zikrtime.app.prayer.PrayerSchedule
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun FajrLastSignal(resume: Int) {
    val context = LocalContext.current
    val signal = remember(context, resume, AlarmState.ringing) { FajrAlarm.lastSignal(context) }
    ZCard(modifier = Modifier.testTag("alarm_last_signal")) {
        Text(stringResource(R.string.fajr_history_title), style = MaterialTheme.typography.titleMedium)
        if (signal == null) {
            Text(stringResource(R.string.fajr_history_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            val locale = LocalConfiguration.current.locales[0]
            val clock = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale).withZone(PrayerSchedule.zone())
            val label = when (signal.kind) {
                "test" -> R.string.fajr_history_test
                "snooze" -> R.string.fajr_history_snooze
                else -> R.string.fajr_history_daily
            }
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.fajr_history_planned, clock.format(Instant.ofEpochMilli(signal.plannedAt))))
            Text(stringResource(R.string.fajr_history_fired, clock.format(Instant.ofEpochMilli(signal.firedAt))))
            val delayMinutes = (signal.firedAt - signal.plannedAt) / 60_000L
            if (delayMinutes > 0) Text(stringResource(R.string.fajr_history_delay, delayMinutes))
        }
    }
}
