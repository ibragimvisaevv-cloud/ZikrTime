@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.zikrtime.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.data.PrayerMark
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.Prayer
import com.zikrtime.app.prayer.PrayerSchedule
import com.zikrtime.app.widget.Widgets
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZonedDateTime

@Composable
fun JournalHomeActions() {
    val nav = LocalNav.current
    IconButton({ nav.open(Screen.PRAYER_JOURNAL) }, Modifier.testTag("home_prayer_journal")) {
        Icon(painterResource(R.drawable.ic_prayer_rug), stringResource(R.string.journal_title), Modifier.size(24.dp))
    }
}
@Composable
fun ReadingHomeAction(tint: androidx.compose.ui.graphics.Color) {
    val nav = LocalNav.current
    IconButton({ nav.open(Screen.READING_REMINDERS) }, Modifier.testTag("home_reading_reminders")) {
        Icon(painterResource(R.drawable.ic_shop_book), stringResource(R.string.reading_title), Modifier.size(24.dp), tint = tint)
    }
}

@Composable
private fun journalDate(day: LocalDate): String = day.format(java.time.format.DateTimeFormatter
    .ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(com.zikrtime.app.i18n.Lang.locale(LocalContext.current)))

private val PrayerMark.label get() = when (this) {
    PrayerMark.DONE -> R.string.journal_done
    PrayerMark.MISSED -> R.string.journal_missed
    PrayerMark.MADE_UP -> R.string.journal_made_up
    PrayerMark.UNMARKED -> R.string.journal_unmarked
}

@Composable
fun PrayerJournalScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val zone = PrayerSchedule.zone()
    var now by remember(zone) { mutableStateOf(ZonedDateTime.now(zone)) }
    LaunchedEffect(zone, AppEvents.resumeCount) {
        while (true) { now = ZonedDateTime.now(zone); delay(30_000) }
    }
    val today = now.toLocalDate()
    var selectedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val day = selectedDay?.let(LocalDate::ofEpochDay)?.coerceAtMost(today) ?: today
    var days by rememberSaveable { mutableIntStateOf(7) }
    var edit by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLimit by rememberSaveable { mutableIntStateOf(5) }
    ScreenScaffold(stringResource(R.string.journal_title), { nav.back() }, showHeading = false) {
        if (!Store.personalProgressEnabled) {
            ZCard { PersonalProgressSetting() }
            return@ScreenScaffold
        }
        val todayTimes = PrayerSchedule.forDate(today, zone)
        val yesterday = today.minusDays(1)
        val yesterdayTimes = PrayerSchedule.forDate(yesterday, zone)
        fun started(d: LocalDate, p: Prayer): Boolean = when (d) {
            today -> !todayTimes[p].isAfter(now)
            yesterday -> !yesterdayTimes[p].isAfter(now)
            else -> d < today
        }
        val counts = PrayerMark.entries.associateWith { 0 }.toMutableMap()
        repeat(days) { offset ->
            val d = today.minusDays(offset.toLong())
            val marks = Store.prayerMarks(d)
            Prayer.obligatory.filter { started(d, it) }.forEach { prayer ->
                val status = marks[prayer] ?: PrayerMark.UNMARKED
                counts[status] = counts.getValue(status) + 1
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in listOf(7, 30)) FilterChip(days == n, { days = n }, label = { Text(daysText(n)) })
            }
            ZCard(padding = PaddingValues(16.dp)) {
                for ((index, mark) in listOf(PrayerMark.DONE, PrayerMark.MADE_UP, PrayerMark.MISSED, PrayerMark.UNMARKED).withIndex()) {
                    if (index > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(mark.label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(counts.getValue(mark).toString(), Modifier.testTag("journal_count_" + mark.name), style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Text(stringResource(R.string.journal_note), style = MaterialTheme.typography.bodySmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(R.string.journal_day))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { selectedDay = day.minusDays(1).toEpochDay() }, modifier = Modifier.testTag("journal_previous")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.journal_previous))
                }
                OutlinedButton(onClick = {
                    DatePickerDialog(context, { _, y, m, d ->
                        selectedDay = LocalDate.of(y, m + 1, d).coerceAtMost(today).toEpochDay()
                    }, day.year, day.monthValue - 1, day.dayOfMonth).apply {
                        // The platform picker interprets these milliseconds in the phone's zone.
                        datePicker.maxDate = today.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                    }.show()
                }, modifier = Modifier.weight(1f).testTag("journal_date")) { Text(journalDate(day)) }
                IconButton(onClick = { selectedDay = day.plusDays(1).toEpochDay() }, enabled = day < today,
                    modifier = Modifier.testTag("journal_next")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.journal_next))
                }
            }
            val marks = Store.prayerMarks(day)
            ZCard(padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Prayer.obligatory.forEachIndexed { index, prayer ->
                    if (index > 0) HorizontalDivider()
                    val available = started(day, prayer)
                    val status = marks[prayer] ?: PrayerMark.UNMARKED
                    Row(Modifier.fillMaxWidth().testTag("journal_" + prayer.name)
                        .clickable(enabled = available, role = Role.Button) { edit = prayer.name }
                        .padding(vertical = 13.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(prayerIconRes(prayer)), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(prayerName(prayer), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(if (available) status.label else R.string.journal_upcoming),
                                modifier = Modifier.testTag("journal_state_" + prayer.name + "_" + status.name),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        val pendingDates = Store.pendingPrayerDates().filter { it <= today }
        SectionLabel(stringResource(R.string.journal_pending))
        if (pendingDates.isEmpty()) Text(stringResource(R.string.journal_no_pending))
        else {
            ZCard(padding = PaddingValues(12.dp)) {
                pendingDates.take(pendingLimit).forEach { d ->
                    val missing = Store.prayerMarks(d).filterValues { it == PrayerMark.MISSED }.keys
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(journalDate(d), style = MaterialTheme.typography.titleSmall)
                        for (prayer in missing) {
                            TextButton(onClick = { selectedDay = d.toEpochDay(); edit = prayer.name }) {
                                Text(prayerName(prayer))
                            }
                        }
                    }
                }
                if (pendingDates.size > pendingLimit) TextButton(onClick = { pendingLimit += 10 }) {
                    Text(stringResource(R.string.journal_more))
                }
            }
        }
        val prayer = Prayer.obligatory.firstOrNull { it.name == edit }
        if (prayer != null && started(day, prayer)) {
            val status = Store.prayerMarks(day)[prayer] ?: PrayerMark.UNMARKED
            val times = when (day) { today -> todayTimes; yesterday -> yesterdayTimes; else -> null }
            val ended = times == null || !when (prayer) {
                Prayer.FAJR -> times[Prayer.SUNRISE]
                Prayer.DHUHR -> times[Prayer.ASR]
                Prayer.ASR -> times[Prayer.MAGHRIB]
                Prayer.MAGHRIB -> times[Prayer.ISHA]
                Prayer.ISHA -> PrayerSchedule.forDate(day.plusDays(1), zone)[Prayer.FAJR]
                Prayer.SUNRISE -> error("Sunrise is not a prayer")
            }.isAfter(now)
            AlertDialog(onDismissRequest = { edit = null }, modifier = Modifier.semantics { testTagsAsResourceId = true }, title = { Text(prayerName(prayer)) }, text = {
                Column {
                    Text(journalDate(day), style = MaterialTheme.typography.bodyMedium)
                    listOf(PrayerMark.DONE, PrayerMark.MISSED, PrayerMark.MADE_UP, PrayerMark.UNMARKED).forEach { mark ->
                        val allowed = ended || mark == PrayerMark.DONE || mark == PrayerMark.UNMARKED || status == mark
                        Row(Modifier.fillMaxWidth().testTag("journal_" + mark.name)
                            .selectable(selected = mark == status, enabled = allowed, role = Role.RadioButton, onClick = {
                                Store.setPrayerMark(day, prayer, mark)
                                Widgets.updateAll(context)
                                edit = null
                            }).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mark == status, onClick = null, enabled = allowed)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(mark.label))
                        }
                    }
                }
            }, confirmButton = { TextButton({ edit = null }) { Text(stringResource(R.string.action_close)) } })
        }
    }
}
