package com.zikrtime.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.data.Ramadan
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.prayer.PrayerSchedule
import com.zikrtime.app.ui.theme.Cream
import com.zikrtime.app.ui.theme.Gold
import com.zikrtime.app.ui.theme.Tabular
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Обратный отсчёт «ч:мм:сс». */
private fun hms(d: Duration): String {
    val s = d.seconds.coerceAtLeast(0)
    return "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
}

/** Что сейчас: до конца сухура (фаджр) или до ифтара (магриб). */
private class FastState(val iftarNext: Boolean, val target: ZonedDateTime, val suhurEnd: ZonedDateTime, val iftar: ZonedDateTime)

private fun fastState(now: ZonedDateTime): FastState {
    val today = PrayerSchedule.forDate(now.toLocalDate())
    val fajr = today[Prayer.FAJR]
    val maghrib = today[Prayer.MAGHRIB]
    return when {
        now.isBefore(fajr) -> FastState(false, fajr, fajr, maghrib)
        now.isBefore(maghrib) -> FastState(true, maghrib, fajr, maghrib)
        else -> {
            val tomorrow = PrayerSchedule.forDate(now.toLocalDate().plusDays(1))
            FastState(false, tomorrow[Prayer.FAJR], tomorrow[Prayer.FAJR], tomorrow[Prayer.MAGHRIB])
        }
    }
}

@Composable
fun RamadanScreen() {
    val nav = LocalNav.current
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now = ZonedDateTime.now()
        }
    }
    val today = now.toLocalDate()
    val shift = Store.ramadanShift
    val period = remember(today, shift) { Ramadan.currentOrNext(today) }
    val state = fastState(now)
    val context = androidx.compose.ui.platform.LocalContext.current
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMMM", com.zikrtime.app.i18n.Lang.locale(context)) }

    ScreenScaffold(title = stringResource(R.string.ramadan_title), onBack = { nav.back() }) {
        SectionBackdrop {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val day = period?.dayOf(today)
                androidx.compose.material3.Icon(painterResource(R.drawable.ic_prayer_isha), null, tint = Gold)
                Text(
                    when {
                        period == null -> stringResource(R.string.ramadan_title)
                        day != null -> stringResource(R.string.ramadan_day, day, period.days)
                        else -> stringResource(R.string.ramadan_until, (period.start.toEpochDay() - today.toEpochDay()).toInt())
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = Cream,
                    textAlign = TextAlign.Center,
                )
                if (period != null && day == null) {
                    Text(
                        stringResource(R.string.ramadan_starts, period.start.format(dateFmt)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Cream.copy(alpha = 0.8f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(if (state.iftarNext) R.string.ramadan_to_iftar else R.string.ramadan_to_suhur_end),
                    style = MaterialTheme.typography.labelLarge,
                    color = Cream.copy(alpha = 0.85f),
                )
                Text(
                    hms(Duration.between(now, state.target)),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Light,
                    color = Gold,
                    style = Tabular,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TimeCell(stringResource(R.string.ramadan_suhur), clock(state.suhurEnd), R.drawable.ic_prayer_fajr)
                    TimeCell(stringResource(R.string.ramadan_iftar), clock(state.iftar), R.drawable.ic_prayer_maghrib)
                }
            }
        }

        if (period != null) {
            val fasted = period.let { p -> (0 until p.days).count { Ramadan.fasted(p.start.plusDays(it.toLong())) } }
            ZCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ramadan_fasts_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.ramadan_fasts_count, fasted, period.days), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.ramadan_fasts_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                (1..period.days).chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { n ->
                            val date = period.start.plusDays(n - 1L)
                            val done = Ramadan.fasted(date)
                            val canMark = !date.isAfter(today)
                            val isToday = date == today
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            done -> Gold
                                            canMark -> MaterialTheme.colorScheme.surfaceContainerHigh
                                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                                        },
                                    )
                                    .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                    .clickable(enabled = canMark) { Ramadan.toggle(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    n.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when {
                                        done -> Color(0xFF2B1E00)
                                        canMark -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    },
                                )
                            }
                        }
                        repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        ZCard {
            Text(stringResource(R.string.ramadan_start_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-1 to R.string.ramadan_shift_earlier, 0 to R.string.ramadan_shift_none, 1 to R.string.ramadan_shift_later).forEach { (v, label) ->
                    FilterChip(selected = shift == v, onClick = { Store.ramadanShift = v }, label = { Text(stringResource(label)) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ramadan_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimeCell(label: String, time: String, icon: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Icon(painterResource(icon), contentDescription = null, tint = Gold, modifier = Modifier.height(24.dp).width(24.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Cream.copy(alpha = 0.8f))
        Text(time, style = MaterialTheme.typography.titleLarge.merge(Tabular), color = Cream)
    }
}

/** Карточка на главном экране: во время Рамадана и за две недели до него. */
@Composable
fun RamadanHomeCard(now: ZonedDateTime, onOpen: () -> Unit) {
    val today = now.toLocalDate()
    val period = remember(today, Store.ramadanShift) { Ramadan.currentOrNext(today) } ?: return
    val day = period.dayOf(today)
    val daysUntil = (period.start.toEpochDay() - today.toEpochDay()).toInt()
    if (day == null && daysUntil > 14) return
    val state = fastState(now)
    ZCard(onClick = onOpen, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🌙", fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (day != null) stringResource(R.string.ramadan_day, day, period.days) else stringResource(R.string.ramadan_until, daysUntil),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(
                        if (state.iftarNext) R.string.ramadan_home_iftar else R.string.ramadan_home_suhur,
                        clock(if (state.iftarNext) state.iftar else state.suhurEnd),
                        durationText(Duration.between(now, state.target).toMinutes() + 1),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
