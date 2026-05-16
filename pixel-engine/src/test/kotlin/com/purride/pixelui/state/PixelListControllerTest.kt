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

    @Test
    fun scrollItemIntoViewHandlesVariableItemHeightsAndSpacingOffsets() {
        val state = controller.create(initialScrollOffsetPx = 0f)
        state.itemTopOffsetsPx = intArrayOf(0, 10, 28, 44)
        state.itemHeightsPx = intArrayOf(8, 16, 12, 20)

        controller.sync(
            state = state,
            viewportHeightPx = 24,
            contentHeightPx = 64,
        )

        controller.scrollItemIntoView(state, itemIndex = 2)
        assertEquals(16f, state.scrollOffsetPx, 0.001f)

        controller.scrollItemIntoView(state, itemIndex = 3)
        assertEquals(40f, state.scrollOffsetPx, 0.001f)

        controller.scrollItemIntoView(state, itemIndex = 1)
        assertEquals(10f, state.scrollOffsetPx, 0.001f)
    }

    @Test
    fun scrollItemIntoViewIgnoresInvalidItemIndex() {
        val state = controller.create(initialScrollOffsetPx = 8f)
        state.itemTopOffsetsPx = intArrayOf(0, 12)
        state.itemHeightsPx = intArrayOf(8, 8)

        controller.sync(
            state = state,
            viewportHeightPx = 10,
            contentHeightPx = 24,
        )
        controller.scrollItemIntoView(state, itemIndex = 4)

        assertEquals(8f, state.scrollOffsetPx, 0.001f)
    }

    @Test
    fun endDragStartsSettlingWhenVelocityIsLargeEnough() {
        val state = controller.create(initialScrollOffsetPx = 10f)

        controller.endDrag(
            state = state,
            velocityPxPerSecond = -600f,
            viewportHeightPx = 20,
            contentHeightPx = 80,
        )

        assertFalse(state.isDragging)
        assertTrue(state.isSettling)
        assertEquals(-600f, state.scrollVelocityPxPerSecond, 0.001f)
    }

    @Test
    fun stepMovesScrollOffsetAndGraduallySlowsDown() {
        val state = controller.create(initialScrollOffsetPx = 10f)

        controller.endDrag(
            state = state,
            velocityPxPerSecond = -600f,
            viewportHeightPx = 20,
            contentHeightPx = 120,
        )

        controller.step(
            state = state,
            deltaMs = 100,
            viewportHeightPx = 20,
            contentHeightPx = 120,
        )

        assertEquals(70f, state.scrollOffsetPx, 0.001f)
        assertEquals(-360f, state.scrollVelocityPxPerSecond, 0.001f)
        assertTrue(state.isSettling)
    }

    @Test
    fun stepStopsSettlingAtScrollBoundary() {
        val state = controller.create(initialScrollOffsetPx = 55f)

        controller.endDrag(
            state = state,
            velocityPxPerSecond = -600f,
            viewportHeightPx = 20,
            contentHeightPx = 80,
        )

        controller.step(
            state = state,
            deltaMs = 100,
            viewportHeightPx = 20,
            contentHeightPx = 80,
        )

        assertEquals(60f, state.scrollOffsetPx, 0.001f)
        assertFalse(state.isSettling)
        assertEquals(0f, state.scrollVelocityPxPerSecond, 0.001f)
    }

    @Test
    fun stepStopsSettlingWhenVelocityCrossesZero() {
        val state = controller.create(initialScrollOffsetPx = 30f)

        controller.endDrag(
            state = state,
            velocityPxPerSecond = 60f,
            viewportHeightPx = 20,
            contentHeightPx = 100,
        )

        controller.step(
            state = state,
            deltaMs = 100,
            viewportHeightPx = 20,
            contentHeightPx = 100,
        )

        assertFalse(state.isSettling)
        assertEquals(0f, state.scrollVelocityPxPerSecond, 0.001f)
        assertEquals(24f, state.scrollOffsetPx, 0.001f)
    }

    @Test
    fun customPhysicsCanEnableResistedOverscrollDuringDrag() {
        val controller = PixelListController(
            physics = PixelScrollPhysics(
                bounceEnabled = true,
                bounceOverscrollLimitPx = 10f,
                bounceResistance = 0.5f,
            ),
        )
        val state = controller.create()

        controller.dragBy(
            state = state,
            deltaPx = 8f,
            viewportHeightPx = 20,
            contentHeightPx = 50,
        )

        assertEquals(-4f, state.scrollOffsetPx, 0.001f)
    }

    @Test
    fun flingExtensionStartsSettlingFromCachedViewportMetrics() {
        val state = controller.create(initialScrollOffsetPx = 10f)

        controller.sync(
            state = state,
            viewportHeightPx = 20,
            contentHeightPx = 80,
        )
        controller.fling(
            state = state,
            velocityPxPerSecond = -300f,
        )

        assertTrue(state.isSettling)
        assertEquals(-300f, state.scrollVelocityPxPerSecond, 0.001f)
    }
}
