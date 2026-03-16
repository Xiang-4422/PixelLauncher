package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerReleaseSettleTest {

    @Test
    fun releaseBuildsUpwardTargetFromVelocity() {
        val release = DrawerVerticalScrollController.release(
            residualOffsetPx = -4f,
            velocityPxPerSecond = -1700f,
            thresholds = DrawerVerticalScrollThresholds(
                upwardStepPx = 17f,
                downwardStepPx = 17f,
            ),
        )

        assertTrue(release.isAnimating)
        assertEquals(-1700f, release.nextVelocityPxPerSecond, 0.0001f)
        assertNotNull(release.settleTarget)
        assertEquals(1, release.settleTarget?.direction)
        assertEquals(-17f, release.settleTarget?.targetResidualPx ?: 0f, 0.0001f)
        assertEquals(1, release.settleTarget?.completionStepDelta ?: 0)
    }

    @Test
    fun velocityPhaseKeepsDirectionalTargetWhileFlingIsActive() {
        val release = DrawerVerticalScrollController.release(
            residualOffsetPx = -4f,
            velocityPxPerSecond = -1700f,
            thresholds = DrawerVerticalScrollThresholds(
                upwardStepPx = 17f,
                downwardStepPx = 17f,
            ),
        )

        val step = DrawerVerticalScrollController.stepAnimation(
            residualOffsetPx = release.residualOffsetPx,
            velocityPxPerSecond = release.nextVelocityPxPerSecond,
            thresholds = DrawerVerticalScrollThresholds(
                upwardStepPx = 17f,
                downwardStepPx = 17f,
            ),
            deltaMs = 16L,
            settleTarget = release.settleTarget,
        )

        assertTrue(step.stepDelta > 0)
        assertTrue(step.nextVelocityPxPerSecond < 0f)
        assertTrue(step.isAnimating)
        assertNotNull(step.settleTarget)
        assertEquals(1, step.settleTarget?.direction)
    }

    @Test
    fun noVelocityAndNoResidualEndsImmediately() {
        val release = DrawerVerticalScrollController.release(
            residualOffsetPx = 0f,
            velocityPxPerSecond = 0f,
            thresholds = DrawerVerticalScrollThresholds(
                upwardStepPx = 17f,
                downwardStepPx = 17f,
            ),
        )

        assertFalse(release.isAnimating)
        assertNull(release.settleTarget)
        assertEquals(0f, release.residualOffsetPx, 0.0001f)
        assertEquals(0f, release.nextVelocityPxPerSecond, 0.0001f)
    }
}
