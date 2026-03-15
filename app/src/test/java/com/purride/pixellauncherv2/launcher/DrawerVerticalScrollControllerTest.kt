package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
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
    fun lowVelocityReleaseEntersSnapOnly() {
        val result = DrawerVerticalScrollController.release(
            residualOffsetPx = 8f,
            velocityPxPerSecond = 10f,
            thresholds = DrawerVerticalScrollThresholds(
                upwardStepPx = 17f,
                downwardStepPx = 17f,
            ),
        )

        assertEquals(0, result.stepDelta)
        assertEquals(0f, result.nextVelocityPxPerSecond, 0.0001f)
        assertTrue(result.isAnimating)
    }

    @Test
    fun snapAnimationConvergesResidualToZero() {
        var residual = 8f
        var velocity = 0f
        repeat(10) {
            val step = DrawerVerticalScrollController.stepAnimation(
                residualOffsetPx = residual,
                velocityPxPerSecond = velocity,
                thresholds = DrawerVerticalScrollThresholds(
                    upwardStepPx = 17f,
                    downwardStepPx = 17f,
                ),
                deltaMs = 16L,
            )
            residual = step.residualOffsetPx
            velocity = step.nextVelocityPxPerSecond
        }
        assertEquals(0f, residual, 0.5f)
        assertEquals(0f, velocity, 0.0001f)
    }
}
