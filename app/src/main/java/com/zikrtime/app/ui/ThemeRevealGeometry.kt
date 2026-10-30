package com.zikrtime.app.ui

import kotlin.math.hypot
import kotlin.math.max

/** Radius reaching the farthest corner, including the final antialiased pixel. */
internal fun revealRadius(x: Float, y: Float, width: Float, height: Float): Float =
    hypot(max(x, width - x), max(y, height - y)) + 1f
