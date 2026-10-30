package com.zikrtime.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.data.*
import java.time.LocalDate

@Composable
internal fun ReaderAction(label: Int, tag: String, action: () -> Unit) {
    OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(stringResource(label)) }
}

@Composable
internal fun QuranJumpSheet(count: Int, current: Int, close: () -> Unit, jumpTo: (Int) -> Unit) {
    var value by rememberSaveable { mutableStateOf(current.toString()) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    HorizonSheet(stringResource(R.string.qf_jump), close) {
        OutlinedTextField(value, { value = it.take(3); invalid = false }, singleLine = true, isError = invalid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(stringResource(R.string.quran_ayah)) }, modifier = Modifier.fillMaxWidth().testTag("quran_ayah_input"))
        if (invalid) Text(stringResource(R.string.quran_jump_error, count), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("quran_jump_error"))
        PrimaryButton(stringResource(R.string.quran_go), modifier = Modifier.testTag("quran_go"), onClick = {
            val number = value.toIntOrNull()
            if (number == null || number !in 1..count) invalid = true else jumpTo(number)
        })
    }
}

@Composable
internal fun QuranStopSheet(book: QuranBook, surahId: Int, current: Int, progress: QuranProgress, close: () -> Unit) {
    var value by rememberSaveable { mutableStateOf(current.toString()) }
    var note by rememberSaveable { mutableStateOf(progress.note(QuranProgress.Location(surahId, current))) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val count = book.surahs.first { it.id == surahId }.ayahCount
    HorizonSheet(stringResource(R.string.qf_stop), close) {
        Text(stringResource(R.string.qf_stop_hint))
        OutlinedTextField(value, {
            value = it.take(3); invalid = false
            val number = value.toIntOrNull()
            note = if (number != null && number in 1..count) progress.note(QuranProgress.Location(surahId, number)) else ""
        }, singleLine = true, isError = invalid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(stringResource(R.string.quran_ayah)) }, modifier = Modifier.fillMaxWidth().testTag("quran_stop_ayah"))
        OutlinedTextField(note, { note = it.take(2000) }, minLines = 3, maxLines = 6,
            label = { Text(stringResource(R.string.qf_note)) }, modifier = Modifier.fillMaxWidth().testTag("quran_stop_note"))
        if (invalid) Text(stringResource(R.string.quran_jump_error, count), color = MaterialTheme.colorScheme.error)
        PrimaryButton(stringResource(R.string.qf_save_stop), modifier = Modifier.testTag("quran_stop_save"), onClick = {
            val number = value.toIntOrNull()
            if (number == null || number !in 1..count) invalid = true
            else { progress.saveStop(QuranProgress.Location(surahId, number), note.trim()); close() }
        })
    }
}

@Composable
internal fun QuranMarkSheet(surahId: Int, current: Int, count: Int, close: () -> Unit) {
    var first by rememberSaveable { mutableStateOf("1") }
    var last by rememberSaveable { mutableStateOf(current.toString()) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    HorizonSheet(stringResource(R.string.qf_mark_read), close) {
        Text(stringResource(R.string.qf_mark_hint))
        if (!Store.personalProgressEnabled) {
            Text(stringResource(R.string.qf_stats_disabled))
            PersonalProgressSetting()
        }
        OutlinedTextField(first, { first = it.take(3); invalid = false }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(stringResource(R.string.qf_first_ayah)) }, modifier = Modifier.fillMaxWidth().testTag("quran_mark_first"))
        OutlinedTextField(last, { last = it.take(3); invalid = false }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(stringResource(R.string.qf_last_ayah)) }, modifier = Modifier.fillMaxWidth().testTag("quran_mark_last"))
        if (invalid) Text(stringResource(R.string.qf_range_error, count), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("quran_mark_error"))
        Button(onClick = {
            val from = first.toIntOrNull()
            val to = last.toIntOrNull()
            if (from == null || to == null || from !in 1..count || to !in from..count) invalid = true
            else { QuranStatistics.mark(LocalDate.now(), surahId, from, to, count); close() }
        }, enabled = Store.personalProgressEnabled, modifier = Modifier.fillMaxWidth().testTag("quran_mark_save")) {
            Text(stringResource(R.string.qf_mark_read))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuranTimerSheet(progress: QuranProgress, session: QuranSession, close: () -> Unit) {
    var minutes by rememberSaveable { mutableStateOf(progress.dailyGoalMinutes.toString()) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    HorizonSheet(stringResource(R.string.qf_timer), close) {
        Text(stringResource(R.string.qf_timer_hint))
        Text(readingClock(session.timer.remaining), style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("quran_timer_value"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 30).forEach { m ->
                FilterChip(minutes == m.toString(), { minutes = m.toString(); invalid = false }, label = { Text(stringResource(R.string.qf_minutes, m)) })
            }
        }
        OutlinedTextField(minutes, { minutes = it.take(3); invalid = false }, singleLine = true, isError = invalid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(stringResource(R.string.qf_duration)) }, modifier = Modifier.fillMaxWidth().testTag("quran_timer_minutes"))
        if (invalid) Text(stringResource(R.string.qf_duration_error), color = MaterialTheme.colorScheme.error)
        PrimaryButton(stringResource(R.string.qf_timer_start), modifier = Modifier.testTag("quran_timer_start"), onClick = {
            val count = minutes.toIntOrNull()
            if (count == null || count !in 1..180) invalid = true
            else { session.setTimer(QuranReadingTimer(count * 60_000L)); close() }
        })
        if (session.timer.remaining > 0 || session.timer.paused) ReaderAction(
            if (session.timer.paused) R.string.qf_resume else R.string.qf_pause, "quran_timer_pause") {
            session.setTimer(session.timer.copy(paused = !session.timer.paused)); close()
        }
        ReaderAction(R.string.qf_timer_cancel, "quran_timer_cancel") { session.setTimer(QuranReadingTimer()); close() }
    }
}
