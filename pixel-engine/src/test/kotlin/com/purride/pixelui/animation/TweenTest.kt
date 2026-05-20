package com.purride.pixelui.animation

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelTone
import com.purride.pixelui.EdgeInsets
import org.junit.Assert.assertEquals
import org.junit.Test

class TweenTest {

    @Test
    fun intTweenBoundaries() {
        val tween = IntTween(0, 10)
        assertEquals(0, tween.lerp(0f))
        assertEquals(10, tween.lerp(1f))
    }

    @Test
    fun intTweenMidpoint() {
        val tween = IntTween(0, 10)
        assertEquals(5, tween.lerp(0.5f))
    }

    @Test
    fun intTweenRoundsNotFloors() {
        val tween = IntTween(0, 10)
        // 0.49 * 10 = 4.9 → rounds to 5, not floor to 4
        assertEquals(5, tween.lerp(0.49f))
        // 0.44 * 10 = 4.4 → rounds to 4
        assertEquals(4, tween.lerp(0.44f))
    }

    @Test
    fun edgeInsetsTweenAllSides() {
        val tween = EdgeInsetsTween(
            EdgeInsets(left = 0, top = 0, right = 0, bottom = 0),
            EdgeInsets(left = 10, top = 20, right = 30, bottom = 40),
        )
        val result = tween.lerp(0.5f)
        assertEquals(5, result.left)
        assertEquals(10, result.top)
        assertEquals(15, result.right)
        assertEquals(20, result.bottom)
    }

    @Test
    fun edgeInsetsTweenBoundaries() {
        val begin = EdgeInsets(left = 2, top = 4, right = 6, bottom = 8)
        val end = EdgeInsets(left = 10, top = 20, right = 30, bottom = 40)
        val tween = EdgeInsetsTween(begin, end)
        assertEquals(begin, tween.lerp(0f))
        assertEquals(end, tween.lerp(1f))
    }

    @Test
    fun offsetTweenInterpolates() {
        val tween = OffsetTween(IntOffset(0, 0), IntOffset(100, 200))
        val result = tween.lerp(0.5f)
        assertEquals(50, result.x)
        assertEquals(100, result.y)
    }

    @Test
    fun offsetTweenRounds() {
        val tween = OffsetTween(IntOffset(0, 0), IntOffset(10, 10))
        // 0.49 → 4.9 → rounds to 5
        val result = tween.lerp(0.49f)
        assertEquals(5, result.x)
        assertEquals(5, result.y)
    }

    @Test
    fun pixelToneTweenBelowHalf() {
        val tween = PixelToneTween(PixelTone.ON, PixelTone.ACCENT)
        assertEquals(PixelTone.ON, tween.lerp(0f))
        assertEquals(PixelTone.ON, tween.lerp(0.49f))
    }

    @Test
    fun pixelToneTweenAtAndAboveHalf() {
        val tween = PixelToneTween(PixelTone.ON, PixelTone.ACCENT)
        assertEquals(PixelTone.ACCENT, tween.lerp(0.5f))
        assertEquals(PixelTone.ACCENT, tween.lerp(1f))
    }

    @Test
    fun pixelToneTweenNoThirdTone() {
        val tween = PixelToneTween(PixelTone.OFF, PixelTone.ACCENT)
        val values = listOf(0f, 0.1f, 0.25f, 0.49f, 0.5f, 0.75f, 1f).map { tween.lerp(it) }
        assertTrue(values.none { it == PixelTone.ON })
    }

    @Test
    fun pixelColorTweenBoundaries() {
        val black = PixelColor.fromRgb(0, 0, 0)
        val white = PixelColor.fromRgb(255, 255, 255)
        val tween = PixelColorTween(black, white)
        assertEquals(black, tween.lerp(0f))
        assertEquals(white, tween.lerp(1f))
    }

    @Test
    fun pixelColorTweenMidpoint() {
        val black = PixelColor.fromRgb(0, 0, 0)
        val white = PixelColor.fromRgb(255, 255, 255)
        val tween = PixelColorTween(black, white)
        val mid = tween.lerp(0.5f)
        assertEquals(128, mid.red)
        assertEquals(128, mid.green)
        assertEquals(128, mid.blue)
        assertEquals(255, mid.alpha)
    }

    @Test
    fun pixelColorTweenAlphaChannel() {
        val transparent = PixelColor.fromArgb(0, 255, 0, 0)
        val opaque = PixelColor.fromArgb(255, 255, 0, 0)
        val tween = PixelColorTween(transparent, opaque)
        assertEquals(128, tween.lerp(0.5f).alpha)
    }

    private fun assertTrue(b: Boolean) = org.junit.Assert.assertTrue(b)
}
