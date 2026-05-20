package com.purride.pixelui.widgets.animated

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.animation.EdgeInsetsTween
import com.purride.pixelui.animation.IntTween
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelAnimationStatus
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.animation.PixelToneTween
import com.purride.pixelui.host.ManualFrameScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests AnimatedContainer's per-property tween logic.
 * Exercises the underlying tween types since the widget state is private.
 */
class AnimatedContainerTest {

    private val scheduler = ManualFrameScheduler()
    private val vsync = PixelTickerProvider(scheduler)

    private fun advance(ns: Long) = scheduler.advanceFrame(ns)

    @Test
    fun widthTweenReachesTargetAfterDuration() {
        val ctrl = PixelAnimationController(duration = 200.milliseconds, vsync = vsync)
        val tween = IntTween(begin = 50, end = 100)
        ctrl.forward()
        advance(0L)
        advance(200_000_000L)
        assertEquals(PixelAnimationStatus.Completed, ctrl.status)
        assertEquals(100, tween.lerp(ctrl.value))
    }

    @Test
    fun borderToneSwitchesAtHalfway() {
        val tween = PixelToneTween(begin = PixelTone.ON, end = PixelTone.ACCENT)
        assertEquals(PixelTone.ON, tween.lerp(0.49f))
        assertEquals(PixelTone.ACCENT, tween.lerp(0.5f))
    }

    @Test
    fun multiplePropertiesWithOneController() {
        val ctrl = PixelAnimationController(duration = 300.milliseconds, vsync = vsync)
        val widthTween = IntTween(begin = 0, end = 100)
        val toneTween = PixelToneTween(begin = PixelTone.OFF, end = PixelTone.ON)
        ctrl.forward()
        advance(0L)
        advance(300_000_000L)
        // Both tweens at t=1
        assertEquals(100, widthTween.lerp(ctrl.value))
        assertEquals(PixelTone.ON, toneTween.lerp(ctrl.value))
    }

    @Test
    fun unchangedPropertiesUseInitialValue() {
        val ctrl = PixelAnimationController(duration = 200.milliseconds, vsync = vsync)
        val tween = IntTween(begin = 50, end = 50)
        ctrl.forward()
        advance(0L)
        advance(200_000_000L)
        assertEquals(50, tween.lerp(ctrl.value))
    }

    @Test
    fun paddingTweenInterpolatesAllSides() {
        val begin = EdgeInsets(left = 0, top = 0, right = 0, bottom = 0)
        val end = EdgeInsets(left = 8, top = 4, right = 8, bottom = 4)
        val tween = EdgeInsetsTween(begin, end)
        val ctrl = PixelAnimationController(duration = 200.milliseconds, vsync = vsync)
        ctrl.forward()
        advance(0L)
        advance(200_000_000L)
        val result = tween.lerp(ctrl.value)
        assertEquals(end, result)
    }
}
