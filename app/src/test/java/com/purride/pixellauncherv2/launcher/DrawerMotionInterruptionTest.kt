package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DrawerMotionInterruptionTest {

    @Test
    fun settlesMotionWhenTappingSearchResultDuringInertia() {
        val runtime = DrawerMotionRuntimeState(
            isDragging = false,
            isAnimating = true,
            residualOffsetPx = 11f,
            velocityPxPerSecond = 420f,
        )

        val settled = DrawerMotionInterruption.settleBeforeExplicitAction(runtime)

        assertFalse(settled.isDragging)
        assertFalse(settled.isAnimating)
        assertEquals(0f, settled.residualOffsetPx, 0.0001f)
        assertEquals(0f, settled.velocityPxPerSecond, 0.0001f)
    }

    @Test
    fun settlesMotionWhenKeyboardMovesSelectionDuringInertia() {
        val runtime = DrawerMotionRuntimeState(
            isDragging = true,
            isAnimating = true,
            residualOffsetPx = -7f,
            velocityPxPerSecond = -260f,
        )

        val settled = DrawerMotionInterruption.settleBeforeExplicitAction(runtime)

        assertFalse(settled.isDragging)
        assertFalse(settled.isAnimating)
        assertEquals(0f, settled.residualOffsetPx, 0.0001f)
        assertEquals(0f, settled.velocityPxPerSecond, 0.0001f)
    }

    @Test
    fun settlesMotionWhenHorizontalPageSwitchStartsDuringInertia() {
        val runtime = DrawerMotionRuntimeState(
            isDragging = false,
            isAnimating = true,
            residualOffsetPx = 4f,
            velocityPxPerSecond = 180f,
        )

        val settled = DrawerMotionInterruption.settleBeforeExplicitAction(runtime)

        assertFalse(settled.isDragging)
        assertFalse(settled.isAnimating)
        assertEquals(0f, settled.residualOffsetPx, 0.0001f)
        assertEquals(0f, settled.velocityPxPerSecond, 0.0001f)
    }
}
