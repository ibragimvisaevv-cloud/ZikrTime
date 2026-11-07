package com.zikrtime.app.prayer

import org.junit.Assert.*
import org.junit.Test

class QiblaProjectionTest {
    @Test fun horizontalPhoneHasNoCameraBearing() {
        assertNull(cameraDirection(floatArrayOf(1f,0f,0f, 0f,1f,0f, 0f,0f,1f), 0f).bearing)
    }

    @Test fun uprightRearCameraFacesNorth() {
        val value = cameraDirection(floatArrayOf(1f,0f,0f, 0f,0f,-1f, 0f,1f,0f), 7f)
        assertEquals(7f, value.bearing!!, 0.001f)
        assertEquals(0f, value.elevation, 0.001f)
    }

    @Test fun turnCanCrossNorthAndMultipleFullRotations() {
        assertEquals(2f, qiblaAngleDifference(1f, 359f), 0.001f)
        assertEquals(2f, qiblaAngleDifference(1f, 1079f), 0.001f)
        assertEquals(-2f, qiblaAngleDifference(359f, -719f), 0.001f)
    }

    @Test fun rearCameraBearingSurvivesPortraitToLandscapeRoll() {
        val portrait = floatArrayOf(1f,0f,0f, 0f,0f,-1f, 0f,1f,0f)
        val landscape = floatArrayOf(0f,-1f,0f, 0f,0f,-1f, 1f,0f,0f)
        assertEquals(cameraDirection(portrait, 7f), cameraDirection(landscape, 7f))
        val east = floatArrayOf(0f,0f,-1f, -1f,0f,0f, 0f,1f,0f)
        assertEquals(90f, cameraDirection(east, 0f).bearing!!, 0.001f)
    }
}
