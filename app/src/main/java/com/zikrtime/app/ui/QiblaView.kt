@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.zikrtime.app.ui

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zikrtime.app.R
import com.zikrtime.app.data.Cities
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.Qibla
import com.zikrtime.app.prayer.qiblaAngleDifference
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun QiblaViewButton(onViewChanged: (Boolean) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val notifyViewChanged by rememberUpdatedState(onViewChanged)
    LaunchedEffect(open) { notifyViewChanged(open) }
    DisposableEffect(Unit) { onDispose { notifyViewChanged(false) } }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(stringResource(R.string.qibla_view_open))
    }
    Text(stringResource(R.string.qibla_view_explain), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (open) QiblaViewDialog { open = false }
}

@Composable
private fun QiblaViewDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val location = Store.location
    val pose = rememberQiblaPose(location.latitude, location.longitude, upright = true)
    val target = remember(location) { Qibla.bearing(location.latitude, location.longitude).toFloat() }
    val hasCompass = remember(context) {
        context.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
    }
    val relative = pose.heading?.let { qiblaAngleDifference(target, it) }
    val reliable = hasCompass && relative != null && !pose.lowAccuracy
    var aligned by remember(location) { mutableStateOf(false) }
    LaunchedEffect(relative, pose.elevation, reliable) {
        // Separate entry/exit thresholds avoid flashing and repeated haptics at the boundary.
        aligned = reliable && abs(relative ?: 180f) <= (if (aligned) 9f else 5f) &&
            abs(pose.elevation) <= (if (aligned) 35f else 25f)
    }
    val view = LocalView.current
    LaunchedEffect(aligned) {
        if (aligned) view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30)
            HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    }
    val message = when {
        !hasCompass -> stringResource(R.string.qibla_view_no_sensor, target.roundToInt())
        pose.heading == null -> stringResource(R.string.qibla_view_level)
        pose.lowAccuracy -> stringResource(R.string.qibla_calibrate)
        abs(pose.elevation) > (if (aligned) 35f else 25f) -> stringResource(R.string.qibla_view_level)
        aligned -> stringResource(R.string.qibla_view_found)
        relative != null && relative < 0 -> stringResource(R.string.qibla_turn_left, abs(relative).roundToInt())
        else -> stringResource(R.string.qibla_turn_right, (relative ?: 0f).roundToInt())
    }
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding()
            .semantics { testTagsAsResourceId = true }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.qibla_title), style = MaterialTheme.typography.headlineSmall)
                    Text(Cities.displayName(context, location), style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant)
                }
                FilledTonalButton(onClick = onClose) { Text(stringResource(R.string.qibla_view_close)) }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val sceneHeight = (maxHeight * 0.5f).coerceIn(230.dp, 420.dp)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    QiblaScene(relative, pose.elevation, reliable, aligned, Modifier.fillMaxWidth().height(sceneHeight))
                    Surface(Modifier.padding(horizontal = 20.dp).fillMaxWidth().testTag("qibla_view_direction"),
                        color = if (aligned) colors.primaryContainer else colors.surfaceContainer,
                        shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(message, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                                color = if (aligned) colors.onPrimaryContainer else colors.onSurface)
                            Text(stringResource(R.string.qibla_bearing) + " · ${target.roundToInt()}°",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (aligned) colors.onPrimaryContainer else colors.onSurfaceVariant)
                        }
                    }
                    Text(stringResource(R.string.qibla_view_note), Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = colors.onSurfaceVariant)
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://commons.wikimedia.org/wiki/File:Kaaba_1.jpg"))) }
                            .onFailure { android.widget.Toast.makeText(context, R.string.source_link_failed, android.widget.Toast.LENGTH_SHORT).show() }
                    }) { Text(stringResource(R.string.qibla_view_attribution), style = MaterialTheme.typography.labelSmall) }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun QiblaScene(relative: Float?, elevation: Float, reliable: Boolean, aligned: Boolean, modifier: Modifier) {
    val motion = motionEnabled()
    val duration = if (motion) 180 else 0
    val near = if (reliable) (1f - abs(relative ?: 180f) / 45f).coerceIn(0f, 1f) else 0.45f
    val shiftX by animateFloatAsState(if (motion && reliable) ((relative ?: 0f) / 4f).coerceIn(-12f, 12f) else 0f,
        tween(duration), label = "qibla-view-horizontal")
    val shiftY by animateFloatAsState(if (motion && reliable) (elevation / 4f).coerceIn(-8f, 8f) else 0f,
        tween(duration), label = "qibla-view-elevation")
    val scale by animateFloatAsState(if (motion) 0.96f + near * 0.06f else 1f, tween(duration), label = "qibla-view-distance")
    val opacity by animateFloatAsState(if (motion) 0.65f + near * 0.35f else 1f, tween(duration), label = "qibla-view-reveal")
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.testTag("qibla_view_scene"), contentAlignment = Alignment.Center) {
        val frameWidth = minOf(maxWidth * 0.88f, (maxHeight - 56.dp) * (16f / 9f), 700.dp)
        Canvas(Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val lineColor = colors.primary.copy(alpha = 0.7f)
            drawLine(lineColor, Offset(centerX, 16.dp.toPx()), Offset(centerX, 38.dp.toPx()), 1.dp.toPx())
            drawCircle(lineColor, 2.dp.toPx(), Offset(centerX, size.height - 28.dp.toPx()))
        }
        Surface(Modifier.width(frameWidth).offset(shiftX.dp, shiftY.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = opacity },
            shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLow,
            border = BorderStroke(if (aligned) 2.dp else 1.dp, if (aligned) colors.primary else colors.outlineVariant),
            shadowElevation = 0.dp) {
            Image(painterResource(R.drawable.qibla_kaaba), stringResource(R.string.qibla_view_image),
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).testTag("qibla_view_photo"))
        }
    }
}
