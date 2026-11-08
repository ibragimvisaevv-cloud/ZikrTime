@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.zikrtime.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.zikrtime.app.R
import com.zikrtime.app.data.GuideScreenshot
import com.zikrtime.app.data.GuideScreenshots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/** Real, bundled application captures. No mock screen or network image fallback. */
@Composable
internal fun AppGuidePreview(topic: GuideTopic, step: Int, modifier: Modifier = Modifier, imageHeight: Dp = 340.dp) {
    val context = LocalContext.current.applicationContext
    var frames by remember(context) { mutableStateOf<Map<String, List<GuideScreenshot>>?>(null) }
    LaunchedEffect(context) {
        frames = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(GuideScreenshots.MANIFEST).bufferedReader().use {
                GuideScreenshots.parse(it.readText())
            } }.getOrDefault(emptyMap())
        }
    }
    val frame = frames?.get(topic.key)?.getOrNull(step)
    var zoom by rememberSaveable(topic.key, step) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.guide_visual_example), style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { zoom = true }, enabled = frame != null,
                modifier = Modifier.testTag("guide_zoom")) {
                Text(stringResource(R.string.guide_visual_enlarge))
            }
        }
        if (frame != null) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // A full phone screenshot stays visible as a map of the screen.
                // Enlargement is explicit; the user need not hunt for a marker below a giant image.
                val width = minOf(maxWidth, imageHeight * frame.aspectRatio)
                Surface(onClick = { zoom = true }, shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    modifier = Modifier.width(width).height(width / frame.aspectRatio).testTag("guide_screenshot")) {
                    ScreenshotWithTarget(frame, step + 1, topic.key,
                        Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)))
                }
            }
        } else {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer) {
                Box(Modifier.fillMaxWidth().heightIn(min = 180.dp).padding(24.dp), contentAlignment = Alignment.Center) {
                    if (frames == null) CircularProgressIndicator(Modifier.size(32.dp))
                    else Text(stringResource(R.string.guide_visual_unavailable), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    if (zoom && frame != null) {
        GuideImageZoom(frame, step + 1, topic, onClose = { zoom = false })
    }
}

@Composable
private fun ScreenshotWithTarget(frame: GuideScreenshot, number: Int, topic: String, modifier: Modifier) {
    var loaded by remember(frame.image) { mutableStateOf(false) }
    var failed by remember(frame.image) { mutableStateOf(false) }
    val description = stringResource(R.string.guide_visual_highlight, number)
    val accent = Color(0xFFFFD16A)
    BoxWithConstraints(modifier.background(Color(0xFF151913))) {
        AsyncImage(model = "file:///android_asset/${frame.assetPath}", contentDescription = null,
            contentScale = ContentScale.FillBounds,
            onSuccess = { loaded = true; failed = false }, onError = { failed = true },
            modifier = Modifier.fillMaxSize().then(if (loaded) Modifier.testTag("guide_image_${topic}_$number") else Modifier))
        if (loaded) {
            val target = frame.target
            Canvas(Modifier.fillMaxSize().testTag("guide_hotspot").semantics { contentDescription = description }) {
                val l = target.left * size.width
                val t = target.top * size.height
                val r = target.right * size.width
                val b = target.bottom * size.height
                drawRoundRect(Color.Black.copy(alpha = .7f), Offset(l, t), Size(r - l, b - t),
                    cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(5.dp.toPx()))
                drawRoundRect(accent, Offset(l, t), Size(r - l, b - t),
                    cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(2.5.dp.toPx()))
            }
            val badge = 28.dp
            val x = (maxWidth * target.left).coerceIn(2.dp, (maxWidth - badge - 2.dp).coerceAtLeast(2.dp))
            val y = (maxHeight * target.top - badge / 2).coerceIn(2.dp, (maxHeight - badge - 2.dp).coerceAtLeast(2.dp))
            Box(Modifier.offset(x, y).size(badge).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = Color(0xFF191A13))
            }
        } else if (failed) {
            Text(stringResource(R.string.guide_visual_unavailable), color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp))
        } else {
            CircularProgressIndicator(Modifier.size(32.dp).align(Alignment.Center), color = accent)
        }
    }
}

@Composable
private fun GuideImageZoom(frame: GuideScreenshot, number: Int, topic: GuideTopic, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var scale by remember(frame.image) { mutableFloatStateOf(1f) }
    var pan by remember(frame.image) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    fun focusTarget(atScale: Float): Offset {
        val w = min(viewport.width.toFloat(), viewport.height * frame.aspectRatio)
        val h = w / frame.aspectRatio
        val limitX = ((w * atScale - viewport.width) / 2).coerceAtLeast(0f)
        val limitY = ((h * atScale - viewport.height) / 2).coerceAtLeast(0f)
        return Offset(((.5f - (frame.target.left + frame.target.right) / 2) * w * atScale).coerceIn(-limitX, limitX),
            ((.5f - (frame.target.top + frame.target.bottom) / 2) * h * atScale).coerceIn(-limitY, limitY))
    }
    Dialog(onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()
            .testTag("guide_zoom_dialog").semantics { testTagsAsResourceId = true }) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(topic.controls[number - 1]), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.testTag("guide_zoom_close")) {
                    Icon(Icons.Default.Close, stringResource(R.string.guide_close))
                }
            }
            Text(stringResource(R.string.guide_visual_zoom_hint), style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewport = it }.clipToBounds(), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                val baseWidth = min(maxWidth.value, maxHeight.value * frame.aspectRatio).dp
                val baseHeight = baseWidth / frame.aspectRatio
                val widthPx = with(density) { baseWidth.toPx() }
                val heightPx = with(density) { baseHeight.toPx() }
                val viewWidth = with(density) { maxWidth.toPx() }
                val viewHeight = with(density) { maxHeight.toPx() }
                fun bounded(offset: Offset, atScale: Float): Offset {
                    val x = ((widthPx * atScale - viewWidth) / 2).coerceAtLeast(0f)
                    val y = ((heightPx * atScale - viewHeight) / 2).coerceAtLeast(0f)
                    return Offset(offset.x.coerceIn(-x, x), offset.y.coerceIn(-y, y))
                }
                Box(Modifier.fillMaxSize().pointerInput(frame.image, widthPx, heightPx, viewWidth, viewHeight) {
                    detectTransformGestures { _, change, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, 3f)
                        pan = bounded(pan + change, next)
                        scale = next
                    }
                }, contentAlignment = Alignment.Center) {
                    ScreenshotWithTarget(frame, number, topic.key,
                        Modifier.size(baseWidth, baseHeight).graphicsLayer {
                            scaleX = scale; scaleY = scale
                            translationX = pan.x; translationY = pan.y
                        })
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scale = 1f; pan = Offset.Zero },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("guide_zoom_reset")) {
                    Text(stringResource(R.string.guide_visual_reset))
                }
                FilledTonalButton(onClick = { scale = (scale + .5f).coerceAtMost(3f); pan = focusTarget(scale) }, enabled = scale < 3f,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("guide_zoom_in")) {
                    Text(stringResource(R.string.guide_visual_enlarge))
                }
            }
        }
    }
}
