package com.zikrtime.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.LiveData
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zikrtime.app.R
import com.zikrtime.app.data.Store
import com.zikrtime.app.prayer.Qibla
import com.zikrtime.app.prayer.qiblaAngleDifference
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun QiblaCameraButton(onCameraChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    val cameraExists = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        open = granted
        denied = !granted
    }
    LaunchedEffect(open) { onCameraChanged(open) }
    DisposableEffect(Unit) { onDispose { onCameraChanged(false) } }
    OutlinedButton(
        onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) open = true
            else permission.launch(Manifest.permission.CAMERA)
        },
        enabled = cameraExists,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text(stringResource(R.string.qibla_camera_open)) }
    Text(stringResource(if (cameraExists) R.string.qibla_camera_explain else R.string.qibla_camera_missing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (denied) {
        AlertDialog(
            onDismissRequest = { denied = false },
            title = { Text(stringResource(R.string.qibla_camera_permission)) },
            text = { Text(stringResource(R.string.qibla_camera_explain)) },
            confirmButton = {
                TextButton(onClick = {
                    denied = false
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text(stringResource(R.string.qibla_camera_settings)) }
            },
            dismissButton = { TextButton(onClick = { denied = false }) { Text(stringResource(R.string.qibla_camera_close)) } },
        )
    }
    if (open) QiblaCameraDialog { open = false }
}

@Composable
private fun QiblaCameraDialog(onClose: () -> Unit) {
    val location = Store.location
    val pose = rememberQiblaPose(location.latitude, location.longitude, camera = true)
    val target = remember(location) { Qibla.bearing(location.latitude, location.longitude).toFloat() }
    val relative = pose.heading?.let { qiblaAngleDifference(target, it) }
    var streaming by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val aligned = streaming && relative != null && abs(relative) <= 5f && abs(pose.elevation) <= 25f && !pose.lowAccuracy
    val context = LocalContext.current
    val hasCompass = remember(context) {
        context.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            CameraPreview(Modifier.fillMaxSize(), onStreaming = { streaming = it; if (it) failed = false }, onError = { failed = true })
            val message = when {
                failed -> stringResource(R.string.qibla_camera_error)
                !streaming -> stringResource(R.string.qibla_camera_loading)
                !hasCompass -> stringResource(R.string.qibla_no_sensor) + "\n${target.roundToInt()}°"
                pose.heading == null -> stringResource(R.string.qibla_camera_raise)
                pose.lowAccuracy -> stringResource(R.string.qibla_calibrate)
                abs(pose.elevation) > 25f -> stringResource(R.string.qibla_camera_level)
                aligned -> stringResource(R.string.qibla_aligned)
                relative != null && relative < 0 -> stringResource(R.string.qibla_turn_left, abs(relative).roundToInt())
                else -> stringResource(R.string.qibla_turn_right, (relative ?: 0f).roundToInt())
            }
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.qibla_title), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    FilledTonalButton(onClick = onClose) { Text(stringResource(R.string.qibla_camera_close)) }
                }
                Surface(color = Color.Black.copy(alpha = 0.72f), shape = RoundedCornerShape(18.dp)) {
                    Text(message, Modifier.padding(16.dp), color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (streaming && !failed && relative != null && abs(relative) < 28f && !pose.lowAccuracy && abs(pose.elevation) < 35f) {
                val horizontal = maxWidth * (relative / 100f)
                val vertical = maxHeight * (pose.elevation / 150f)
                val photoAlpha by androidx.compose.animation.core.animateFloatAsState(if (aligned) 1f else 0.7f, androidx.compose.animation.core.tween(if (motionEnabled()) 180 else 0), label = "kaaba-image")
                Surface(
                    modifier = Modifier.align(Alignment.Center).offset(x = horizontal, y = vertical).width(252.dp).graphicsLayer { alpha = photoAlpha },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(2.dp, if (aligned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column {
                        Image(painterResource(R.drawable.qibla_kaaba), contentDescription = stringResource(R.string.qibla_camera_image), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1.5f))
                        Text(stringResource(R.string.qibla_camera_image), Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.76f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.qibla_camera_note), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://commons.wikimedia.org/wiki/File:Kaaba_1.jpg"))) }
                        .onFailure { android.widget.Toast.makeText(context, R.string.source_link_failed, android.widget.Toast.LENGTH_SHORT).show() }
                }) {
                    Text("مانفی · CC BY-SA 4.0", color = Color(0xFFF2D59D), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(modifier: Modifier, onStreaming: (Boolean) -> Unit, onError: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentStreaming by rememberUpdatedState(onStreaming)
    val currentError by rememberUpdatedState(onError)
    val preview = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(owner, preview) {
        var disposed = false
        var initialized = false
        val controller = LifecycleCameraController(context)
        val observer = Observer<PreviewView.StreamState> {
            currentStreaming(it == PreviewView.StreamState.STREAMING && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        var cameraState: LiveData<CameraState>? = null
        val cameraErrors = Observer<CameraState> { if (it.error != null) currentError() }
        fun start() {
            if (disposed || !initialized || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                currentError()
                return
            }
            try {
                controller.bindToLifecycle(owner)
                cameraState?.removeObserver(cameraErrors)
                cameraState = controller.cameraInfo?.cameraState
                cameraState?.observe(owner, cameraErrors)
            } catch (_: RuntimeException) {
                controller.unbind()
                currentError()
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> {
                    currentStreaming(false)
                    controller.unbind()
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(lifecycleObserver)
        try {
            controller.setEnabledUseCases(0)
            controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            preview.controller = controller
            preview.previewStreamState.observe(owner, observer)
            controller.initializationFuture.addListener({
                if (!disposed) try {
                    controller.initializationFuture.get()
                    initialized = true
                    start()
                } catch (_: Exception) { currentError() }
            }, ContextCompat.getMainExecutor(context))
        } catch (_: RuntimeException) {
            currentError()
        }
        onDispose {
            disposed = true
            owner.lifecycle.removeObserver(lifecycleObserver)
            cameraState?.removeObserver(cameraErrors)
            preview.previewStreamState.removeObserver(observer)
            preview.controller = null
            controller.unbind()
        }
    }
    AndroidView(factory = { preview }, modifier = modifier)
}
