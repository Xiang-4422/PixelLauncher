package com.purride.pixelui.animation

import com.purride.pixelui.host.ManualFrameScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class CurvedAnimationTest {

    private val scheduler = ManualFrameScheduler()
    private val provider = PixelTickerProvider(scheduler)

    private fun makeController(initial: Float = 0f) =
        PixelAnimationController(duration = 1.seconds, vsync = provider, initialValue = initial)

    @Test
    fun easeInCurveSquaresParentValue() {
        val ctrl = makeController()
        val curved = CurvedAnimation(parent = ctrl, curve = Curves.EaseIn)
        ctrl.forward()
        scheduler.advanceFrame(0L)
        scheduler.advanceFrame(500_000_000L)
        val parentVal = ctrl.value
        assertEquals(parentVal * parentVal, curved.value, 1e-3f)
    }

    @Test
    fun reverseCurveUsedInReverseStatus() {
        val ctrl = makeController(initial = 1f)
        val curved = CurvedAnimation(
            parent = ctrl,
            curve = Curves.EaseIn,
            reverseCurve = Curves.Linear,
        )
        ctrl.reverse()
        scheduler.advanceFrame(0L)
        scheduler.advanceFrame(500_000_000L)
        val parentVal = ctrl.value
        assertEquals(parentVal, curved.value, 1e-3f)
    }

    @Test
    fun fallsBackToCurveWhenNoReverseCurve() {
        val ctrl = makeController(initial = 1f)
        val curved = CurvedAnimation(parent = ctrl, curve = Curves.EaseIn)
        ctrl.reverse()
        scheduler.advanceFrame(0L)
        scheduler.advanceFrame(500_000_000L)
        val parentVal = ctrl.value
        assertEquals(parentVal * parentVal, curved.value, 1e-3f)
    }

    @Test
    fun listenersTransparentlyProxiedToParent() {
        val ctrl = makeController()
        val curved = CurvedAnimation(parent = ctrl, curve = Curves.Linear)
        var listenerCount = 0
        curved.addListener { listenerCount++ }
        ctrl.forward()
        listenerCount = 0
        scheduler.advanceFrame(0L)
        scheduler.advanceFrame(200_000_000L)
        assert(listenerCount >= 2) { "Expected listener calls, got $listenerCount" }
    }

    @Test
    fun statusMirrorsParent() {
        val ctrl = makeController()
        val curved = CurvedAnimation(parent = ctrl, curve = Curves.Linear)
        assertEquals(ctrl.status, curved.status)
        ctrl.forward()
        assertEquals(PixelAnimationStatus.Forward, curved.status)
    }
}
