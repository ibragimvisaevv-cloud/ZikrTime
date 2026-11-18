package com.zikrtime.app.ui

import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikrtime.app.R
import com.zikrtime.app.alarm.FajrAlarm
import com.zikrtime.app.data.Cities
import com.zikrtime.app.data.Store
import com.zikrtime.app.data.Streaks
import com.zikrtime.app.prayer.OnlineTimes
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.prayer.PrayerSchedule
import com.zikrtime.app.prayer.Qibla
import com.zikrtime.app.screentime.ScreenTime
import com.zikrtime.app.ui.theme.Cream
import com.zikrtime.app.ui.theme.Emerald
import com.zikrtime.app.ui.theme.EmeraldDeep
import com.zikrtime.app.ui.theme.Gold
import com.zikrtime.app.ui.theme.Tabular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.roundToInt

@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val zone = CityZones.forCity(Store.location.id, OnlineTimes.day(java.time.LocalDate.now())?.zone ?: java.time.ZoneId.systemDefault())
    var now by remember(zone) { mutableStateOf(ZonedDateTime.now(zone)) }
    val today = now.toLocalDate()
    val adjustments = Prayer.entries.map { Store.adjustment(it) }
    val times = remember(today, zone, Store.location, Store.method, Store.madhab, Store.onlineTimes, OnlineTimes.version, adjustments) {
        PrayerSchedule.forDate(today, zone)
    }
    val next = remember(now, times) { PrayerSchedule.next(now) }
    val previousStart = remember(now, times) { PrayerSchedule.recentStarts(now).map { it.time }.filter { !it.isAfter(now.toInstant()) }.maxOrNull() }
    val current = remember(now, times) {
        Prayer.obligatory.lastOrNull { !times[it].isAfter(now) }?.takeIf { p ->
            // Утренний намаз заканчивается с восходом.
            !(p == Prayer.FAJR && !times[Prayer.SUNRISE].isAfter(now))
        }
    }

    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(zone, lifecycle, AppEvents.resumeCount) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                now = ZonedDateTime.now(zone)
                delay(15_000)
            }
        }
    }

    var usedMillis by remember { mutableStateOf<Long?>(null) }
    val resume = AppEvents.resumeCount
    LaunchedEffect(now, resume, Store.blockedPackages) {
        usedMillis = withContext(Dispatchers.IO) { ScreenTime.usedTodayMillis(context) }
    }

    TabColumn {
        NaturalSkyHero(now, next.first, next.second, times)
        ZCard(onClick = { nav.open(Screen.TASBIH) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                androidx.compose.foundation.Image(painterResource(R.drawable.stone_bead), null, Modifier.size(64.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tasbih_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.tasbih_today, Store.tasbihToday(today)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›", fontSize = 28.sp)
            }
        }

        // Намазы на сегодня
        ZCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
            Text(
                stringResource(R.string.home_today),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
            )
            val prayed = Store.prayedOn(today)
            Prayer.entries.forEach { p ->
                PrayerRow(
                    prayer = p,
                    time = clock(times[p]),
                    isCurrent = p == current,
                    started = !times[p].isAfter(now),
                    done = p in prayed,
                    onToggle = {
                        Store.setPrayed(today, p, p !in prayed)
                        com.zikrtime.app.widget.Widgets.updateAll(context)
                    },
                )
            }
            Text(
                if (Store.method == com.zikrtime.app.prayer.CalcMethod.MOSCOW_CATHEDRAL) stringResource(R.string.home_source_dumrf)
                else stringResource(
                    if (PrayerSchedule.isOnline(today)) R.string.home_source_online else R.string.home_source_local,
                    methodName(Store.method),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            )
        }

        // Быстрые действия
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val qibla = Qibla.bearing(Store.location.latitude, Store.location.longitude).roundToInt()
            ActionTile(
                icon = R.drawable.ic_qibla,
                title = stringResource(R.string.qibla_title),
                value = "$qibla°",
                modifier = Modifier.weight(1f),
                onClick = { nav.open(Screen.QIBLA) },
            )
            ActionTile(
                icon = R.drawable.ic_tasbih,
                title = stringResource(R.string.tasbih_title),
                value = Store.tasbihToday(today).toString(),
                modifier = Modifier.weight(1f),
                onClick = { nav.open(Screen.TASBIH) },
            )
            val alarmAt = if (Store.alarmEnabled) FajrAlarm.nextTrigger(now) else null
            ActionTile(
                icon = R.drawable.ic_alarm,
                title = stringResource(R.string.alarm_short),
                value = alarmAt?.let { clock(it) } ?: stringResource(R.string.state_off),
                modifier = Modifier.weight(1f),
                onClick = { nav.open(Screen.ALARM) },
            )
        }

        RamadanHomeCard(now, onOpen = { nav.open(Screen.RAMADAN) })

        PlacesEntryCard(onOpen = { nav.open(Screen.PLACES) })

        if (today.dayOfWeek == java.time.DayOfWeek.FRIDAY) FridayCard(today)

        FocusSummary(usedMillis = usedMillis, onOpen = { nav.tab = Tab.FOCUS }, onQuiz = { nav.startQuiz(null) })

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StreakCard(
                title = stringResource(R.string.streak_knowledge),
                value = Streaks.current(Store.quizDays, today),
                hint = stringResource(R.string.streak_knowledge_hint),
                modifier = Modifier.weight(1f),
            )
            StreakCard(
                title = stringResource(R.string.streak_prayers),
                value = Streaks.current(Store.prayerDays, today),
                hint = stringResource(R.string.streak_prayers_hint),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PrayerRow(prayer: Prayer, time: String, isCurrent: Boolean, started: Boolean, done: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isCurrent) scheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .padding(start = 8.dp, end = 0.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            painterResource(prayerIconRes(prayer)),
            tint = if (prayer.isPrayer) scheme.primary else scheme.onSurfaceVariant,
            container = if (isCurrent) scheme.surfaceContainerLowest else scheme.surfaceContainer,
            size = 38.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                prayerName(prayer),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (prayer.isPrayer) scheme.onSurface else scheme.onSurfaceVariant,
            )
            if (isCurrent) {
                Text(stringResource(R.string.home_now), style = MaterialTheme.typography.labelSmall, color = scheme.primary)
            }
        }
        Text(
            time,
            style = MaterialTheme.typography.titleMedium.merge(Tabular),
            color = if (prayer.isPrayer) scheme.onSurface else scheme.onSurfaceVariant,
        )
        if (prayer.isPrayer) {
            CheckCircle(checked = done, enabled = started, onToggle = onToggle, contentDescription = stringResource(R.string.home_mark_prayed))
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun ActionTile(icon: Int, title: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    ZCard(modifier = modifier, onClick = onClick, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        IconBadge(painterResource(icon), size = 36.dp)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.merge(Tabular),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

@Composable
private fun FocusSummary(usedMillis: Long?, onOpen: () -> Unit, onQuiz: () -> Unit) {
    if (!Store.blockingEnabled || usedMillis == null) {
        ZCard(onClick = onOpen) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(painterResource(R.drawable.ic_nav_focus), container = MaterialTheme.colorScheme.secondaryContainer, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.focus_cta_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.focus_cta_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }
    val usedMin = usedMillis / 60_000
    val allowed = Store.allowedMinutes()
    ZCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = usedMin.toFloat() / allowed.coerceAtLeast(1),
                modifier = Modifier.size(64.dp),
                stroke = 7.dp,
                color = if (usedMin >= allowed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            ) {
                Text("${(usedMin * 100 / allowed.coerceAtLeast(1)).coerceAtMost(999)}%", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.focus_today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.focus_used_of, durationText(usedMin), durationText(allowed.toLong())),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (Store.bonusMinutes() < Store.MAX_BONUS_MINUTES_PER_DAY) {
                    Text(
                        stringResource(R.string.focus_earn_more, Store.BONUS_MINUTES_PER_ROUND),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onQuiz).padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakCard(title: String, value: Int, hint: String, modifier: Modifier = Modifier) {
    ZCard(modifier = modifier, color = MaterialTheme.colorScheme.secondaryContainer, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(daysText(value), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f))
    }
}

/** В пятницу — карточка с аятом или хадисом о пятнице (меняется каждые несколько часов). */
@Composable
private fun FridayCard(today: java.time.LocalDate) {
    val res = LocalContext.current.resources
    val items = com.zikrtime.app.prayer.FridayReminders.itemNumbers
    var index by remember(today) { mutableStateOf((java.time.LocalTime.now().hour / 3) % items.size) }
    val n = items[index]
    ZCard(onClick = { index = (index + 1) % items.size }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(painterResource(R.drawable.ic_shop_book), container = Gold.copy(alpha = 0.35f), tint = Color(0xFF8A6414))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.home_friday_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${index + 1}/${items.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Text("«" + com.zikrtime.app.prayer.FridayReminders.text(res, n) + "»", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            com.zikrtime.app.prayer.FridayReminders.source(res, n),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
