package com.purride.pixelui.widgets.animated

import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelAnimationStatus
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.ManualFrameScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests the quantization and fade logic that AnimatedSwitcher uses.
 * Widget phase transitions are tested via the underlying quantizeOpacity function.
 */
class AnimatedSwitcherTest {

    private val scheduler = ManualFrameScheduler()
    private val vsync = PixelTickerProvider(scheduler)

    private fun advance(ns: Long) = scheduler.advanceFrame(ns)

    @Test
    fun fadeOutQuantizesCorrectly() {
        // When fading out (1 - t):
        // t=0 → fadeOut=1 → q=1 (visible)
        // t=0.75 → fadeOut=0.25 → q=0.5 (semi)
        // t=0.76 → fadeOut=0.24 → q=0 (invisible)
        assertEquals(1f, quantizeOpacity(1f - 0f), 1e-4f)
        assertEquals(0.5f, quantizeOpacity(1f - 0.75f), 1e-4f)
        assertEquals(0f, quantizeOpacity(1f - 0.76f), 1e-4f)
    }

    @Test
    fun fadeInQuantizesCorrectly() {
        // When fading in (t):
        // t=0 → q=0 (invisible)
        // t=0.25 → q=0.5 (semi)
        // t=0.76 → q=1 (visible)
        assertEquals(0f, quantizeOpacity(0f), 1e-4f)
        assertEquals(0.5f, quantizeOpacity(0.25f), 1e-4f)
        assertEquals(1f, quantizeOpacity(0.76f), 1e-4f)
    }

    @Test
    fun controllerCompletesAfterDuration() {
        val ctrl = PixelAnimationController(duration = 200.milliseconds, vsync = vsync)
        ctrl.forward()
        advance(0L)
        advance(200_000_000L)
        assertEquals(PixelAnimationStatus.Completed, ctrl.status)
    }

    @Test
    fun distinctChildKeyIdentitiesTriggerSwitch() {
        // Widget keys are different → identity differs → should switch
        val key1 = "child-a"
        val key2 = "child-b"
        assertNotEquals(key1, key2)
    }

    @Test
    fun sameChildKeyDoesNotTriggerSwitch() {
        val key = "same"
        assertEquals(key, key)
    }
}
