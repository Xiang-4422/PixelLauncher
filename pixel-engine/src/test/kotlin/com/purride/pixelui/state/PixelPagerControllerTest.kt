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
        controller.startDrag(state, viewportSizePx = 100)
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
        controller.startDrag(state, viewportSizePx = 100)
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
    fun sameDirectionDragDuringSettleContinuesToFollowingPage() {
        val state = controller.create(pageCount = 3, currentPage = 0, axis = PixelAxis.HORIZONTAL)
        controller.startDrag(state, viewportSizePx = 100)
        controller.dragBy(state, deltaPx = -45f, viewportSizePx = 100)
        controller.endDrag(state, viewportSizePx = 100, velocityPxPerSecond = 0f)
        controller.step(state, deltaMs = 60L)
        val beforeTakeover = controller.snapshot(state)

        controller.startDrag(state, viewportSizePx = 100)
        val afterTakeover = controller.snapshot(state)

        assertEquals(1, state.currentPage)
        assertTrue(state.isDragging)
        assertEquals(
            beforeTakeover.dragOffsetPx + 100f,
            afterTakeover.dragOffsetPx,
            0.001f,
        )

        controller.dragBy(state, deltaPx = -45f, viewportSizePx = 100)
        controller.endDrag(state, viewportSizePx = 100, velocityPxPerSecond = 0f)
        assertEquals(2, state.settleTargetPage)

        controller.step(state, deltaMs = 240L)
        assertEquals(2, state.currentPage)
        assertFalse(state.isSettling)
    }

    @Test
    fun velocityCanTriggerPageChangeEvenWithSmallDistance() {
        val state = controller.create(pageCount = 3, currentPage = 1, axis = PixelAxis.HORIZONTAL)
        controller.startDrag(state, viewportSizePx = 100)
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
        controller.startDrag(firstPageState, viewportSizePx = 100)
        controller.dragBy(firstPageState, deltaPx = 40f, viewportSizePx = 100)
        controller.endDrag(firstPageState, viewportSizePx = 100, velocityPxPerSecond = 120f)
        assertEquals(0, firstPageState.settleTargetPage)

        val lastPageState = controller.create(pageCount = 3, currentPage = 2, axis = PixelAxis.VERTICAL)
        controller.startDrag(lastPageState, viewportSizePx = 100)
        controller.dragBy(lastPageState, deltaPx = -40f, viewportSizePx = 100)
        controller.endDrag(lastPageState, viewportSizePx = 100, velocityPxPerSecond = -120f)
        assertEquals(2, lastPageState.settleTargetPage)
    }

    @Test
    fun cancelDragKeepsCurrentPageAndSettlesBackToZeroOffset() {
        val state = controller.create(pageCount = 3, currentPage = 1, axis = PixelAxis.HORIZONTAL)
        controller.startDrag(state, viewportSizePx = 100)
        controller.dragBy(state, deltaPx = -30f, viewportSizePx = 100)

        controller.cancelDrag(state)
        assertEquals(1, state.settleTargetPage)

        controller.step(state, deltaMs = 240L)
        assertEquals(1, state.currentPage)
        assertFalse(state.isSettling)
    }

    @Test
    fun saveAndRestoreStateKeepsCurrentPageAndClearsMotion() {
        val source = controller.create(pageCount = 5, currentPage = 3, axis = PixelAxis.VERTICAL)
        val savedState = controller.saveState(source)
        val restored = controller.create(pageCount = 2, currentPage = 0, axis = PixelAxis.HORIZONTAL)

        controller.startDrag(restored, viewportSizePx = 100)
        controller.dragBy(restored, deltaPx = -30f, viewportSizePx = 100)
        controller.restoreState(
            state = restored,
            savedState = savedState,
            pageCount = 4,
        )

        assertEquals(PixelAxis.VERTICAL, restored.axis)
        assertEquals(3, restored.currentPage)
        assertEquals(3, restored.settleTargetPage)
        assertFalse(restored.isDragging)
        assertFalse(restored.isSettling)
    }

    @Test
    fun restoreStateClampsPageWhenPageCountShrinks() {
        val state = controller.create(pageCount = 5, currentPage = 0)

        controller.restoreState(
            state = state,
            savedState = controller.saveState(controller.create(pageCount = 5, currentPage = 4)),
            pageCount = 3,
        )

        assertEquals(2, state.currentPage)
        assertEquals(2, state.settleTargetPage)
    }

    @Test
    fun stepDoesNotNotifyWhenPagerIsIdle() {
        val state = controller.create(pageCount = 3, currentPage = 1)
        var notifications = 0
        controller.addListener { notifications++ }

        controller.step(state, deltaMs = 16L)
        controller.step(state, deltaMs = 16L)

        // 静止 pager 每帧 step 不得触发监听者，否则宿主会无限重绘空转。
        assertEquals(0, notifications)
    }

    @Test
    fun stepNotifiesWhileSettling() {
        val state = controller.create(pageCount = 3, currentPage = 1, axis = PixelAxis.HORIZONTAL)
        controller.startDrag(state, viewportSizePx = 100)
        controller.dragBy(state, deltaPx = 45f, viewportSizePx = 100)
        controller.endDrag(state, viewportSizePx = 100, velocityPxPerSecond = 0f)
        assertTrue(state.isSettling)

        var notifications = 0
        controller.addListener { notifications++ }
        controller.step(state, deltaMs = 16L)

        // 仍在 settling 时 step 必须通知，settle 动画才能逐帧推进。
        assertEquals(1, notifications)
    }
}
