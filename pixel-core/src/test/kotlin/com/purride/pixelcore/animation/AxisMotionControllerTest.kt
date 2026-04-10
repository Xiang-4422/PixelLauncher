package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AxisMotionControllerTest {

    private val controller = AxisMotionController()

    @Test
    fun dragByClampsOffsetWithinCallerProvidedRange() {
        var state = controller.create()
        state = controller.startDrag(state)
        state = controller.dragBy(
            state = state,
            deltaPx = 80f,
            minOffsetPx = -40f,
            maxOffsetPx = 40f,
        )
        assertEquals(40f, state.dragOffsetPx, 1e-4f)

        state = controller.dragBy(
            state = state,
            deltaPx = -120f,
            minOffsetPx = -40f,
            maxOffsetPx = 40f,
        )
        assertEquals(-40f, state.dragOffsetPx, 1e-4f)
    }

    @Test
    fun settleToRunsEaseOutAnimationUntilTargetOffset() {
        var state = controller.create()
        state = controller.startDrag(state)
        state = controller.dragBy(
            state = state,
            deltaPx = 20f,
            minOffsetPx = -100f,
            maxOffsetPx = 100f,
        )
        state = controller.settleTo(state, targetOffsetPx = 100f)

        assertTrue(state.isSettling)
        val midState = controller.step(state, deltaMs = 120L)
        assertTrue(controller.visualOffsetPx(midState) > 20f)
        assertTrue(controller.visualOffsetPx(midState) < 100f)

        val settledState = controller.step(midState, deltaMs = 120L)
        assertFalse(settledState.isSettling)
        assertEquals(100f, settledState.dragOffsetPx, 1e-4f)
    }

    @Test
    fun settleToSnapsImmediatelyWhenAlreadyCloseEnough() {
        var state = controller.create()
        state = controller.startDrag(state)
        state = controller.dragBy(
            state = state,
            deltaPx = 0.1f,
            minOffsetPx = -10f,
            maxOffsetPx = 10f,
        )

        val settledState = controller.settleTo(state, targetOffsetPx = 0f)
        assertFalse(settledState.isSettling)
        assertEquals(0f, settledState.dragOffsetPx, 1e-4f)
    }
}
