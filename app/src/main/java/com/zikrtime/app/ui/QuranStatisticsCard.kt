package com.zikrtime.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuranStatisticsCard(book: QuranBook? = null) {
    val context = LocalContext.current
    val progress = remember { QuranProgress(context) }
    val raw = Store.getRaw(QuranStatistics.KEY)
    val history = remember(raw) { QuranReadingHistory.decode(raw) }
    var period by rememberSaveable { mutableIntStateOf(7) }
    var goal by remember { mutableIntStateOf(progress.dailyGoalMinutes) }
    val unique = remember(history) { history.uniqueVerses }
    val today = LocalDate.now()
    val todayTime = history.days[today]?.millis ?: 0L
    val recent = history.days.filter { it.key in today.minusDays(period - 1L)..today }
    val totalTime = recent.values.sumOf { it.millis }
    val read = recent.values.flatMap { it.verses }.toSet().size
    ZCard(modifier = Modifier.testTag("quran_statistics")) {
        Text(stringResource(R.string.qf_statistics), style = MaterialTheme.typography.titleLarge)
        if (!Store.personalProgressEnabled) Text(stringResource(R.string.qf_stats_disabled))
        Text(stringResource(R.string.qf_today, (todayTime / 60_000).toInt(), goal), style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(progress = { (todayTime / (goal * 60_000f)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.qf_daily_goal, goal))
        Slider(goal.toFloat(), {
            goal = it.toInt().coerceIn(1, 180)
            progress.dailyGoalMinutes = goal
        }, valueRange = 1f..180f, steps = 178, modifier = Modifier.testTag("quran_daily_goal"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90).forEach { d ->
                FilterChip(period == d, { period = d }, label = { Text(daysText(d)) })
            }
        }
        Text(stringResource(R.string.qf_total_time, readingClock(totalTime)), modifier = Modifier.testTag("quran_stats_time"))
        Text(stringResource(R.string.qf_read_verses, read), modifier = Modifier.testTag("quran_stats_verses"))
        Text(stringResource(R.string.qf_active_days, recent.values.count { it.millis > 0 || it.verses.isNotEmpty() }))
        Text(stringResource(R.string.qf_unique_total, unique.size), modifier = Modifier.testTag("quran_stats_unique"))
        if (book != null) {
            val completed = book.surahs.count { surah -> (1..surah.ayahCount).all { surah.id.toString() + ":" + it in unique } }
            Text(stringResource(R.string.qf_completed_surahs, completed))
        }
        HorizontalDivider()
        Text(stringResource(R.string.qf_last_week), style = MaterialTheme.typography.titleSmall)
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
        (6 downTo 0).forEach { offset ->
            val day = today.minusDays(offset.toLong())
            val entry = history.days[day] ?: QuranReadingDay()
            Text(day.format(dateFormat) + " · " + readingClock(entry.millis) + " · " + stringResource(R.string.qf_read_verses, entry.verses.size),
                style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.qf_measurement_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
