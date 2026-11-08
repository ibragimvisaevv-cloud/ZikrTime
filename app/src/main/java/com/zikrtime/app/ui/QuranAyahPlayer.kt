@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.zikrtime.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zikrtime.app.R
import com.zikrtime.app.audio.QuranAudioPlayer
import com.zikrtime.app.audio.QuranAudioRepository
import com.zikrtime.app.audio.QuranPlaybackState
import com.zikrtime.app.data.QuranSurah
import com.zikrtime.app.i18n.Lang

/** Reading stays lightweight until the user first opens the listening controls. */
@Composable
internal fun QuranReaderAudioConnection(enabled: Boolean): QuranPlaybackState {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val playback by QuranAudioPlayer.state.collectAsStateWithLifecycle()
    DisposableEffect(owner, enabled) {
        var attached = false
        fun attach() {
            if (enabled && !attached) {
                attached = true
                QuranAudioRepository.init(context)
                QuranAudioPlayer.connect(context)
            }
        }
        fun detach() {
            if (attached) { attached = false; QuranAudioPlayer.disconnect() }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) attach()
            if (event == Lifecycle.Event.ON_STOP) detach()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) attach()
        onDispose { owner.lifecycle.removeObserver(observer); detach() }
    }
    return playback
}

/** One selected verse, with audio first and personal actions underneath. Selection alone is silent. */
@Composable
internal fun QuranAyahActionsSheet(
    surah: QuranSurah, ayah: Int, playback: QuranPlaybackState, saved: Boolean,
    close: () -> Unit, selectAyah: (Int) -> Unit, note: () -> Unit,
    bookmark: () -> Unit, copy: () -> Unit, fullSurah: () -> Unit,
) {
    val context = LocalContext.current
    val colors = quranPaperColors()
    val name = if (Lang.content(context) == "ru") surah.nameRu else surah.nameLatin
    val active = playback.surahId == surah.id && playback.ayah == ayah
    var repeats by rememberSaveable(surah.id) { mutableIntStateOf(if (active) playback.repeatCount else 1) }
    var speed by rememberSaveable(surah.id) { mutableFloatStateOf(if (active) playback.speed else 1f) }
    var continueAfter by rememberSaveable(surah.id) { mutableStateOf(active && playback.continueAfter) }
    LaunchedEffect(active, playback.repeatCount, playback.speed, playback.continueAfter) {
        if (active) {
            repeats = playback.repeatCount
            speed = playback.speed
            continueAfter = playback.continueAfter
        }
    }
    fun configure() {
        if (active) QuranAudioPlayer.configureAyah(repeats, speed, continueAfter && repeats != 0)
    }
    val changeVerse: (Int) -> Unit = { target ->
        if (target in 1..surah.ayahCount) {
            if (active && playback.playWhenReady && !playback.ended)
                QuranAudioPlayer.playAyah(surah.id, target, repeats, speed, continueAfter && repeats != 0)
            selectAyah(target)
        }
    }
    ModalBottomSheet(onDismissRequest = close,
        modifier = Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * .88f).dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.canvas, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }.testTag("quran_ayah_sheet")
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.audio_ayah_title, name, ayah), color = colors.ink,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.testTag("ayah_selection_reference").semantics { heading() })
                    Text(stringResource(R.string.qaudio_reciter), color = colors.muted,
                        style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = close, modifier = Modifier.testTag("ayah_close")) {
                    Icon(Icons.Default.Close, stringResource(R.string.qaudio_close))
                }
            }
            if (!active && playback.playWhenReady) Text(stringResource(R.string.ql_reading_now, playback.title),
                color = colors.muted, style = MaterialTheme.typography.bodySmall)
            if (active && playback.isBuffering) LinearProgressIndicator(Modifier.fillMaxWidth())
            if ((active || !playback.connected) && playback.error != null) Text(playback.error!!,
                color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ayah_player_error"))
            Button(onClick = {
                when {
                    active && playback.playWhenReady && !playback.ended && playback.error == null -> QuranAudioPlayer.pause()
                    active && !playback.ended && playback.error == null -> QuranAudioPlayer.togglePlayPause()
                    else -> QuranAudioPlayer.playAyah(surah.id, ayah, repeats, speed, continueAfter && repeats != 0)
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("ayah_play_pause")) {
                Text(stringResource(when {
                    (active || !playback.connected) && playback.error != null -> R.string.qaudio_retry
                    active && playback.playWhenReady && !playback.ended -> R.string.qaudio_pause
                    active && playback.positionMs > 0 && !playback.ended -> R.string.qaudio_resume
                    else -> R.string.ql_listen_ayah
                }))
            }
            Text(stringResource(when {
                active && playback.ended -> R.string.ql_verse_finished
                active && playback.isBuffering -> R.string.qaudio_buffering
                active && playback.isPlaying -> R.string.qaudio_playing
                active && playback.positionMs > 0 -> R.string.qaudio_paused
                !playback.connected -> R.string.qaudio_connecting
                else -> R.string.qaudio_ready
            }), color = colors.accent, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.testTag("ayah_playback_status"))
            if (active && (playback.isPlaying || playback.positionMs > 0 || playback.ended)) {
                Text(if (playback.repeatCount == 0) stringResource(R.string.ql_repeat_progress_forever, playback.repeatIteration)
                    else stringResource(R.string.ql_repeat_progress, playback.repeatIteration, playback.repeatCount),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("ayah_repeat_progress"))
            }
            Text(stringResource(R.string.ql_repeat), style = MaterialTheme.typography.titleSmall)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5, 10, 0).forEach { count ->
                    FilterChip(selected = repeats == count, onClick = {
                        repeats = count
                        if (count == 0) continueAfter = false
                        configure()
                    }, label = { Text(when (count) {
                        0 -> stringResource(R.string.ql_repeat_forever)
                        1 -> stringResource(R.string.ql_repeat_once)
                        else -> stringResource(R.string.ql_repeat_count, count)
                    }) }, modifier = Modifier.testTag("ayah_repeat_$count"))
                }
            }
            QuranSpeedControls(speed, "ayah") { speed = it; configure() }
            Surface(color = colors.paper, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, colors.rule)) {
                Column(Modifier.fillMaxWidth().toggleable(continueAfter, enabled = repeats != 0, role = Role.Checkbox,
                    onValueChange = { continueAfter = it; configure() }).testTag("ayah_continue").padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = continueAfter, onCheckedChange = null, enabled = repeats != 0)
                        Text(stringResource(R.string.ql_continue), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(stringResource(if (repeats == 0) R.string.ql_continue_infinite else R.string.ql_continue_detail),
                        style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
            }
            Text(stringResource(R.string.ql_verse_counter, ayah, surah.ayahCount), color = colors.muted,
                style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { changeVerse(ayah - 1) }, enabled = ayah > 1,
                    modifier = Modifier.testTag("ayah_previous")) { Text(stringResource(R.string.ql_previous_verse)) }
                OutlinedButton(onClick = { changeVerse(ayah + 1) }, enabled = ayah < surah.ayahCount,
                    modifier = Modifier.testTag("ayah_next")) { Text(stringResource(R.string.ql_next_verse)) }
                if (playback.ayah != null) TextButton(onClick = { QuranAudioPlayer.stop() }, modifier = Modifier.testTag("ayah_stop")) {
                    Text(stringResource(R.string.qaudio_stop))
                }
            }
            OutlinedButton(onClick = fullSurah, modifier = Modifier.fillMaxWidth().testTag("ayah_full_surah")) {
                Text(stringResource(R.string.ql_whole_surah))
            }
            HorizontalDivider(color = colors.rule)
            Text(stringResource(R.string.ql_saved_actions), style = MaterialTheme.typography.titleSmall)
            ReaderAction(if (saved) R.string.qt_remove_bookmark else R.string.qt_save_place, "quran_action_bookmark", bookmark)
            ReaderAction(R.string.qt_note, "quran_note_open", note)
            ReaderAction(R.string.qt_copy, "quran_copy_ayah", copy)
            Text(stringResource(R.string.audio_ayah_source), style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
    }
}

@Composable
internal fun QuranSpeedControls(speed: Float, tagPrefix: String, change: (Float) -> Unit) {
    Text(stringResource(R.string.ql_speed), style = MaterialTheme.typography.titleSmall)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(.5f, .75f, 1f, 1.25f, 1.5f).forEach { value ->
            FilterChip(selected = speed == value, onClick = { change(value) },
                label = { Text("${value.toString().removeSuffix(".0")}×") },
                modifier = Modifier.testTag("${tagPrefix}_speed_${(value * 100).toInt()}"))
        }
    }
}
