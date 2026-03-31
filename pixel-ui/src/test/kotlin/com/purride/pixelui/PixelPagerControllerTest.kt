package com.purride.pixelui

import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.state.PixelPagerController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelPagerControllerTest {

    private val controller = PixelPagerController()

    @Test
    fun horizontalDragPastThresholdChangesToPreviousPage() {
        val state = controller.create(pageCount = 3, currentPage = 1, axis = PixelAxis.HORIZONTAL)
        controller.startDrag(state)
        controller.dragBy(state, deltaPx = 45f, viewportSizePx = 100)
        controller.endDrag(
            state = state,
            viewportSizePx = 100,
            velocityPxPerSecond = 0f,
        )

        assertTrue(state.isSettling)
        assertEquals(0, state.settleTargetPage)

        controller.step(state, deltaMs = 240L)
        assertFalse(state.isSettling)
        assertEquals(0, state.currentPage)
    }

    @Test
    fun verticalDragPastThresholdChangesToNextPage() {
        val state = controller.create(pageCount = 3, currentPage = 1, axis = PixelAxis.VERTICAL)
        controller.startDrag(state)
        controller.dragBy(state, deltaPx = -45f, viewportSizePx = 100)
        controller.endDrag(
            state = state,
            viewportSizePx = 100,
            velocityPxPerSecond = 0f,
        )

        controller.step(state, deltaMs = 240L)
        assertEquals(2, state.currentPage)
    }

    @Test
    fun velocityCanTriggerPageChangeEvenWithSmallDistance() {
        val state = controller.create(pageCount = 3, currentPage = 1, axis = PixelAxis.HORIZONTAL)
        controller.startDrag(state)
        controller.dragBy(state, deltaPx = 8f, viewportSizePx = 100)
        controller.endDrag(
            state = state,
            viewportSizePx = 100,
            velocityPxPerSecond = 80f,
        )

        assertEquals(0, state.settleTargetPage)
    }

    @Test
    fun boundaryClampsPageTargetAtEdges() {
        val firstPageState = controller.create(pageCount = 3, currentPage = 0, axis = PixelAxis.HORIZONTAL)
        controller.startDrag(firstPageState)
        controller.dragBy(firstPageState, deltaPx = 40f, viewportSizePx = 100)
        controller.endDrag(firstPageState, viewportSizePx = 100, velocityPxPerSecond = 120f)
        assertEquals(0, firstPageState.settleTargetPage)

        val lastPageState = controller.create(pageCount = 3, currentPage = 2, axis = PixelAxis.VERTICAL)
        controller.startDrag(lastPageState)
        controller.dragBy(lastPageState, deltaPx = -40f, viewportSizePx = 100)
        controller.endDrag(lastPageState, viewportSizePx = 100, velocityPxPerSecond = -120f)
        assertEquals(2, lastPageState.settleTargetPage)
    }
}
