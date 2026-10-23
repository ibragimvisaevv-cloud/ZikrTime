package com.zikrtime.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.zikrtime.app.R
import com.zikrtime.app.alarm.FajrAlarm
import com.zikrtime.app.data.Store
import com.zikrtime.app.i18n.Lang
import com.zikrtime.app.prayer.AdhanSound
import com.zikrtime.app.prayer.PrayerAlarms
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.WeekFields

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSettingsScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val resume = AppEvents.resumeCount
    val notificationsAllowed = remember(resume) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val fullScreenAllowed = remember(resume) { FajrAlarm.canFullScreen(context) }
    val exactAllowed = remember(resume) { PrayerAlarms.canScheduleExact(context) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { AppEvents.resumeCount++ }
    var version by remember { mutableStateOf(0) } // перерисовать «следующий сигнал» после изменений
    val testToast = stringResource(R.string.alarm_test_toast)

    fun changed() {
        FajrAlarm.schedule(context)
        version++
    }

    ScreenScaffold(title = stringResource(R.string.alarm_title), onBack = { nav.back() }) {
        ZCard {
            SwitchRow(
                title = stringResource(R.string.alarm_enable),
                subtitle = stringResource(R.string.alarm_enable_hint),
                icon = painterResource(R.drawable.ic_alarm),
                checked = Store.alarmEnabled,
                onChange = {
                    Store.alarmEnabled = it
                    if (it && Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    changed()
                },
            )
            if (Store.alarmEnabled) {
                val next = remember(version, Store.alarmOffsetMinutes, Store.alarmDays) { FajrAlarm.nextTrigger(ZonedDateTime.now()) }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (next != null) stringResource(R.string.alarm_next, gregorianDate(next.toLocalDate()), clock(next), clock(FajrAlarm.fajrFor(next)))
                    else stringResource(R.string.alarm_next_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        if (Store.alarmEnabled) {
            ZCard {
                val offset = Store.alarmOffsetMinutes
                SliderRow(
                    title = stringResource(R.string.alarm_offset),
                    valueText = if (offset == 0) stringResource(R.string.alarm_offset_zero) else stringResource(R.string.alarm_offset_value, offset),
                    value = offset,
                    range = 0..120,
                    step = 5,
                    onChange = {
                        Store.alarmOffsetMinutes = it
                        changed()
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.alarm_days), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                val locale = Lang.locale(context)
                val first = WeekFields.of(locale).firstDayOfWeek
                val week = (0L..6L).map { first.plus(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        val on = day in Store.alarmDays
                        FilterChip(
                            selected = on,
                            onClick = {
                                Store.alarmDays = if (on) Store.alarmDays - day else Store.alarmDays + day
                                changed()
                            },
                            label = {
                                Text(
                                    day.getDisplayName(TextStyle.SHORT_STANDALONE, locale).take(3),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 1,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            SectionLabel(stringResource(R.string.alarm_sound))
            ZCard(padding = PaddingValues(vertical = 6.dp, horizontal = 8.dp)) {
                val hasAdhan = AdhanSound.available(context)
                if (hasAdhan) {
                    RadioLine(stringResource(R.string.alarm_sound_adhan), selected = Store.alarmSound == "adhan", onClick = { Store.alarmSound = "adhan" })
                }
                RadioLine(
                    stringResource(R.string.alarm_sound_system),
                    selected = Store.alarmSound == "system" || !hasAdhan,
                    onClick = { Store.alarmSound = "system" },
                )
            }

            ZCard(padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                SwitchRow(
                    title = stringResource(R.string.alarm_max_volume),
                    subtitle = stringResource(R.string.alarm_max_volume_hint),
                    checked = Store.alarmMaxVolume,
                    onChange = { Store.alarmMaxVolume = it },
                )
                SwitchRow(
                    title = stringResource(R.string.alarm_vibrate),
                    checked = Store.alarmVibrate,
                    onChange = { Store.alarmVibrate = it },
                )
                SwitchRow(
                    title = stringResource(R.string.alarm_wake_check),
                    subtitle = stringResource(R.string.alarm_wake_check_hint),
                    checked = Store.alarmWakeCheck,
                    onChange = { Store.alarmWakeCheck = it },
                )
                SwitchRow(
                    title = stringResource(R.string.alarm_snooze_allow),
                    subtitle = stringResource(R.string.alarm_snooze_hint),
                    checked = Store.alarmSnoozeAllowed,
                    onChange = { Store.alarmSnoozeAllowed = it },
                )
            }

            TonalButton(stringResource(R.string.alarm_test), onClick = {
                FajrAlarm.scheduleTest(context, 10)
                Toast.makeText(context, testToast, Toast.LENGTH_LONG).show()
            })

            if (!notificationsAllowed || !fullScreenAllowed || !exactAllowed) {
                SectionLabel(stringResource(R.string.focus_permissions))
                ZCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (!notificationsAllowed) {
                            PermissionRow(stringResource(R.string.perm_notifications_title), stringResource(R.string.perm_notifications_text), false) {
                                if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                        if (!fullScreenAllowed && Build.VERSION.SDK_INT >= 34) {
                            PermissionRow(stringResource(R.string.perm_fullscreen_title), stringResource(R.string.perm_fullscreen_text), false) {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                        if (!exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PermissionRow(stringResource(R.string.perm_exact_title), stringResource(R.string.perm_exact_text), false) {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.alarm_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
