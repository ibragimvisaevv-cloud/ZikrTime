package com.zikrtime.app.prayer

import org.junit.Assert.*
import org.junit.Test

class QiblaProjectionTest {
    @Test fun horizontalPhoneHasNoForwardBearing() {
        assertNull(forwardDirection(floatArrayOf(1f,0f,0f, 0f,1f,0f, 0f,0f,1f), 0f).bearing)
    }

    @Test fun uprightPhoneFacesNorth() {
        val value = forwardDirection(floatArrayOf(1f,0f,0f, 0f,0f,-1f, 0f,1f,0f), 7f)
        assertEquals(7f, value.bearing!!, 0.001f)
        assertEquals(0f, value.elevation, 0.001f)
    }

    @Test fun turnCanCrossNorthAndMultipleFullRotations() {
        assertEquals(2f, qiblaAngleDifference(1f, 359f), 0.001f)
        assertEquals(2f, qiblaAngleDifference(1f, 1079f), 0.001f)
        assertEquals(-2f, qiblaAngleDifference(359f, -719f), 0.001f)
    }

    @Test fun forwardBearingSurvivesPortraitToLandscapeRoll() {
        val portrait = floatArrayOf(1f,0f,0f, 0f,0f,-1f, 0f,1f,0f)
        val landscape = floatArrayOf(0f,-1f,0f, 0f,0f,-1f, 1f,0f,0f)
        assertEquals(forwardDirection(portrait, 7f), forwardDirection(landscape, 7f))
        val east = floatArrayOf(0f,0f,-1f, -1f,0f,0f, 0f,1f,0f)
        assertEquals(90f, forwardDirection(east, 0f).bearing!!, 0.001f)
    }
}
