package com.zikrtime.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.data.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.ceil

/** Presentation only: saved observations and time retain their existing meaning. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuranStatisticsCard(book: QuranBook? = null, showTitle: Boolean = true) {
    val context = LocalContext.current
    val progress = remember { QuranProgress(context) }
    val raw = Store.getRaw(QuranStatistics.KEY)
    val history = remember(raw) { QuranReadingHistory.decode(raw) }
    var period by rememberSaveable { mutableIntStateOf(7) }
    var goal by remember { mutableIntStateOf(progress.dailyGoalMinutes) }
    var editGoal by rememberSaveable { mutableStateOf(false) }
    val unique = remember(history) { history.uniqueVerses }
    val today = LocalDate.now()
    val todayTime = history.days[today]?.millis ?: 0L
    val recent = history.days.filter { it.key in today.minusDays(period - 1L)..today }
    val totalTime = recent.values.sumOf { it.millis }
    val read = recent.values.flatMap { it.verses }.toSet().size
    val activeDays = recent.values.count { it.millis > 0 || it.verses.isNotEmpty() }
    val empty = history.days.values.none { it.millis > 0 || it.verses.isNotEmpty() }
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }

    Column(Modifier.fillMaxWidth().testTag("quran_statistics"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (showTitle) Text(stringResource(R.string.qf_statistics), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() })
        if (!Store.personalProgressEnabled) {
            Surface(color = colors.surfaceVariant, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().testTag("quran_stats_disabled")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.qs_paused), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.qf_stats_disabled), style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant)
                    TextButton(onClick = { Store.personalProgressEnabled = true },
                        modifier = Modifier.testTag("quran_stats_enable")) {
                        Text(stringResource(R.string.qs_enable_progress))
                    }
                }
            }
        }
        QuranStatsSection(stringResource(R.string.qs_today)) {
            Text(quranStatsDuration(todayTime), style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold, color = colors.primary,
                modifier = Modifier.testTag("quran_stats_today_time"))
            Text(stringResource(R.string.qf_daily_goal, goal), style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant, modifier = Modifier.testTag("quran_stats_today_goal"))
            LinearProgressIndicator(progress = { (todayTime / (goal * 60_000f)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                trackColor = colors.surfaceVariant)
            if (Store.personalProgressEnabled) Text(if (todayTime >= goal * 60_000L) stringResource(R.string.qs_goal_complete)
                else if (todayTime == 0L) stringResource(R.string.qs_today_start)
                else stringResource(R.string.qs_goal_remaining, ceil((goal * 60_000L - todayTime) / 60_000.0).toInt()),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            OutlinedButton(onClick = { editGoal = true }, modifier = Modifier.testTag("quran_goal_open")) {
                Text(stringResource(R.string.qs_change_goal))
            }
        }
        if (empty && Store.personalProgressEnabled) Surface(color = colors.secondaryContainer, shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("quran_stats_empty")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.qs_empty_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.qs_empty_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
        QuranStatsSection(stringResource(R.string.qs_period)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90).forEach { days ->
                    FilterChip(selected = period == days, onClick = { period = days },
                        leadingIcon = if (period == days) { { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) } } else null,
                        label = { Text(daysText(days)) }, modifier = Modifier.testTag("quran_stats_period_$days"))
                }
            }
            Text(today.minusDays(period - 1L).format(dateFormat) + " — " + today.format(dateFormat),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            QuranStatsMetric(quranStatsDuration(totalTime), stringResource(R.string.qs_reading_time),
                "quran_stats_time")
            val largeText = LocalDensity.current.fontScale > 1.25f
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 300.dp || largeText) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuranStatsMetric(read.toString(), stringResource(R.string.qs_observed_ayahs), "quran_stats_verses")
                        QuranStatsMetric("$activeDays / $period", stringResource(R.string.qs_active_days), "quran_stats_active_days")
                    }
                } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuranStatsMetric(read.toString(), stringResource(R.string.qs_observed_ayahs),
                        "quran_stats_verses", Modifier.weight(1f))
                    QuranStatsMetric("$activeDays / $period", stringResource(R.string.qs_active_days),
                        "quran_stats_active_days", Modifier.weight(1f))
                }
            }
            Text(stringResource(R.string.qs_unique_hint), style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
        }
        QuranStatsSection(stringResource(R.string.qf_last_week)) {
            QuranStatsWeek(history, today)
        }
        QuranStatsSection(stringResource(R.string.qs_all_time)) {
            val totalVerses = book?.surahs?.sumOf { it.ayahCount } ?: 6236
            val fraction = (unique.size.toFloat() / totalVerses.coerceAtLeast(1)).coerceIn(0f, 1f)
            val percent = remember(locale, fraction) {
                NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }.format(fraction.toDouble())
            }
            Text(percent, style = MaterialTheme.typography.headlineLarge, color = colors.primary,
                fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth()
                .height(8.dp).clip(RoundedCornerShape(4.dp)), trackColor = colors.surfaceVariant)
            QuranStatsMetric("${unique.size} / $totalVerses", stringResource(R.string.qs_observed_ayahs),
                "quran_stats_unique")
            if (book != null) {
                val completed = book.surahs.count { surah ->
                    (1..surah.ayahCount).all { surah.id.toString() + ":" + it in unique }
                }
                QuranStatsMetric("$completed / ${book.surahs.size}", stringResource(R.string.qs_complete_surahs),
                    "quran_stats_surahs")
            }
        }
        Text(stringResource(R.string.qf_measurement_note), style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    }
    if (editGoal) QuranGoalDialog(goal, onDismiss = { editGoal = false }) { value ->
        progress.dailyGoalMinutes = value
        goal = value
        editGoal = false
    }
}

@Composable
private fun QuranStatsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HorizontalDivider(color = colors.outlineVariant)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun QuranStatsMetric(value: String, label: String, tag: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag(tag).semantics(mergeDescendants = true) {}
        .padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun quranStatsDuration(millis: Long): String {
    val minutes = millis.coerceAtLeast(0) / 60_000
    return when {
        millis in 1..59_999 -> stringResource(R.string.qs_less_minute)
        minutes < 60 -> stringResource(R.string.qs_duration_minutes, minutes)
        else -> stringResource(R.string.qs_duration_hours, minutes / 60, minutes % 60)
    }
}

@Composable
private fun QuranStatsWeek(history: QuranReadingHistory, today: LocalDate) {
    val locale = LocalConfiguration.current.locales[0]
    val shortDay = remember(locale) { DateTimeFormatter.ofPattern("EEE", locale) }
    val fullDate = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    val rowDate = remember(locale) { DateTimeFormatter.ofPattern("EEE, d MMM", locale) }
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val maxMillis = days.maxOf { history.days[it]?.millis ?: 0 }.coerceAtLeast(60_000)
    val largeText = LocalDensity.current.fontScale > 1.25f
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("quran_stats_week")) {
        if (maxWidth < 280.dp || largeText) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                days.forEach { day ->
                    val entry = history.days[day] ?: QuranReadingDay()
                    Column(Modifier.fillMaxWidth().testTag("quran_stats_day_$day").semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(day.format(rowDate), style = MaterialTheme.typography.labelLarge,
                            color = if (day == today) colors.primary else colors.onSurfaceVariant)
                        Text(quranStatsDuration(entry.millis), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.qf_read_verses, entry.verses.size),
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        LinearProgressIndicator(progress = { entry.millis.toFloat() / maxMillis },
                            modifier = Modifier.fillMaxWidth().height(4.dp), trackColor = colors.surfaceVariant)
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.qs_week_minutes), style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom) {
                    days.forEach { day ->
                        val entry = history.days[day] ?: QuranReadingDay()
                        val duration = quranStatsDuration(entry.millis)
                        val description = day.format(fullDate) + ", " + duration + ", " +
                            stringResource(R.string.qf_read_verses, entry.verses.size)
                        Column(Modifier.weight(1f).testTag("quran_stats_day_$day").clearAndSetSemantics { contentDescription = description },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (entry.millis in 1..59_999) "<1" else (entry.millis / 60_000).toString(),
                                style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                            Box(Modifier.height(88.dp).width(18.dp).clip(RoundedCornerShape(6.dp))
                                .background(colors.surfaceVariant), contentAlignment = Alignment.BottomCenter) {
                                if (entry.millis > 0) Box(Modifier.fillMaxWidth()
                                    .fillMaxHeight((entry.millis.toFloat() / maxMillis).coerceIn(.025f, 1f))
                                    .background(if (day == today) colors.primary else colors.primary.copy(alpha = .62f)))
                            }
                            Text(day.format(shortDay), style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center, color = if (day == today) colors.primary else colors.onSurfaceVariant)
                            Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                                fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun QuranGoalDialog(goal: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var draft by rememberSaveable { mutableStateOf(goal.toString()) }
    val value = draft.toIntOrNull()
    val valid = value != null && value in 1..180
    AlertDialog(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.qs_goal_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.qs_goal_hint), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30).forEach { minutes ->
                        RhythmChoiceChip(selected = value == minutes, onClick = { draft = minutes.toString() },
                            label = { Text(stringResource(R.string.qf_minutes, minutes)) },
                            modifier = Modifier.testTag("quran_goal_preset_$minutes"))
                    }
                }
                OutlinedTextField(value = draft, onValueChange = { input ->
                    if (input.length <= 3 && input.all { it in '0'..'9' }) draft = input
                }, singleLine = true, label = { Text(stringResource(R.string.qs_goal_minutes)) },
                    supportingText = { Text(stringResource(R.string.qf_duration_error)) },
                    isError = draft.isNotBlank() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("quran_daily_goal"))
            }
        }, confirmButton = {
            TextButton(onClick = { value?.takeIf { valid }?.let(onSave) }, enabled = valid,
                modifier = Modifier.testTag("quran_goal_save")) { Text(stringResource(R.string.qs_goal_save)) }
        }, dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        })
}
