package com.purride.pixellauncherv2.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherAnimationStateTest {

    @Test
    fun bootSequenceStartsAtScanStage() {
        val animationState = LauncherAnimationState().startBootSequence()

        assertTrue(animationState.hasActiveAnimations)
        assertEquals(BootSequenceStage.SCAN, animationState.bootSequence?.stage)
    }

    @Test
    fun bootSequenceCompletesAfterConfiguredFrames() {
        var animationState = LauncherAnimationState().startBootSequence()
        repeat(BootSequenceAnimation.totalFrames) {
            animationState = animationState.nextFrame()
        }

        assertFalse(animationState.hasActiveAnimations)
        assertNull(animationState.bootSequence)
    }

    @Test
    fun drawerRevealExposesMoreRowsPerFrame() {
        val animation = LauncherAnimationState().startDrawerReveal().drawerReveal

        assertNotNull(animation)
        assertEquals(1f / DrawerRevealAnimation.totalFrames.toFloat(), animation?.revealProgress)
    }

    @Test
    fun launchShutterDurationMatchesFrameCount() {
        assertEquals(
            LauncherAnimationState.frameDelayMs * LaunchShutterAnimation.totalFrames,
            LauncherAnimationState.launchShutterDurationMs,
        )
    }

    @Test
    fun hasActiveAnimationsIsFalseWhenOnlyHeaderTickAdvances() {
        val nextFrame = LauncherAnimationState().nextFrame()

        assertFalse(nextFrame.hasActiveAnimations)
    }

    @Test
    fun headerChargeTickAdvancesEveryFrame() {
        val nextFrame = LauncherAnimationState().nextFrame()

        assertEquals(1, nextFrame.headerChargeTick)
    }
}
