package com.zikrtime.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.zikrtime.app.R
import com.zikrtime.app.alarm.FajrAlarm
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.PrayerAlarms
import com.zikrtime.app.ui.theme.Cream
import com.zikrtime.app.ui.theme.EmeraldDeep
import com.zikrtime.app.ui.theme.EmeraldNight
import com.zikrtime.app.ui.theme.Gold
import java.time.ZonedDateTime
import java.time.temporal.WeekFields
import java.util.Locale

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
        val next = remember(version, Store.alarmEnabled, Store.alarmOffsetMinutes, Store.alarmDays) {
            if (Store.alarmEnabled) FajrAlarm.nextTrigger(ZonedDateTime.now()) else null
        }
        AlarmHero(
            enabled = Store.alarmEnabled,
            next = next,
            onToggle = {
                Store.alarmEnabled = it
                if (it && Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                changed()
            },
        )

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
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.alarm_days), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                // Неделя начинается так, как принято в стране телефона (понедельник или воскресенье).
                val first = WeekFields.of(LocaleList.getDefault()[0] ?: Locale.getDefault()).firstDayOfWeek
                val week = (0L..6L).map { first.plus(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        DayToggle(
                            label = weekdayShort(day),
                            selected = day in Store.alarmDays,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                Store.alarmDays = if (day in Store.alarmDays) Store.alarmDays - day else Store.alarmDays + day
                                changed()
                            },
                        )
                    }
                }
            }

            SectionLabel(stringResource(R.string.alarm_sound))
            ZCard(padding = PaddingValues(vertical = 6.dp, horizontal = 8.dp)) {
                AdhanVoiceList(selected = Store.alarmSound, onSelect = { Store.alarmSound = it }, includeSystem = true)
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

            if (!Perms.allGranted(context).also { resume }) {
                SectionLabel(stringResource(R.string.focus_permissions))
                ZCard { EssentialPermissions(showGranted = false) }
            }
            Text(
                stringResource(R.string.alarm_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Главная карточка: включатель и время следующего будильника крупно. */
@Composable
private fun AlarmHero(enabled: Boolean, next: ZonedDateTime?, onToggle: (Boolean) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(EmeraldDeep, EmeraldNight)))
            .clickable { onToggle(!enabled) },
    ) {
        StarPattern(Modifier.matchParentSize(), color = Gold.copy(alpha = 0.10f))
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_alarm), contentDescription = null, tint = Gold, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.alarm_enable),
                    style = MaterialTheme.typography.titleMedium,
                    color = Cream,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = EmeraldDeep,
                        checkedTrackColor = Gold,
                        uncheckedThumbColor = Cream.copy(alpha = 0.8f),
                        uncheckedTrackColor = Color.Transparent,
                        uncheckedBorderColor = Cream.copy(alpha = 0.5f),
                    ),
                )
            }
            Spacer(Modifier.height(10.dp))
            if (enabled && next != null) {
                BigClock(next, fontSize = 56.sp, color = Gold)
                Text(
                    gregorianDate(next.toLocalDate()) + " · " + stringResource(R.string.alarm_fajr_at, clock(FajrAlarm.fajrFor(next))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cream.copy(alpha = 0.85f),
                )
            } else {
                Text(
                    if (enabled) stringResource(R.string.alarm_next_none) else stringResource(R.string.alarm_enable_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cream.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/** Кружок дня недели: выбран — закрашен. */
@Composable
private fun DayToggle(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (selected) scheme.primary else scheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}
