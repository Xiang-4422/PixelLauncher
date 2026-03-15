package com.purride.pixellauncherv2.system

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenGravityMapperTest {

    @Test
    fun mapsRotation0() {
        val (x, y) = ScreenGravityMapper.mapToScreen(
            rawGravityX = 2f,
            rawGravityY = -3f,
            rawGravityZ = 1f,
            rotation = Surface.ROTATION_0,
        )
        assertEquals(-2f, x, 1e-4f)
        assertEquals(-3f, y, 1e-4f)
    }

    @Test
    fun mapsRotation90() {
        val (x, y) = ScreenGravityMapper.mapToScreen(
            rawGravityX = 2f,
            rawGravityY = -3f,
            rawGravityZ = 1f,
            rotation = Surface.ROTATION_90,
        )
        assertEquals(-3f, x, 1e-4f)
        assertEquals(2f, y, 1e-4f)
    }

    @Test
    fun mapsRotation180() {
        val (x, y) = ScreenGravityMapper.mapToScreen(
            rawGravityX = 2f,
            rawGravityY = -3f,
            rawGravityZ = 1f,
            rotation = Surface.ROTATION_180,
        )
        assertEquals(2f, x, 1e-4f)
        assertEquals(3f, y, 1e-4f)
    }

    @Test
    fun mapsRotation270() {
        val (x, y) = ScreenGravityMapper.mapToScreen(
            rawGravityX = 2f,
            rawGravityY = -3f,
            rawGravityZ = 1f,
            rotation = Surface.ROTATION_270,
        )
        assertEquals(3f, x, 1e-4f)
        assertEquals(-2f, y, 1e-4f)
    }
}
