package com.purride.pixelui.widgets.animated

import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.IntTween
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelAnimationStatus
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.ManualFrameScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests the core animation logic that TweenAnimationBuilder relies on.
 * Uses controller + tween directly since widget state is private.
 */
class TweenAnimationBuilderTest {

    private val scheduler = ManualFrameScheduler()
    private val vsync = PixelTickerProvider(scheduler)

    private fun advance(ns: Long) = scheduler.advanceFrame(ns)

    @Test
    fun tweenBeginsAtStartValue() {
        val tween = IntTween(0, 100)
        assertEquals(0, tween.lerp(0f))
    }

    @Test
    fun tweenEndsAtEndValue() {
        val tween = IntTween(0, 100)
        assertEquals(100, tween.lerp(1f))
    }

    @Test
    fun controllerReachesCompletedAfterDuration() {
        val ctrl = PixelAnimationController(duration = 300.milliseconds, vsync = vsync)
        ctrl.forward()
        advance(0L)
        advance(300_000_000L)
        assertEquals(PixelAnimationStatus.Completed, ctrl.status)
        assertEquals(1f, ctrl.value, 1e-4f)
    }

    @Test
    fun onEndCalledOnceOnCompletion() {
        var callCount = 0
        val ctrl = PixelAnimationController(duration = 100.milliseconds, vsync = vsync)
        ctrl.addListener { if (ctrl.status == PixelAnimationStatus.Completed) callCount++ }
        ctrl.forward()
        advance(0L)
        advance(100_000_000L)
        assertEquals(1, callCount)
    }

    @Test
    fun disposeReleasesController() {
        val ctrl = PixelAnimationController(duration = 100.milliseconds, vsync = vsync)
        ctrl.forward()
        advance(0L)
        ctrl.dispose()
        // Drain any lingering frame and verify no more frames are scheduled
        advance(100_000_000L)
        scheduler.advanceFrame(200_000_000L)
        assertEquals(0, scheduler.pendingCount)
    }

    @Test
    fun linearCurveProducesLinearValues() {
        val tween = IntTween(0, 1000)
        val ctrl = PixelAnimationController(duration = 1000.milliseconds, vsync = vsync)
        ctrl.forward()
        advance(0L)
        advance(500_000_000L)
        val value = tween.lerp(ctrl.value)
        // At t=0.5 with linear curve, value should be ~500
        assertEquals(500, value, 10)
    }

    private fun assertEquals(expected: Int, actual: Int, delta: Int) {
        assertTrue("Expected $expected ± $delta, got $actual", kotlin.math.abs(actual - expected) <= delta)
    }
}
