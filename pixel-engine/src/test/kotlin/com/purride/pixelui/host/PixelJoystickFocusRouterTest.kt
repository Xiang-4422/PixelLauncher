package com.purride.pixelui

import com.purride.pixelui.internal.host.PixelJoystickFocusRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PixelJoystickFocusRouterTest {
    @Test
    fun deadZoneDoesNotEmitDirection() {
        val router = PixelJoystickFocusRouter(deadZone = 0.5f)

        assertNull(router.onAxes(xAxis = 0.25f, yAxis = 0.1f, eventTimeMs = 0L))
    }

    @Test
    fun directionChangeEmitsImmediately() {
        val router = PixelJoystickFocusRouter(deadZone = 0.5f)

        assertEquals(PixelKey.ARROW_RIGHT, router.onAxes(xAxis = 0.8f, yAxis = 0f, eventTimeMs = 0L)?.key)
        assertEquals(PixelKey.ARROW_LEFT, router.onAxes(xAxis = -0.8f, yAxis = 0f, eventTimeMs = 20L)?.key)
        assertEquals(PixelKey.ARROW_DOWN, router.onAxes(xAxis = 0f, yAxis = 0.8f, eventTimeMs = 40L)?.key)
    }

    @Test
    fun sameDirectionRepeatsOnlyAfterInterval() {
        val router = PixelJoystickFocusRouter(deadZone = 0.5f, repeatDelayMs = 300L, repeatIntervalMs = 160L)

        assertEquals(PixelKey.ARROW_DOWN, router.onAxes(xAxis = 0f, yAxis = 0.8f, eventTimeMs = 0L)?.key)
        assertNull(router.onAxes(xAxis = 0f, yAxis = 0.8f, eventTimeMs = 299L))
        assertEquals(PixelKey.ARROW_DOWN, router.onAxes(xAxis = 0f, yAxis = 0.8f, eventTimeMs = 300L)?.key)
        assertNull(router.onAxes(xAxis = 0f, yAxis = 0.8f, eventTimeMs = 459L))
        assertEquals(PixelKey.ARROW_DOWN, router.onAxes(xAxis = 0f, yAxis = 0.8f, eventTimeMs = 460L)?.key)
    }

    @Test
    fun returningToNeutralResetsRepeatState() {
        val router = PixelJoystickFocusRouter(deadZone = 0.5f, repeatDelayMs = 300L)

        assertEquals(PixelKey.ARROW_UP, router.onAxes(xAxis = 0f, yAxis = -0.8f, eventTimeMs = 0L)?.key)
        assertNull(router.onAxes(xAxis = 0f, yAxis = 0f, eventTimeMs = 100L))
        assertEquals(PixelKey.ARROW_UP, router.onAxes(xAxis = 0f, yAxis = -0.8f, eventTimeMs = 120L)?.key)
    }

    @Test
    fun hatAndDominantAxesResolveToExpectedDirection() {
        val router = PixelJoystickFocusRouter(deadZone = 0.5f)

        assertEquals(PixelKey.ARROW_LEFT, router.onAxes(xAxis = 0.2f, yAxis = 0f, hatX = -1f, eventTimeMs = 0L)?.key)
        assertEquals(PixelKey.ARROW_DOWN, router.onAxes(xAxis = 0.6f, yAxis = 0.9f, eventTimeMs = 10L)?.key)
    }
}
