package com.zikrtime.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ThemeRevealGeometryTest {
    @Test fun waveCoversEveryCornerFromHeaderAndSettingsButtons() {
        for ((width, height) in listOf(360f to 800f, 1440f to 3120f, 900f to 400f)) {
            for ((x, y) in listOf(0f to 0f, width to 0f, width / 2 to height / 2, width / 3 to height)) {
                val radius = revealRadius(x, y, width, height)
                for ((cx, cy) in listOf(0f to 0f, width to 0f, 0f to height, width to height)) {
                    assertTrue("Uncovered corner", radius > hypot(cx - x, cy - y))
                }
            }
        }
    }
}
