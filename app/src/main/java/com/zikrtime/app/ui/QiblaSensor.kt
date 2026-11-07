package com.zikrtime.app.ui

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zikrtime.app.prayer.forwardDirection
import com.zikrtime.app.prayer.qiblaAngleDifference

data class QiblaPose(val heading: Float? = null, val elevation: Float = 0f, val lowAccuracy: Boolean = true)

@Composable
fun rememberQiblaPose(lat: Double, lon: Double, upright: Boolean, enabled: Boolean = true): QiblaPose {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    var pose by remember(lat, lon, upright) { mutableStateOf(QiblaPose()) }
    DisposableEffect(lat, lon, upright, lifecycle, enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val declination = GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination
        val rotation = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        var lastSample = 0L
        var registered = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!registered) return
                if (event.timestamp - lastSample < 50_000_000L) return
                lastSample = event.timestamp
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                val elevation: Float
                val bearing: Float?
                if (upright) {
                    val direction = forwardDirection(rotation, declination)
                    elevation = direction.elevation
                    bearing = direction.bearing
                } else {
                    val axes = when (view.display?.rotation) {
                        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                    }
                    SensorManager.remapCoordinateSystem(rotation, axes.first, axes.second, remapped)
                    SensorManager.getOrientation(remapped, orientation)
                    bearing = ((Math.toDegrees(orientation[0].toDouble()) + declination + 360.0) % 360.0).toFloat()
                    elevation = 0f
                }
                val old = pose.heading
                val smooth = if (old != null && bearing != null) old + qiblaAngleDifference(bearing, old) * 0.25f else bearing
                // An unbounded angle avoids a 359° → 0° visual jump.
                pose = QiblaPose(smooth, elevation, event.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (registered) pose = pose.copy(lowAccuracy = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW)
            }
        }
        fun start() {
            if (enabled && !registered && sensor != null) registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        fun stop() {
            manager.unregisterListener(listener)
            registered = false
            lastSample = 0L
            pose = QiblaPose()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_DESTROY -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
        }
    }
    return pose
}
