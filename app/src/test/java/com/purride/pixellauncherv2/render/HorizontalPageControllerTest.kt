package com.purride.pixellauncherv2.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HorizontalPageControllerTest {

    private val controller = HorizontalPageController()

    @Test
    fun dragPastDistanceThresholdSettlesToPreviousPage() {
        var state = controller.create(pageCount = 3, currentIndex = 1)
        state = controller.startDrag(state)
        state = controller.dragBy(state = state, deltaPx = 30f, pageWidth = 100)
        state = controller.endDrag(
            state = state,
            pageWidth = 100,
            velocityPxPerSecond = 0f,
        )

        assertTrue(state.isSettling)
        assertEquals(0, state.settleTargetIndex)

        state = controller.step(state, deltaMs = 200L)
        assertEquals(false, state.isSettling)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun dragPastDistanceThresholdSettlesToNextPage() {
        var state = controller.create(pageCount = 3, currentIndex = 1)
        state = controller.startDrag(state)
        state = controller.dragBy(state = state, deltaPx = -30f, pageWidth = 100)
        state = controller.endDrag(
            state = state,
            pageWidth = 100,
            velocityPxPerSecond = 0f,
        )

        assertEquals(2, state.settleTargetIndex)
        state = controller.step(state, deltaMs = 200L)
        assertEquals(2, state.currentIndex)
    }

    @Test
    fun highVelocityTriggersPageChangeEvenWithSmallDistance() {
        var state = controller.create(pageCount = 3, currentIndex = 1)
        state = controller.startDrag(state)
        state = controller.dragBy(state = state, deltaPx = 8f, pageWidth = 100)
        state = controller.endDrag(
            state = state,
            pageWidth = 100,
            velocityPxPerSecond = 80f,
        )

        assertEquals(0, state.settleTargetIndex)
    }

    @Test
    fun boundaryClampsDraggingAndTargetIndex() {
        var state = controller.create(pageCount = 3, currentIndex = 0)
        state = controller.startDrag(state)
        state = controller.dragBy(
            state = state,
            deltaPx = 40f,
            pageWidth = 100,
        )
        assertEquals(0f, state.dragOffsetPx)

        state = controller.endDrag(
            state = state,
            pageWidth = 100,
            velocityPxPerSecond = 120f,
        )
        assertEquals(0, state.settleTargetIndex)

        state = controller.create(pageCount = 3, currentIndex = 2)
        state = controller.startDrag(state)
        state = controller.dragBy(
            state = state,
            deltaPx = -40f,
            pageWidth = 100,
        )
        assertEquals(0f, state.dragOffsetPx)
        state = controller.endDrag(
            state = state,
            pageWidth = 100,
            velocityPxPerSecond = -120f,
        )
        assertEquals(2, state.settleTargetIndex)
    }
}
