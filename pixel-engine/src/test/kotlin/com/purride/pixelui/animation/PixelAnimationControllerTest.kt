package com.purride.pixelui.animation

import com.purride.pixelui.host.ManualFrameScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class PixelAnimationControllerTest {

    private val scheduler = ManualFrameScheduler()
    private val provider = PixelTickerProvider(scheduler)
    private val duration = 1_000.milliseconds

    private fun makeController(initial: Float = 0f) =
        PixelAnimationController(duration = duration, vsync = provider, initialValue = initial)

    private fun advanceMs(ms: Long) = scheduler.advanceFrame(ms * 1_000_000L)

    @Test
    fun defaultStatusAndValue() {
        val ctrl = makeController()
        assertEquals(PixelAnimationStatus.Dismissed, ctrl.status)
        assertEquals(0f, ctrl.value, 1e-4f)
        assertFalse(ctrl.isAnimating)
    }

    @Test
    fun forwardSetsStatusForward() {
        val ctrl = makeController()
        ctrl.forward()
        assertEquals(PixelAnimationStatus.Forward, ctrl.status)
        assertTrue(ctrl.isAnimating)
    }

    @Test
    fun forwardCompletesAtEnd() {
        val ctrl = makeController()
        ctrl.forward()
        advanceMs(0)
        advanceMs(1_000)
        assertEquals(PixelAnimationStatus.Completed, ctrl.status)
        assertEquals(1f, ctrl.value, 1e-4f)
        assertFalse(ctrl.isAnimating)
    }

    @Test
    fun reverseSetStatusReverse() {
        val ctrl = makeController(initial = 1f)
        ctrl.reverse()
        assertEquals(PixelAnimationStatus.Reverse, ctrl.status)
        assertTrue(ctrl.isAnimating)
    }

    @Test
    fun reverseDismissesAtZero() {
        val ctrl = makeController()
        ctrl.reverse()
        advanceMs(0)
        advanceMs(1_000)
        assertEquals(PixelAnimationStatus.Dismissed, ctrl.status)
        assertEquals(0f, ctrl.value, 1e-4f)
    }

    @Test
    fun valueAtMidpoint() {
        val ctrl = makeController()
        ctrl.forward()
        advanceMs(0)
        advanceMs(500)
        assertEquals(0.5f, ctrl.value, 0.01f)
    }

    @Test
    fun repeatForwardLoopsBackToZero() {
        val ctrl = makeController()
        ctrl.repeat(reverse = false)
        advanceMs(0)
        advanceMs(1_000)
        // should have restarted
        assertEquals(PixelAnimationStatus.Forward, ctrl.status)
        advanceMs(0)
        assertEquals(0f, ctrl.value, 0.01f)
    }

    @Test
    fun repeatPingPongReversesAtEnd() {
        val ctrl = makeController()
        ctrl.repeat(reverse = true)
        advanceMs(0)
        advanceMs(1_000)
        assertEquals(PixelAnimationStatus.Reverse, ctrl.status)
    }

    @Test
    fun stopDoesNotChangeStatus() {
        val ctrl = makeController()
        ctrl.forward()
        advanceMs(0)
        advanceMs(500)
        val valueAtStop = ctrl.value
        ctrl.stop()
        assertEquals(PixelAnimationStatus.Forward, ctrl.status)
        scheduler.advanceFrame(900_000_000L)
        assertEquals(valueAtStop, ctrl.value, 1e-4f)
    }

    @Test
    fun setValueStopsAnimation() {
        val ctrl = makeController()
        ctrl.forward()
        advanceMs(200)
        ctrl.setValue(0.8f)
        assertEquals(0.8f, ctrl.value, 1e-4f)
        assertFalse(ctrl.isAnimating)
    }

    @Test
    fun resetGoesToZeroDismissed() {
        val ctrl = makeController()
        ctrl.forward()
        advanceMs(600)
        ctrl.reset()
        assertEquals(0f, ctrl.value, 1e-4f)
        assertEquals(PixelAnimationStatus.Dismissed, ctrl.status)
    }

    @Test
    fun listenersNotifiedOnValueChange() {
        val ctrl = makeController()
        var notifyCount = 0
        ctrl.addListener { notifyCount++ }
        ctrl.forward()
        notifyCount = 0
        advanceMs(0)
        advanceMs(200)
        assertTrue(notifyCount >= 2)
    }

    @Test
    fun disposeStopsTicker() {
        val ctrl = makeController()
        ctrl.forward()
        advanceMs(0)
        ctrl.dispose()
        // Drain any lingering no-op frame; should not reschedule afterward
        advanceMs(1_000)
        assertEquals(0, scheduler.pendingCount)
    }
}
