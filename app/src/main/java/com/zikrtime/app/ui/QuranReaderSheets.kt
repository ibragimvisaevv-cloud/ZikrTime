package com.zikrtime.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.data.*

@Composable
internal fun ReaderAction(label: Int, tag: String, action: () -> Unit) {
    TextButton(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag),
        shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Text(stringResource(label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(12.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun QuranNoteSheet(surahName: String, location: QuranProgress.Location, progress: QuranProgress,
    onSaved: (deleted: Boolean) -> Unit, close: () -> Unit) {
    val original = rememberSaveable(location.key) { progress.note(location) }
    var note by rememberSaveable(location.key) { mutableStateOf(original) }
    var discard by rememberSaveable(location.key) { mutableStateOf(false) }
    var delete by rememberSaveable(location.key) { mutableStateOf(false) }
    var allowDismiss by remember { mutableStateOf(false) }
    val dirty by rememberUpdatedState(note != original)
    val requestClose: () -> Unit = { if (dirty) discard = true else close() }
    // Blocking Hidden keeps the editor on screen when the user cancels a swipe/back dismissal.
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { next ->
        if (next == SheetValue.Hidden && dirty && !allowDismiss) {
            discard = true
            false
        } else true
    })
    ModalBottomSheet(onDismissRequest = requestClose, sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }.imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.qt_note), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = requestClose, modifier = Modifier.testTag("quran_note_close")) {
                    Icon(Icons.Filled.Close, stringResource(R.string.qp_action_close))
                }
            }
            Text(surahName + " · " + location.key, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("quran_note_location"))
            OutlinedTextField(note, { note = it.take(2000) }, minLines = 3, maxLines = 6,
                label = { Text(stringResource(R.string.qf_note)) },
                supportingText = { Text(stringResource(R.string.qp_note_counter, note.length)) },
                modifier = Modifier.fillMaxWidth().testTag("quran_stop_note"))
            Button(onClick = {
                if (note.trim().isEmpty() && original.isNotEmpty()) delete = true
                else {
                    if (note.trim() != original) {
                        progress.saveNote(location, note.trim())
                        onSaved(false)
                    }
                    allowDismiss = true
                    close()
                }
            }, modifier = Modifier.fillMaxWidth().testTag("quran_stop_save")) {
                Text(stringResource(R.string.qt_save_note))
            }
            if (original.isNotEmpty()) TextButton(onClick = { delete = true },
                modifier = Modifier.fillMaxWidth().testTag("quran_note_delete"),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.qp_delete_note))
            }
        }
    }
    if (discard) AlertDialog(
        onDismissRequest = { discard = false },
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        title = { Text(stringResource(R.string.qp_discard_note_title)) },
        text = { Text(stringResource(R.string.qp_discard_note_body)) },
        confirmButton = { TextButton(onClick = { allowDismiss = true; discard = false; close() },
            modifier = Modifier.testTag("quran_note_discard")) { Text(stringResource(R.string.qp_discard)) } },
        dismissButton = { TextButton(onClick = { discard = false },
            modifier = Modifier.testTag("quran_note_keep")) { Text(stringResource(R.string.qp_keep_editing)) } },
    )
    if (delete) AlertDialog(
        onDismissRequest = { delete = false },
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        title = { Text(stringResource(R.string.qp_delete_note_title)) },
        text = { Text(stringResource(R.string.qp_delete_note_body)) },
        confirmButton = { TextButton(onClick = {
            progress.saveNote(location, "")
            allowDismiss = true
            delete = false
            onSaved(true)
            close()
        }, modifier = Modifier.testTag("quran_note_delete_confirm")) { Text(stringResource(R.string.qp_delete)) } },
        dismissButton = { TextButton(onClick = { delete = false },
            modifier = Modifier.testTag("quran_note_delete_cancel")) { Text(stringResource(R.string.qp_note_delete_cancel)) } },
    )
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

