@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
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
import com.zikrtime.app.quiz.QuestionBank
import com.zikrtime.app.quiz.LearningStore
import com.zikrtime.app.data.AzkarStore
import androidx.compose.material3.TextButton
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

    TabColumn(spacing = 10.dp) {
        NaturalSkyHero(now, next.first, next.second, times)
        // Намазы на сегодня
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.home_today),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp, top = 0.dp, bottom = 8.dp),
            )
            val prayed = Store.prayedOn(today)
            Prayer.entries.forEach { p ->
                PrayerRow(
                    prayer = p,
                    time = clock(times[p]),
                    isCurrent = p == current,
                    isNext = current == null && next.second.toLocalDate() == today && p == next.first,
                    started = !times[p].isAfter(now),
                    done = p in prayed,
                    onToggle = {
                        Store.setPrayed(today, p, p !in prayed)
                        com.zikrtime.app.widget.Widgets.updateAll(context)
                    },
                )
                if (p != Prayer.ISHA) androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(start = 52.dp, end = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
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

        MyDayActions()
        ZCard(onClick = { nav.open(Screen.TASBIH) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                androidx.compose.foundation.Image(painterResource(R.drawable.stone_bead), null, Modifier.size(64.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tasbih_title), style = MaterialTheme.typography.titleLarge)
                    if (Store.personalProgressEnabled) Text(stringResource(R.string.tasbih_today, Store.tasbihToday(today)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Text(stringResource(R.string.tasbih_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›", fontSize = 28.sp)
            }
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
                value = (if (Store.personalProgressEnabled) Store.tasbihToday(today) else Store.tasbihCount).toString(),
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

        if (Store.personalProgressEnabled) {
            val summary = Store.personalProgressSummary(today, 30)
            if (summary.sessions > 0 || summary.reviewed > 0 || Store.totalAnswered > 0) {
                ZCard(onClick = { nav.open(Screen.PROGRESS) }) {
                    Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.progress_home_summary, summary.sessions, summary.activeDays), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PrayerRow(prayer: Prayer, time: String, isCurrent: Boolean, isNext: Boolean, started: Boolean, done: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val highlighted = isCurrent || isNext
    val accent = prayerAccent(prayer)
    val ink = if (highlighted) Porcelain else scheme.onSurface
    val line = scheme.outlineVariant
    Row(Modifier.fillMaxWidth()
        .drawBehind {
            val x = 24.dp.toPx()
            drawLine(line, androidx.compose.ui.geometry.Offset(x, if (prayer == Prayer.FAJR) size.height / 2 else 0f),
                androidx.compose.ui.geometry.Offset(x, if (prayer == Prayer.ISHA) size.height / 2 else size.height), 1.dp.toPx())
        }
        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 22.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
        .background(if (highlighted) Olive else Color.Transparent)
        .padding(start = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(painterResource(prayerIconRes(prayer)), tint = if (highlighted) Sand else accent,
            container = if (highlighted) Color(0xFF526245) else scheme.surfaceContainerHigh, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(prayerName(prayer), style = MaterialTheme.typography.titleMedium,
                color = ink, fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium)
            if (highlighted) Text(stringResource(if (isCurrent) R.string.home_now else R.string.home_next_prayer), style = MaterialTheme.typography.labelSmall, color = Sand)
        }
        Text(time, style = MaterialTheme.typography.titleLarge.merge(Tabular), color = ink,
            fontWeight = FontWeight.SemiBold)
        if (prayer.isPrayer) {
            CheckCircle(checked = done, enabled = started, onToggle = onToggle,
                contentDescription = stringResource(R.string.home_mark_prayed), onDark = highlighted)
        } else Spacer(Modifier.width(48.dp).height(48.dp))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyDayActions() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val bank = QuestionBank.load(context)
    val pending = LearningStore.pendingSession(bank)
    val due = LearningStore.dueCount(bank)
    ZCard {
        Text(stringResource(R.string.my_day_title), style = MaterialTheme.typography.titleMedium)
        if (pending != null) {
            TextButton(onClick = { nav.resumeLearning(context) }, modifier = Modifier.testTag("my_day_resume_learning").semantics { testTagsAsResourceId = true }) { Text(stringResource(R.string.my_day_resume_learning)) }
        } else {
            TextButton(onClick = { nav.startQuiz(null) }, modifier = Modifier.testTag("my_day_start_learning").semantics { testTagsAsResourceId = true }) { Text(stringResource(R.string.my_day_start_learning)) }
        }
        if (due > 0) {
            TextButton(onClick = { nav.startReview() }) { Text(stringResource(R.string.my_day_review, due)) }
        }
    }
}

@Composable
private fun prayerAccent(prayer: Prayer): Color {
    val dark = com.zikrtime.app.ui.theme.LocalDarkTheme.current
    return when (prayer) {
        Prayer.FAJR, Prayer.ISHA -> if (dark) Color(0xFFBCCBA5) else Olive
        Prayer.SUNRISE, Prayer.DHUHR -> if (dark) Sand else Color(0xFF98602E)
        Prayer.ASR, Prayer.MAGHRIB -> if (dark) Color(0xFFE6AC8B) else Clay
    }
}
