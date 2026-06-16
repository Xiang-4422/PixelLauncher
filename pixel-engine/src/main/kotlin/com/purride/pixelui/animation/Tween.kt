package com.purride.pixelui.animation

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.PixelGradient
import com.purride.pixelui.PixelGradientStop
import com.purride.pixelui.PixelPoint
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

public class PixelGradientTween(begin: PixelGradient, end: PixelGradient) : Tween<PixelGradient>(begin, end) {
    init {
        require(begin::class == end::class) {
            "PixelGradientTween requires matching gradient types but was ${begin.javaClass.simpleName} -> ${end.javaClass.simpleName}"
        }
        require(begin.stops.size == end.stops.size) {
            "PixelGradientTween requires matching stop counts but was ${begin.stops.size} -> ${end.stops.size}"
        }
    }

    override fun lerp(t: Float): PixelGradient {
        return when (val b = begin) {
            is PixelGradient.Linear -> {
                val e = end as PixelGradient.Linear
                PixelGradient.Linear(
                    start = lerpPoint(b.start, e.start, t),
                    end = lerpPoint(b.end, e.end, t),
                    stops = lerpStops(b.stops, e.stops, t),
                )
            }
            is PixelGradient.Radial -> {
                val e = end as PixelGradient.Radial
                PixelGradient.Radial(
                    center = lerpPoint(b.center, e.center, t),
                    radius = lerpInt(b.radius, e.radius, t),
                    stops = lerpStops(b.stops, e.stops, t),
                )
            }
        }
    }

    private fun lerpPoint(begin: PixelPoint, end: PixelPoint, t: Float): PixelPoint {
        return PixelPoint(
            x = lerpInt(begin.x, end.x, t),
            y = lerpInt(begin.y, end.y, t),
        )
    }

    private fun lerpStops(begin: List<PixelGradientStop>, end: List<PixelGradientStop>, t: Float): List<PixelGradientStop> {
        return begin.indices.map { index ->
            val b = begin[index]
            val e = end[index]
            PixelGradientStop(
                offset = b.offset + (e.offset - b.offset) * t,
                color = lerpColor(b.color, e.color, t),
            )
        }
    }

    private fun lerpColor(begin: PixelColor, end: PixelColor, t: Float): PixelColor {
        return PixelColor.fromArgb(
            a = lerpInt(begin.alpha, end.alpha, t),
            r = lerpInt(begin.red, end.red, t),
            g = lerpInt(begin.green, end.green, t),
            b = lerpInt(begin.blue, end.blue, t),
        )
    }
}
