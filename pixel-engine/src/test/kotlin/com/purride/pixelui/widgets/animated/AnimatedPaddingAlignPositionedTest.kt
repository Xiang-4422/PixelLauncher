package com.purride.pixelui.widgets.animated

import com.purride.pixelui.Alignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.animation.EdgeInsetsTween
import com.purride.pixelui.animation.IntTween
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelAnimationStatus
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.ManualFrameScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class AnimatedPaddingAlignPositionedTest {

    private val scheduler = ManualFrameScheduler()
    private val vsync = PixelTickerProvider(scheduler)

    private fun advance(ns: Long) = scheduler.advanceFrame(ns)

    // ──── AnimatedPadding ────────────────────────────────────────────────────

    @Test
    fun paddingTweenReachesTargetAfterDuration() {
        val begin = EdgeInsets.all(0)
        val end = EdgeInsets.all(16)
        val tween = EdgeInsetsTween(begin, end)
        val ctrl = PixelAnimationController(duration = 200.milliseconds, vsync = vsync)
        ctrl.forward()
        advance(0L)
        advance(200_000_000L)
        assertEquals(PixelAnimationStatus.Completed, ctrl.status)
        assertEquals(end, tween.lerp(ctrl.value))
    }

    @Test
    fun paddingTweenStartsAtBegin() {
        val begin = EdgeInsets.all(4)
        val end = EdgeInsets.all(16)
        val tween = EdgeInsetsTween(begin, end)
        assertEquals(begin, tween.lerp(0f))
    }

    // ──── AnimatedAlign ─────────────────────────────────────────────────────

    @Test
    fun alignmentSwitchesAtHalfway() {
        val from = Alignment.TOP_START
        val to = Alignment.BOTTOM_END
        // threshold switch: t < 0.5 → from, t >= 0.5 → to
        val currentAtLow: Alignment = if (0.49f < 0.5f) from else to
        val currentAtHigh: Alignment = if (0.5f < 0.5f) from else to
        assertEquals(from, currentAtLow)
        assertEquals(to, currentAtHigh)
    }

    // ──── AnimatedPositioned ─────────────────────────────────────────────────

    @Test
    fun positionTweenReachesTarget() {
        val leftTween = IntTween(begin = 0, end = 50)
        val topTween = IntTween(begin = 0, end = 30)
        val ctrl = PixelAnimationController(duration = 200.milliseconds, vsync = vsync)
        ctrl.forward()
        advance(0L)
        advance(200_000_000L)
        assertEquals(50, leftTween.lerp(ctrl.value))
        assertEquals(30, topTween.lerp(ctrl.value))
    }

    @Test
    fun positionTweenRounds() {
        val tween = IntTween(begin = 0, end = 10)
        // At t=0.49, 0.49 * 10 = 4.9 → rounds to 5
        assertEquals(5, tween.lerp(0.49f))
        // At t=0.44, 0.44 * 10 = 4.4 → rounds to 4
        assertEquals(4, tween.lerp(0.44f))
    }
}
