package com.zikrtime.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zikrtime.app.R
import com.zikrtime.app.alarm.AlarmTone
import com.zikrtime.app.prayer.AdhanSound
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AlarmTonePicker() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val sample = remember { ToneSample() }
    val selected = AlarmTone.selected()
    var importing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var defaultTitle by remember(context, selected) { mutableStateOf<String?>(null) }
    LaunchedEffect(context, selected, AppEvents.resumeCount) {
        if (selected == null) {
            defaultTitle = withContext(Dispatchers.IO) { AlarmTone.defaultTitle(context) }
        }
    }

    DisposableEffect(lifecycle, sample) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) sample.stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); sample.stop() }
    }
    LaunchedEffect(sample.playing) { if (sample.playing) { delay(10_000); sample.stop() } }

    fun choose(uri: Uri) {
        if (importing) return
        sample.stop()
        failed = false
        if (RingtoneManager.isDefault(uri)) {
            AlarmTone.select(context, null)
            return
        }
        importing = true
        scope.launch {
            var copied: AlarmTone.Choice? = null
            try {
                withContext(Dispatchers.IO) { copied = AlarmTone.copySelection(context, uri) }
                AlarmTone.select(context, requireNotNull(copied))
                copied = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            } finally {
                copied?.let { AlarmTone.discard(context, it) }
                importing = false
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::choose) }
    fun openFile() {
        sample.stop()
        runCatching { filePicker.launch(arrayOf("audio/*")) }.onFailure { failed = true }
    }
    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) choose(uri) else failed = true
        }
    }
    Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp)) {
        Text(selected?.title?.takeIf(String::isNotBlank) ?: defaultTitle?.takeIf(String::isNotBlank) ?: stringResource(
            if (selected == null) R.string.tone_phone_default else R.string.tone_selected),
            style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.tone_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            TextButton(enabled = !importing, modifier = Modifier.testTag("alarm_tone_choose"), onClick = {
                sample.stop()
                val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, default)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selected?.originalUri?.let(Uri::parse) ?: default)
                runCatching { systemPicker.launch(intent) }.onFailure { openFile() }
            }) { Text(stringResource(R.string.tone_choose)) }
            TextButton(enabled = !importing, modifier = Modifier.testTag("alarm_tone_preview"),
                onClick = { if (sample.playing) sample.stop() else sample.play(context) }) {
                Text(stringResource(if (sample.playing) R.string.tone_stop else R.string.adhan_preview))
            }
        }
        Row {
            TextButton(enabled = !importing, onClick = ::openFile) { Text(stringResource(R.string.tone_file)) }
            if (selected != null) TextButton(enabled = !importing, onClick = {
                sample.stop()
                AlarmTone.select(context, null)
                failed = false
            }) { Text(stringResource(R.string.action_reset)) }
        }
        if (importing) androidx.compose.material3.LinearProgressIndicator()
        if (failed || sample.failed) Text(stringResource(R.string.tone_failed),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** Uses the media volume and releases the decoder on stop, completion and screen exit. */
private class ToneSample {
    var playing by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    private var player: MediaPlayer? = null

    fun stop() {
        val previous = player
        player = null
        playing = false
        previous?.release()
    }

    fun play(context: Context) {
        stop()
        AdhanSound.stopPreview()
        com.zikrtime.app.audio.QuranPlaybackService.pauseForInterruption(context)
        failed = false
        val uris = AlarmTone.candidates(context)
        fun attempt(index: Int) {
            if (index == uris.size) { failed = true; stop(); return }
            val candidate = runCatching { MediaPlayer() }.getOrElse { failed = true; stop(); return }
            player = candidate
            playing = true
            fun retry() {
                if (player !== candidate) return
                player = null
                candidate.release()
                attempt(index + 1)
            }
            runCatching {
                candidate.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                candidate.setDataSource(context, uris[index])
                candidate.setOnPreparedListener { if (player === it) runCatching { it.start() }.onFailure { retry() } }
                candidate.setOnCompletionListener { if (player === it) stop() }
                candidate.setOnErrorListener { _, _, _ -> retry(); true }
                candidate.prepareAsync()
            }.onFailure { retry() }
        }
        attempt(0)
    }
}
