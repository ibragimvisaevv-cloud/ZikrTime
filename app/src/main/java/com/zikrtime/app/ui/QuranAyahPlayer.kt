@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.zikrtime.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import com.zikrtime.app.audio.QuranPlaybackService
import com.zikrtime.app.audio.QuranPlaybackState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal data class QuranReaderPlayback(
    val surahId: Int?, val playingAyah: Int?, val playingWord: Int?, val positionAyah: Int?, val clipAyah: Int?,
    val continueAfter: Boolean, val repeatCount: Int, val speed: Float, val playing: Boolean, val connected: Boolean,
    val ended: Boolean, val buffering: Boolean, val playWhenReady: Boolean, val error: String?,
)

private fun QuranPlaybackState.readerPlayback() = QuranReaderPlayback(
    surahId, playingAyah, playingWord, positionAyah, ayah, continueAfter, repeatCount, speed,
    connected && isPlaying, connected, ended, isBuffering, playWhenReady, error,
)

/** Observe an existing service when opening the reader. Silent reading creates no playback service. */
@Composable
internal fun QuranReaderAudioConnection(enabled: Boolean): QuranReaderPlayback {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val changes = remember { QuranAudioPlayer.state.map { it.readerPlayback() }.distinctUntilChanged() }
    val playback by changes.collectAsStateWithLifecycle(initialValue = QuranAudioPlayer.state.value.readerPlayback())
    DisposableEffect(owner, enabled) {
        var attached = false
        fun attach() {
            if (!attached && (enabled || QuranPlaybackService.hasActiveSession)) {
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

@Composable
internal fun QuranInlinePlayer(surah: Int, ayah: Int, playing: Boolean, buffering: Boolean, error: String?,
    toggle: () -> Unit, options: () -> Unit, close: () -> Unit) {
    val colors = quranPaperColors()
    Surface(Modifier.fillMaxWidth().testTag("quran_inline_player"), shape = RoundedCornerShape(18.dp),
        color = colors.paper, border = BorderStroke(1.dp, colors.rule)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                val playLabel = stringResource(if (error != null) R.string.qaudio_retry
                    else if (playing) R.string.qaudio_pause else R.string.qaudio_listen)
                FilledIconButton(onClick = toggle, modifier = Modifier.size(48.dp).testTag("quran_inline_play_pause")) {
                    if (playing && error == null) {
                        val ink = LocalContentColor.current
                        Canvas(Modifier.size(20.dp).semantics { contentDescription = playLabel }) {
                            val bar = Size(size.width * .22f, size.height * .76f)
                            drawRect(ink, Offset(size.width * .17f, size.height * .12f), bar)
                            drawRect(ink, Offset(size.width * .61f, size.height * .12f), bar)
                        }
                    } else Icon(Icons.Filled.PlayArrow, playLabel)
                }
                Text(stringResource(R.string.qp_selected_ayah, surah, ayah),
                    Modifier.weight(1f).padding(horizontal = 8.dp), color = colors.ink,
                    style = MaterialTheme.typography.titleSmall, maxLines = 2)
                IconButton(onClick = options, modifier = Modifier.testTag("quran_inline_options")) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.qr_audio_options))
                }
                IconButton(onClick = close, modifier = Modifier.testTag("quran_inline_close")) {
                    Icon(Icons.Filled.Close, stringResource(R.string.qaudio_close))
                }
            }
            if (buffering) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error != null) Text(error, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun QuranInlineOptions(repeats: Int, speed: Float, continueAfter: Boolean,
    configure: (Int, Float, Boolean) -> Unit, close: () -> Unit) {
    val colors = quranPaperColors()
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.canvas, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }.testTag("quran_ayah_sheet")
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.qr_audio_options), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = close, modifier = Modifier.testTag("ayah_close")) {
                    Icon(Icons.Default.Close, stringResource(R.string.qaudio_close))
                }
            }
            Text(stringResource(R.string.ql_repeat), style = MaterialTheme.typography.titleSmall)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 5, 10, 0).forEach { count ->
                    FilterChip(selected = repeats == count, onClick = { configure(count, speed, count != 0 && continueAfter) },
                        label = { Text(if (count == 0) "∞" else "${count}×") }, modifier = Modifier.testTag("ayah_repeat_$count"))
                }
            }
            QuranSpeedControls(speed, "ayah") { configure(repeats, it, continueAfter) }
            Row(Modifier.fillMaxWidth().toggleable(continueAfter, enabled = repeats != 0, role = Role.Checkbox,
                onValueChange = { configure(repeats, speed, it) }).testTag("ayah_continue").padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = continueAfter, onCheckedChange = null, enabled = repeats != 0)
                Text(stringResource(R.string.ql_continue), Modifier.padding(start = 8.dp))
            }
            Text(stringResource(R.string.qr_word_timing_credit), style = MaterialTheme.typography.bodySmall, color = colors.muted)
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
