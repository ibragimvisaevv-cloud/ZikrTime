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
internal fun QuranNoteSheet(surahName: String, location: QuranProgress.Location, progress: QuranProgress, close: () -> Unit) {
    var note by rememberSaveable(location.key) { mutableStateOf(progress.note(location)) }
    HorizonSheet(stringResource(R.string.qt_note), close) {
        Text(surahName + " · " + location.key, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("quran_note_location"))
        OutlinedTextField(note, { note = it.take(2000) }, minLines = 3, maxLines = 6,
            label = { Text(stringResource(R.string.qf_note)) }, modifier = Modifier.fillMaxWidth().testTag("quran_stop_note"))
        PrimaryButton(stringResource(R.string.qt_save_note), modifier = Modifier.testTag("quran_stop_save"), onClick = {
            progress.saveNote(location, note.trim())
            close()
        })
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
            else { session.changeTimer(QuranReadingTimer(count * 60_000L)); close() }
        })
        if (session.timer.remaining > 0 || session.timer.paused) ReaderAction(
            if (session.timer.paused) R.string.qf_resume else R.string.qf_pause, "quran_timer_pause") {
            session.changeTimer(session.timer.copy(paused = !session.timer.paused)); close()
        }
        ReaderAction(R.string.qf_timer_cancel, "quran_timer_cancel") { session.changeTimer(QuranReadingTimer()); close() }
    }
}
