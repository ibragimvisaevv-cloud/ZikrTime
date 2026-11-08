@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.zikrtime.app.ui

import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val zone = PrayerSchedule.zone()
    var now by remember(zone) { mutableStateOf(ZonedDateTime.now(zone)) }
    val today = now.toLocalDate()
    val adjustments = Prayer.entries.map { Store.adjustment(it) }
    val times = remember(today, zone, Store.location, Store.method, Store.madhab, Store.onlineTimes, com.zikrtime.app.prayer.RegionalTimes.preferred, OnlineTimes.version, adjustments) {
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

    HorizonHome(now, next.first, next.second, times) {
        Column(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_today), style = MaterialTheme.typography.headlineSmall)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { nav.open(Screen.PRAYER_JOURNAL) },
                    modifier = Modifier.testTag("home_prayer_journal")) {
                    Text(clarityCopy(UiClarity0825.PrayerHistory))
                }
                TextButton(onClick = { nav.open(Screen.PRAYER_SETTINGS) },
                    modifier = Modifier.testTag("prayer_notification_settings")) {
                    Text(clarityCopy(UiClarity0825.PrayerAlerts))
                }
            }
            Text(clarityCopy(UiClarity0825.MarkPrayerHint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            val prayed = Store.prayedOn(today)
            Prayer.entries.forEach { p ->
                val dayOffset = times[p].toLocalDate().toEpochDay() - today.toEpochDay()
                val clockLabel = when (dayOffset) {
                    -1L -> stringResource(R.string.regional_previous_day, clock(times[p]))
                    1L -> stringResource(R.string.regional_next_day, clock(times[p]))
                    else -> clock(times[p])
                }
                PrayerRow(p, clockLabel, p == current,
                    p == next.first && times[p].toInstant() == next.second.toInstant(),
                    !times[p].isAfter(now), p in prayed, onToggle = {
                        Store.setPrayed(today, p, p !in prayed)
                        com.zikrtime.app.widget.Widgets.updateAll(context)
                    })
            }
            Text(
                scheduleSourceLabel(today, times.polarEstimate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("prayer_schedule_source").semantics { testTagsAsResourceId = true }
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            )
        }

        Column {
            Box(Modifier.testTag("home_quran_open")) {
                DestinationRow(clarityCopy(UiClarity0825.ReadQuran), R.drawable.ic_nav_knowledge,
                    clarityCopy(UiClarity0825.QuranHint)) { nav.open(Screen.QURAN) }
            }
            DestinationRow(clarityCopy(UiClarity0825.ReadingMoments), R.drawable.ic_shop_book,
                clarityCopy(UiClarity0825.ReadingHint)) { nav.open(Screen.READING_REMINDERS) }
        }
        MyDayActions()
        Column(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.tasbih_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.tasbih_subtitle), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (Store.personalProgressEnabled) Text(stringResource(R.string.tasbih_today, Store.tasbihToday(today)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = { nav.open(Screen.TASBIH) }, modifier = Modifier.testTag("home_tasbih_open")) {
                Text(clarityCopy(UiClarity0825.StartDhikr)); Spacer(Modifier.width(8.dp)); Text("→")
            }
        }

        // Быстрые действия
        Column {
            DestinationRow(clarityCopy(UiClarity0825.FindQibla), R.drawable.ic_qibla,
                clarityCopy(UiClarity0825.QiblaHint)) { nav.open(Screen.QIBLA) }
            val alarmAt = if (Store.alarmEnabled) FajrAlarm.nextTrigger(now) else null
            DestinationRow(stringResource(R.string.alarm_short), R.drawable.ic_alarm,
                alarmAt?.let { clock(it) } ?: clarityCopy(UiClarity0825.AlarmOffHint)) { nav.open(Screen.ALARM) }
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
    val large = LocalDensity.current.fontScale > 1.3f
    val line = scheme.outlineVariant
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
        .drawBehind {
            val x = 8.dp.toPx()
            if (prayer != Prayer.FAJR) drawLine(line, androidx.compose.ui.geometry.Offset(x, 0f),
                androidx.compose.ui.geometry.Offset(x, size.height / 2), 1.dp.toPx())
            if (prayer != Prayer.ISHA) drawLine(line, androidx.compose.ui.geometry.Offset(x, size.height / 2),
                androidx.compose.ui.geometry.Offset(x, size.height), 1.dp.toPx())
            drawCircle(if (isCurrent || isNext) scheme.primary else scheme.background,
                (if (isNext) 5.dp else 3.dp).toPx(), androidx.compose.ui.geometry.Offset(x, size.height / 2))
            if (!isCurrent && !isNext) drawCircle(scheme.outline, 3.dp.toPx(), androidx.compose.ui.geometry.Offset(x, size.height / 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }.padding(start = 28.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (prayer == Prayer.SUNRISE) Icon(painterResource(prayerIconRes(prayer)), null,
                    tint = scheme.secondary, modifier = Modifier.size(18.dp))
                Text(prayerName(prayer), style = MaterialTheme.typography.titleMedium,
                    color = if (isNext) scheme.primary else scheme.onSurface)
            }
            if (isCurrent || isNext) Text(stringResource(if (isCurrent) R.string.home_now else R.string.home_next_prayer),
                style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            if (large) Text(time, style = MaterialTheme.typography.titleLarge.merge(Tabular))
        }
        if (!large) Text(time, style = MaterialTheme.typography.titleLarge.merge(Tabular),
            color = if (isNext) scheme.primary else scheme.onSurface, fontWeight = FontWeight.Medium)
        if (prayer.isPrayer) CheckCircle(checked = done, enabled = started, onToggle = onToggle,
            contentDescription = prayerName(prayer) + ": " + stringResource(R.string.home_mark_prayed))
        else Spacer(Modifier.size(48.dp))
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
        ZCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(painterResource(R.drawable.ic_nav_focus), container = MaterialTheme.colorScheme.secondaryContainer, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(clarityCopy(UiClarity0825.AppLimits), style = MaterialTheme.typography.titleMedium)
                    Text(clarityCopy(UiClarity0825.AppLimitsHint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onOpen, modifier = Modifier.testTag("home_set_limits")) {
                Text(clarityCopy(UiClarity0825.SetLimit))
            }
        }
        return
    }
    val usedMin = usedMillis / 60_000
    val allowed = Store.allowedMinutes()
    ZCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(painterResource(R.drawable.ic_nav_focus), size = 40.dp)
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
        TextButton(onClick = onOpen, modifier = Modifier.testTag("home_set_limits")) {
            Text(clarityCopy(UiClarity0825.SetLimit))
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
            IconBadge(painterResource(R.drawable.ic_shop_book), container = MaterialTheme.colorScheme.primaryContainer, tint = MaterialTheme.colorScheme.primary)
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
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(clarityCopy(UiClarity0825.Questions), style = MaterialTheme.typography.titleMedium)
        Text(clarityCopy(UiClarity0825.QuestionsHint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Prayer.FAJR, Prayer.ISHA -> MaterialTheme.colorScheme.primary
        Prayer.SUNRISE, Prayer.DHUHR -> MaterialTheme.colorScheme.secondary
        Prayer.ASR, Prayer.MAGHRIB -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
