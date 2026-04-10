package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagerGesturePolicyTest {

    @Test
    fun horizontalDragStartsOnlyWhenHorizontalMovementDominates() {
        assertTrue(
            PagerGesturePolicy.shouldStartDrag(
                axis = PixelAxis.HORIZONTAL,
                deltaX = 20f,
                deltaY = 4f,
                touchSlopPx = 8f,
            ),
        )
        assertFalse(
            PagerGesturePolicy.shouldStartDrag(
                axis = PixelAxis.HORIZONTAL,
                deltaX = 10f,
                deltaY = 10f,
                touchSlopPx = 8f,
            ),
        )
    }

    @Test
    fun verticalDragStartsOnlyWhenVerticalMovementDominates() {
        assertTrue(
            PagerGesturePolicy.shouldStartDrag(
                axis = PixelAxis.VERTICAL,
                deltaX = 3f,
                deltaY = 18f,
                touchSlopPx = 8f,
            ),
        )
        assertFalse(
            PagerGesturePolicy.shouldStartDrag(
                axis = PixelAxis.VERTICAL,
                deltaX = 18f,
                deltaY = 10f,
                touchSlopPx = 8f,
            ),
        )
    }

    @Test
    fun movementBelowTouchSlopDoesNotStartPagerDrag() {
        assertFalse(
            PagerGesturePolicy.shouldStartDrag(
                axis = PixelAxis.HORIZONTAL,
                deltaX = 6f,
                deltaY = 1f,
                touchSlopPx = 8f,
            ),
        )
    }
}
