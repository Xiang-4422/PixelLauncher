package com.purride.pixelui.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class PixelRefreshIndicatorControllerTest {
    private val controller = PixelRefreshIndicatorController()

    @Test
    fun pullBelowThresholdResetsWithoutRefreshing() {
        val state: PixelRefreshIndicatorState = controller.create()

        controller.startPull(state)
        controller.updatePull(state, distancePx = 6f, thresholdPx = 12)
        val triggered = controller.endPull(state, thresholdPx = 12)

        assertFalse(triggered)
        assertFalse(state.isRefreshing)
        assertFalse(state.isArmed)
        assertEquals(0f, state.pullDistancePx, 0.001f)
    }

    @Test
    fun pullPastThresholdArmsAndStartsRefreshingOnRelease() {
        val state = controller.create()

        controller.startPull(state)
        controller.updatePull(state, distancePx = 18f, thresholdPx = 12)
        assertTrue(state.isArmed)

        val triggered = controller.endPull(state, thresholdPx = 12)

        assertTrue(triggered)
        assertTrue(state.isRefreshing)
        assertFalse(state.isArmed)
        assertEquals(12f, state.pullDistancePx, 0.001f)
    }

    @Test
    fun completeRefreshClearsRefreshingState() {
        val state = controller.create()
        controller.startPull(state)
        controller.updatePull(state, distancePx = 20f, thresholdPx = 10)
        controller.endPull(state, thresholdPx = 10)

        controller.completeRefresh(state)

        assertFalse(state.isRefreshing)
        assertFalse(state.isArmed)
        assertEquals(0f, state.pullDistancePx, 0.001f)
    }
}
