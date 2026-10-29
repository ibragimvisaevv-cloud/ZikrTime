package com.zikrtime.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.ui.theme.LocalDarkTheme

/** One movement preference for page, sheet and reading transitions. No persistent animation loop. */
@Composable
fun motionEnabled(): Boolean {
    val context = LocalContext.current
    val resume = AppEvents.resumeCount
    return remember(resume) { android.provider.Settings.Global.getFloat(context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
}

/** The approved home scene also connects onboarding, ringing and interruption screens. */
@Composable
fun SceneBackdrop(modifier: Modifier = Modifier, night: Boolean = LocalDarkTheme.current) {
    Box(modifier) {
        Image(painterResource(if (night) R.drawable.horizon_portrait_night else R.drawable.horizon_portrait_day),
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color(0xFF071F1B).copy(alpha = .52f), Color(0xFF071F1B).copy(alpha = .24f), Color(0xFF071F1B).copy(alpha = .88f)))))
    }
}

@Composable
fun PageHeading(title: String, subtitle: String? = null, scene: Int? = null) {
    if (scene == null) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(30.dp).height(3.dp).background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp)))
            Text(title, style = MaterialTheme.typography.headlineLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LandscapePanel(scene) {
            Column(Modifier.fillMaxWidth().heightIn(min = 168.dp).padding(24.dp), verticalArrangement = Arrangement.Bottom) {
                Text(title, style = MaterialTheme.typography.headlineLarge, color = Porcelain)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Porcelain.copy(alpha = .88f))
            }
        }
    }
}

/** Shared empty / permission / offline state; text is never embedded in artwork. */
@Composable
fun StatePanel(title: String, text: String? = null, icon: Int = R.drawable.ic_nav_knowledge,
    action: String? = null, onAction: (() -> Unit)? = null, error: Boolean = false) {
    Surface(shape = RoundedCornerShape(24.dp), color = if (error) MaterialTheme.colorScheme.errorContainer.copy(alpha = .45f)
        else MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(painterResource(icon), size = 44.dp)
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun HorizonDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    title: @Composable (() -> Unit)? = null, text: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null) {
    AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, dismissButton = dismissButton,
        title = title, text = text, shape = RoundedCornerShape(30.dp),
        icon = { Icon(painterResource(R.drawable.ic_prayer_isha), null, tint = MaterialTheme.colorScheme.secondary) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HorizonSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
            .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            content()
        }
    }
}

private val readingScaleState: MutableState<Float> by lazy {
    mutableStateOf(Store.getRaw("reader_scale")?.toFloatOrNull()?.coerceIn(.85f, 1.5f) ?: 1f)
}
@Composable
fun rememberReadingScale(): MutableState<Float> {
    LaunchedEffect(readingScaleState.value) { Store.putRaw("reader_scale", readingScaleState.value.toString()) }
    return readingScaleState
}

@Composable
fun ReadingTools(scale: Float, onScale: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.testTag("reader_options")) {
        Text("Aa", style = MaterialTheme.typography.titleMedium)
    }
    if (open) HorizonSheet(stringResource(R.string.reader_text_size), { open = false }) {
        Text("بِسْمِ اللَّهِ", style = com.zikrtime.app.ui.theme.ArabicStyle.copy(fontSize = (30 * scale).let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) }),
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Slider(value = scale, onValueChange = onScale, valueRange = .85f..1.5f, steps = 12, modifier = Modifier.testTag("reader_scale"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("85%", style = MaterialTheme.typography.labelMedium)
            Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
            Text("150%", style = MaterialTheme.typography.labelMedium)
        }
        PrimaryButton(stringResource(R.string.action_done), onClick = { open = false })
    }
}
