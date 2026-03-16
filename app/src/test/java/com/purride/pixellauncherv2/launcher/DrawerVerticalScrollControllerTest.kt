package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerVerticalScrollControllerTest {

    @Test
    fun consumeDragDoesNotStepWhenBelowThreshold() {
        val result = DrawerVerticalScrollController.consumeDrag(
            residualOffsetPx = 0f,
            deltaPx = -16f,
            thresholds = DrawerVerticalScrollThresholds(
                upwardStepPx = 25f,
                downwardStepPx = 17f,
            ),
        )

        assertEquals(0, result.stepDelta)
        assertEquals(-16f, result.residualOffsetPx, 0.0001f)
    }

    @Test
    fun consumeDragUsesAsymmetricThresholds() {
        val upResult = DrawerVerticalScrollController.consumeDrag(
            residualOffsetPx = -24f,
            deltaPx = -2f,
            thresholds = DrawerVerticalScrollThresholds(
                upwardStepPx = 25f,
                downwardStepPx = 17f,
            ),
        )
        assertEquals(1, upResult.stepDelta)
        assertEquals(-1f, upResult.residualOffsetPx, 0.0001f)

        val downResult = DrawerVerticalScrollController.consumeDrag(
            residualOffsetPx = 16f,
            deltaPx = 2f,
            thresholds = DrawerVerticalScrollThresholds(
                upwardStepPx = 25f,
                downwardStepPx = 17f,
            ),
        )
        assertEquals(-1, downResult.stepDelta)
        assertEquals(1f, downResult.residualOffsetPx, 0.0001f)
    }

    @Test
    fun stepAnimationProducesMultiStepFlingAndKeepsAnimating() {
        val result = DrawerVerticalScrollController.stepAnimation(
            residualOffsetPx = 0f,
            velocityPxPerSecond = -1700f,
            thresholds = DrawerVerticalScrollThresholds(
                upwardStepPx = 17f,
                downwardStepPx = 17f,
            ),
            deltaMs = 60L,
        )

        assertTrue(result.stepDelta >= 5)
        assertTrue(result.isAnimating)
        assertTrue(result.nextVelocityPxPerSecond < 0f)
    }

    @Test
    fun lowVelocityReleaseStartsDirectionalSettleUsingResidualSign() {
        val result = DrawerVerticalScrollController.release(
            residualOffsetPx = -8f,
            velocityPxPerSecond = 10f,
            thresholds = DrawerVerticalScrollThresholds(
                upwardStepPx = 17f,
                downwardStepPx = 17f,
            ),
        )

        assertEquals(0, result.stepDelta)
        assertEquals(0f, result.nextVelocityPxPerSecond, 0.0001f)
        assertTrue(result.isAnimating)
        assertNotNull(result.settleTarget)
        assertEquals(1, result.settleTarget?.direction)
        assertEquals(-17f, result.settleTarget?.targetResidualPx ?: 0f, 0.0001f)
    }

    @Test
    fun settlePhaseContinuesUpwardUntilNextStableItem() {
        var step = DrawerVerticalScrollController.release(
            residualOffsetPx = -8f,
            velocityPxPerSecond = 0f,
            thresholds = DrawerVerticalScrollThresholds(
                upwardStepPx = 17f,
                downwardStepPx = 17f,
            ),
        )
        var accumulatedStepDelta = 0
        var guard = 0
        while (step.isAnimating && guard < 32) {
            step = DrawerVerticalScrollController.stepAnimation(
                residualOffsetPx = step.residualOffsetPx,
                velocityPxPerSecond = step.nextVelocityPxPerSecond,
                thresholds = DrawerVerticalScrollThresholds(
                    upwardStepPx = 17f,
                    downwardStepPx = 17f,
                ),
                deltaMs = 16L,
                settleTarget = step.settleTarget,
            )
            accumulatedStepDelta += step.stepDelta
            guard += 1
        }

        assertTrue(guard < 32)
        assertFalse(step.isAnimating)
        assertNull(step.settleTarget)
        assertEquals(1, accumulatedStepDelta)
        assertEquals(0f, step.residualOffsetPx, 0.0001f)
        assertEquals(0f, step.nextVelocityPxPerSecond, 0.0001f)
    }

    @Test
    fun settlePhaseCommitsSelectionBeforeResidualFullySettles() {
        val release = DrawerVerticalScrollController.release(
            residualOffsetPx = -8f,
            velocityPxPerSecond = 0f,
            thresholds = DrawerVerticalScrollThresholds(
                upwardStepPx = 17f,
                downwardStepPx = 17f,
            ),
        )

        val firstStep = DrawerVerticalScrollController.stepAnimation(
            residualOffsetPx = release.residualOffsetPx,
            velocityPxPerSecond = release.nextVelocityPxPerSecond,
            thresholds = DrawerVerticalScrollThresholds(
                upwardStepPx = 17f,
                downwardStepPx = 17f,
            ),
            deltaMs = 16L,
            settleTarget = release.settleTarget,
        )

        assertEquals(1, firstStep.stepDelta)
        assertTrue(firstStep.residualOffsetPx > 0f)
        assertTrue(firstStep.isAnimating)
        assertNull(firstStep.settleTarget)
    }

    @Test
    fun settlePhaseContinuesDownwardUntilPreviousStableItem() {
        var step = DrawerVerticalScrollController.release(
            residualOffsetPx = 9f,
            velocityPxPerSecond = 0f,
            thresholds = DrawerVerticalScrollThresholds(
                upwardStepPx = 17f,
                downwardStepPx = 17f,
            ),
        )
        var accumulatedStepDelta = 0
        var guard = 0
        while (step.isAnimating && guard < 32) {
            step = DrawerVerticalScrollController.stepAnimation(
                residualOffsetPx = step.residualOffsetPx,
                velocityPxPerSecond = step.nextVelocityPxPerSecond,
                thresholds = DrawerVerticalScrollThresholds(
                    upwardStepPx = 17f,
                    downwardStepPx = 17f,
                ),
                deltaMs = 16L,
                settleTarget = step.settleTarget,
            )
            accumulatedStepDelta += step.stepDelta
            guard += 1
        }

        assertTrue(guard < 32)
        assertFalse(step.isAnimating)
        assertNull(step.settleTarget)
        assertEquals(-1, accumulatedStepDelta)
        assertEquals(0f, step.residualOffsetPx, 0.0001f)
        assertEquals(0f, step.nextVelocityPxPerSecond, 0.0001f)
    }
}
