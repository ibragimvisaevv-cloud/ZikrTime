package com.zikrtime.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.widget.Widgets
import kotlinx.coroutines.delay

private enum class TasbihPanel { SETTINGS, PHRASES, RESET }

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun TasbihScreen() {
    val context = LocalContext.current
    val nav = LocalNav.current
    val phrase = Store.tasbihPhrase.coerceIn(TASBIH_ARABIC.indices)
    val goal = Store.tasbihTarget
    val count = Store.tasbihCount
    val transcription = stringResource(TASBIH_NAMES[phrase].first)
    val meaning = stringResource(TASBIH_NAMES[phrase].second)
    val config = TasbihSceneConfig(
        TASBIH_ARABIC[phrase], transcription, meaning, goal,
        LocalConfiguration.current.locales[0].toLanguageTag(), !motionEnabled(),
        stringResource(R.string.tb_gesture_hint), stringResource(R.string.tb_unlimited),
        stringResource(R.string.tb_cycle_complete)
    )
    var panel by rememberSaveable { mutableStateOf<TasbihPanel?>(null) }
    val scene = remember(context) { TasbihSceneView(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(scene, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> scene.resumeScene()
                Lifecycle.Event.ON_PAUSE -> scene.pauseScene()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(scene) {
        onDispose {
            scene.releaseScene()
            Widgets.updateTasbih(context)
        }
    }
    // Store accepts every input immediately. Only the launcher redraw is debounced.
    LaunchedEffect(count, phrase, goal) {
        delay(300)
        Widgets.updateTasbih(context)
    }

    val view = LocalView.current
    DisposableEffect(view) {
        val controller = view.context.readerActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previousBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (controller != null && previousBehavior != null) controller.systemBarsBehavior = previousBehavior
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        AndroidView(factory = { scene }, modifier = Modifier.fillMaxSize(),
            update = { it.bind(config, Store.tasbihCount, acceptingInput = panel == null) })
        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { nav.back() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back),
                    tint = Color.White.copy(alpha = .78f))
            }
            IconButton(onClick = { panel = TasbihPanel.SETTINGS }, modifier = Modifier.testTag("tasbih_settings")) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.action_open_settings),
                    tint = Color.White.copy(alpha = .78f))
            }
        }
    }

    when (panel) {
        TasbihPanel.SETTINGS -> HorizonSheet(stringResource(R.string.tasbih_title), { panel = null }) {
            TextButton(onClick = { panel = TasbihPanel.PHRASES }, modifier = Modifier.testTag("tasbih_phrase_open")) {
                Text(stringResource(R.string.tb_choose_phrase) + " · " + transcription)
            }
            Text(stringResource(R.string.tasbih_target), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(33, 99, 100, 0).forEach { value ->
                    FilterChip(selected = value == goal, onClick = { Store.tasbihTarget = value },
                        label = { Text(if (value == 0) stringResource(R.string.tb_unlimited) else value.toString()) },
                        modifier = Modifier.testTag("tasbih_target_$value"))
                }
            }
            Box(Modifier.testTag("tasbih_sound")) {
                SwitchRow(stringResource(R.string.alarm_sound), Store.tasbihSoundEnabled,
                    { Store.tasbihSoundEnabled = it })
            }
            Text(stringResource(R.string.tb_gesture_hint), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.tb_total_count) + " · " + count,
                    modifier = Modifier.testTag("tasbih_total_value"))
                if (goal > 0) Text(stringResource(R.string.tb_completed_laps) + " · " + count / goal,
                    modifier = Modifier.testTag("tasbih_laps_value"))
                if (Store.personalProgressEnabled) Text(stringResource(R.string.tasbih_today, Store.tasbihToday()),
                    modifier = Modifier.testTag("tasbih_today_value"))
            }
            TextButton(onClick = { panel = TasbihPanel.RESET }, modifier = Modifier.testTag("tasbih_reset")) {
                Text(stringResource(R.string.action_reset))
            }
        }
        TasbihPanel.PHRASES -> HorizonSheet(stringResource(R.string.tb_choose_phrase), { panel = null }) {
            Text(stringResource(R.string.tb_phrase_change_hint), style = MaterialTheme.typography.bodySmall)
            TASBIH_NAMES.forEachIndexed { index, (name, meaningId) ->
                Box(Modifier.testTag("tasbih_phrase_$index")) {
                    RadioLine(stringResource(name), selected = index == phrase, subtitle = stringResource(meaningId)) {
                        if (index != phrase) {
                            Store.tasbihPhrase = index
                            Store.tasbihCount = 0
                        }
                        panel = null
                    }
                }
            }
        }
        TasbihPanel.RESET -> HorizonDialog(onDismissRequest = { panel = null },
            title = { Text(stringResource(R.string.action_reset)) },
            text = { Text(stringResource(R.string.counter_reset_confirmation)) },
            confirmButton = {
                TextButton(onClick = { Store.tasbihCount = 0; panel = null },
                    modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("tasbih_reset_confirm")) {
                    Text(stringResource(R.string.action_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { panel = null },
                    modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("tasbih_reset_cancel")) {
                    Text(stringResource(R.string.action_close))
                }
            })
        null -> Unit
    }
}
