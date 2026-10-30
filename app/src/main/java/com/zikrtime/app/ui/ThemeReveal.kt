package com.zikrtime.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zikrtime.app.data.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One live screen tree. Only the previous pixels are retained during a manual theme change. */
val LocalThemeReveal = staticCompositionLocalOf<((String, Offset) -> Unit)?> { null }

@Composable
fun rememberAppearanceChange(): (String, Offset) -> Unit {
    val context = LocalContext.current
    return LocalThemeReveal.current ?: { value, _ ->
        Store.appearance = value
        com.zikrtime.app.widget.Widgets.updateAll(context)
    }
}

@Composable
fun ThemeRevealHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val progress = remember { Animatable(1f) }
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var windowOffset by remember { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    var job by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    val motion = motionEnabled()

    fun commit(value: String) {
        Store.appearance = value
        com.zikrtime.app.widget.Widgets.updateAll(context)
    }
    fun stop() {
        generation++
        job?.cancel()
        job = null
        previous = null
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stop()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); stop() }
    }
    LaunchedEffect(bounds) { stop() }

    val change: (String, Offset) -> Unit = { value, position ->
        if (value != Store.appearance) {
            // A second request supersedes the previous one. The capture layer never contains
            // the old overlay, so rapid taps cannot recursively capture snapshots.
            stop()
            if (!motion || bounds.width == 0 || bounds.height == 0 ||
                !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                commit(value)
            } else {
                val token = generation
                job = scope.launch {
                    try {
                        val bitmap = layer.toImageBitmap()
                        origin = Offset(
                            (position.x - windowOffset.x).coerceIn(0f, bounds.width.toFloat()),
                            (position.y - windowOffset.y).coerceIn(0f, bounds.height.toFloat()),
                        )
                        progress.snapTo(0f)
                        previous = bitmap
                        commit(value)
                        // Let the new palette reach the single live tree before revealing it.
                        withFrameNanos { }
                        withFrameNanos { }
                        progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // A capture failure must never prevent changing the saved theme.
                        commit(value)
                    } catch (_: OutOfMemoryError) {
                        commit(value)
                    } finally {
                        if (token == generation) previous = null
                    }
                }
            }
        }
    }

    CompositionLocalProvider(LocalThemeReveal provides change) {
        Box(Modifier.fillMaxSize().onGloballyPositioned {
            windowOffset = it.positionInWindow()
            bounds = it.size
        }) {
            Box(Modifier.fillMaxSize().drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }) { content() }
            previous?.let { bitmap ->
                // Decorative canvas has no pointer handler or accessibility semantics.
                Canvas(Modifier.fillMaxSize()) {
                    val radius = revealRadius(origin.x, origin.y, size.width, size.height) * progress.value
                    val hole = Path().apply {
                        addOval(Rect(origin.x - radius, origin.y - radius, origin.x + radius, origin.y + radius))
                    }
                    clipPath(hole, clipOp = ClipOp.Difference) {
                        drawImage(bitmap, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                    }
                }
            }
        }
    }
}
