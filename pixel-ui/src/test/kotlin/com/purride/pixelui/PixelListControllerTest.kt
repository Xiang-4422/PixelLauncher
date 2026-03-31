package com.purride.pixelui

import com.purride.pixelui.state.PixelListController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelListControllerTest {

    private val controller = PixelListController()

    @Test
    fun dragByClampsScrollOffsetWithinContentRange() {
        val state = controller.create()

        controller.dragBy(
            state = state,
            deltaPx = -18f,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )
        assertEquals(18f, state.scrollOffsetPx, 0.001f)

        controller.dragBy(
            state = state,
            deltaPx = -40f,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )
        assertEquals(30f, state.scrollOffsetPx, 0.001f)

        controller.dragBy(
            state = state,
            deltaPx = 80f,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )
        assertEquals(0f, state.scrollOffsetPx, 0.001f)
    }

    @Test
    fun syncClampsInitialOffsetToViewportUpperBound() {
        val state = controller.create(initialScrollOffsetPx = 80f)

        controller.sync(
            state = state,
            viewportHeightPx = 24,
            contentHeightPx = 40,
        )

        assertEquals(16f, state.scrollOffsetPx, 0.001f)
    }

    @Test
    fun canConsumeDragReflectsTopAndBottomBoundaries() {
        val state = controller.create(initialScrollOffsetPx = 10f)

        controller.sync(
            state = state,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )
        assertTrue(controller.canConsumeDrag(state, deltaPx = 6f, viewportHeightPx = 20, contentHeightPx = 50))
        assertTrue(controller.canConsumeDrag(state, deltaPx = -6f, viewportHeightPx = 20, contentHeightPx = 50))

        controller.scrollTo(
            state = state,
            targetOffsetPx = 0f,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )
        assertFalse(controller.canConsumeDrag(state, deltaPx = 6f, viewportHeightPx = 20, contentHeightPx = 50))

        controller.scrollTo(
            state = state,
            targetOffsetPx = 30f,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )
        assertFalse(controller.canConsumeDrag(state, deltaPx = -6f, viewportHeightPx = 20, contentHeightPx = 50))
    }

    @Test
    fun scrollItemIntoViewMovesOnlyWhenTargetLeavesViewport() {
        val state = controller.create(initialScrollOffsetPx = 0f)
        state.itemTopOffsetsPx = intArrayOf(0, 12, 24, 36)
        state.itemHeightsPx = intArrayOf(8, 8, 8, 8)

        controller.sync(
            state = state,
            viewportHeightPx = 20,
            contentHeightPx = 44,
        )

        controller.scrollItemIntoView(state, itemIndex = 1)
        assertEquals(0f, state.scrollOffsetPx, 0.001f)

        controller.scrollItemIntoView(state, itemIndex = 2)
        assertEquals(12f, state.scrollOffsetPx, 0.001f)

        controller.scrollItemIntoView(state, itemIndex = 0)
        assertEquals(0f, state.scrollOffsetPx, 0.001f)
    }
}
