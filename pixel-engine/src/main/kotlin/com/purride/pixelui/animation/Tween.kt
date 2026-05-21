package com.purride.pixelui.animation

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.EdgeInsets
import kotlin.math.roundToInt

public data class IntOffset(val x: Int, val y: Int)

public abstract class Tween<T>(
    public val begin: T,
    public val end: T,
) {
    public abstract fun lerp(t: Float): T

    public fun evaluate(animation: Animation<Float>): T = lerp(animation.value)
}

private fun lerpInt(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).roundToInt()

public class IntTween(begin: Int, end: Int) : Tween<Int>(begin, end) {
    override fun lerp(t: Float): Int = lerpInt(begin, end, t)
}

public class EdgeInsetsTween(begin: EdgeInsets, end: EdgeInsets) : Tween<EdgeInsets>(begin, end) {
    override fun lerp(t: Float): EdgeInsets = EdgeInsets(
        left = lerpInt(begin.left, end.left, t),
        top = lerpInt(begin.top, end.top, t),
        right = lerpInt(begin.right, end.right, t),
        bottom = lerpInt(begin.bottom, end.bottom, t),
    )
}

public class OffsetTween(begin: IntOffset, end: IntOffset) : Tween<IntOffset>(begin, end) {
    override fun lerp(t: Float): IntOffset = IntOffset(
        x = lerpInt(begin.x, end.x, t),
        y = lerpInt(begin.y, end.y, t),
    )
}

public class PixelColorTween(begin: PixelColor, end: PixelColor) : Tween<PixelColor>(begin, end) {
    override fun lerp(t: Float): PixelColor = PixelColor.fromArgb(
        a = lerpInt(begin.alpha, end.alpha, t),
        r = lerpInt(begin.red, end.red, t),
        g = lerpInt(begin.green, end.green, t),
        b = lerpInt(begin.blue, end.blue, t),
    )
}
