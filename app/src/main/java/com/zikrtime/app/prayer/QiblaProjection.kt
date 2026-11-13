package com.zikrtime.app.prayer

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot

data class ForwardDirection(val bearing: Float?, val elevation: Float)

/** An upright phone faces along negative device Z, independently of display rotation. */
fun forwardDirection(rotation: FloatArray, declination: Float): ForwardDirection {
    require(rotation.size == 9)
    val east = -rotation[2]
    val north = -rotation[5]
    val up = -rotation[8]
    val elevation = Math.toDegrees(asin(up.coerceIn(-1f, 1f)).toDouble()).toFloat()
    val bearing = if (hypot(east, north) < 0.15f) null else {
        ((Math.toDegrees(atan2(east, north).toDouble()) + declination + 360.0) % 360.0).toFloat()
    }
    return ForwardDirection(bearing, elevation)
}

fun qiblaAngleDifference(target: Float, heading: Float): Float =
    (((target - heading) % 360f + 540f) % 360f) - 180f
