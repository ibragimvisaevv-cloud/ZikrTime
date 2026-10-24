package com.zikrtime.app.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.prayer.AdhanSound

/**
 * Выбор записи азана: у каждой строки кнопка «прослушать» (тихо, ~30% громкости).
 * [includeSystem] — добавить вариант «мелодия будильника телефона» (для будильника).
 */
@Composable
fun AdhanVoiceList(selected: String, onSelect: (String) -> Unit, includeSystem: Boolean) {
    val context = LocalContext.current
    DisposableEffect(Unit) { onDispose { AdhanSound.stopPreview() } }
    Column {
        AdhanSound.voices.filter { AdhanSound.available(context, it.id) }.forEach { voice ->
            val playing = AdhanSound.previewing == voice.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(voice.id) }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == voice.id, onClick = { onSelect(voice.id) })
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.adhan_voice, voice.number), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.duration_m_s, voice.seconds / 60, voice.seconds % 60),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                PlayButton(playing = playing) { AdhanSound.togglePreview(context, voice.id) }
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
    }
}

/** Круглая кнопка ▶ / ■ с золотым кольцом, пока звучит. */
@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(44.dp)
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
                contentDescription = stringResource(R.string.adhan_preview),
                tint = scheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
