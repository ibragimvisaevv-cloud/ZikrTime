package com.zikrtime.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zikrtime.app.R
import com.zikrtime.app.prayer.AdhanSound

/**
 * Выбор записи азана с предпрослушиванием на текущей громкости устройства.
 * [includeSystem] — выбор для будильника фаджра: разрешает утренний азан и системную мелодию.
 */
@Composable
fun AdhanVoiceList(selected: String, onSelect: (String) -> Unit, includeSystem: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var source by remember { mutableStateOf<AdhanSound.Voice?>(null) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) AdhanSound.stopPreview()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            AdhanSound.stopPreview()
        }
    }
    Column {
        AdhanSound.eligibleVoices(includeSystem).filter { AdhanSound.available(context, it.id) }.forEach { voice ->
            val playing = AdhanSound.previewing == voice.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag("adhan_select_${voice.id}")
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(voice.id) }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == voice.id, onClick = { onSelect(voice.id) })
                Column(Modifier.weight(1f)) {
                    Text(stringResource(voice.titleRes), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.duration_m_s, voice.seconds / 60, voice.seconds % 60),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (voice.fajrOnly) Text(
                        stringResource(R.string.adhan_fajr_only),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = {
                        AdhanSound.stopPreview()
                        source = voice
                    }) { Text(stringResource(R.string.adhan_recording_source)) }
                }
                Spacer(Modifier.width(8.dp))
                PlayButton(playing = playing, id = voice.id) { AdhanSound.togglePreview(context, voice.id) }
            }
        }
        if (includeSystem) {
            RadioLine(
                stringResource(R.string.alarm_sound_system),
                selected = selected == "system",
                onClick = {
                    AdhanSound.stopPreview()
                    onSelect("system")
                },
            )
        }
        if (AdhanSound.previewFailed) Text(
            stringResource(R.string.adhan_preview_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            stringResource(R.string.adhan_preview_volume),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
    source?.let { voice -> AdhanSourceDialog(voice) { source = null } }
}

@Composable
private fun AdhanSourceDialog(voice: AdhanSound.Voice, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var linkFailed by remember(voice.id) { mutableStateOf(false) }
    fun open(url: String) {
        linkFailed = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isFailure
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(voice.titleRes)) },
        text = {
            Column {
                Text(voice.credit, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.adhan_recording_processing), modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = { open(voice.sourceUrl) }) { Text(stringResource(R.string.adhan_open_recording_source)) }
                TextButton(onClick = { open(voice.licenseUrl) }) { Text(stringResource(R.string.adhan_open_license)) }
                if (linkFailed) Text(stringResource(R.string.adhan_link_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.adhan_close_source)) } },
    )
}

/** Круглая кнопка ▶ / ■ с золотым кольцом, пока звучит. */
@Composable
private fun PlayButton(playing: Boolean, id: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(if (playing) R.string.adhan_stop_preview else R.string.adhan_preview)
    Box(
        Modifier
            .size(44.dp)
            .testTag("adhan_preview_$id")
            .semantics { contentDescription = label }
            .clip(CircleShape)
            .background(if (playing) scheme.primary else scheme.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (playing) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(scheme.onPrimary))
        } else {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
