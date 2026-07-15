package com.purride.pixelui.animation

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.PixelGradient
import com.purride.pixelui.PixelGradientStop
import com.purride.pixelui.PixelPoint
import kotlin.math.roundToInt

/** 表示 `Tween` 使用的不可变几何数据，并以逻辑像素参与布局或绘制。 */
public data class IntOffset(val x: Int, val y: Int)

/** 定义 `Tween` 的确定性插值过程；相同进度和端点必须得到相同结果。 */
public abstract class Tween<T>(
    begin: T,
    end: T,
) {
    /** 公开 `Tween` 的 `begin` 配置或运行值。
 *
 * Current segment start; implicit-animation widgets may rebase it to a rendered value.
 */
    public var begin: T = begin

    /** 公开 `Tween` 的 `end` 配置或运行值。
 *
 * Current segment target supplied by the animation consumer.
 */
    public var end: T = end

    /** 执行 `Tween` 的 `lerp` 公开行为；具体参数、返回和副作用见下文。
 *
 * Interpolates this segment at normalized progress [t].
 */
    public abstract fun lerp(t: Float): T

    /** 执行 `Tween` 的 `evaluate` 公开行为；具体参数、返回和副作用见下文。
 *
 * Evaluates this segment with the normalized value exposed by [animation].
 */
    public fun evaluate(animation: Animation<Float>): T = lerp(animation.value)
}

private fun lerpInt(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).roundToInt()

/** 定义 `Tween` 的确定性插值过程；相同进度和端点必须得到相同结果。 */
public class IntTween(begin: Int, end: Int) : Tween<Int>(begin, end) {
    override fun lerp(t: Float): Int = lerpInt(begin, end, t)
}

/** 定义 `Tween` 的确定性插值过程；相同进度和端点必须得到相同结果。 */
public class EdgeInsetsTween(begin: EdgeInsets, end: EdgeInsets) : Tween<EdgeInsets>(begin, end) {
    override fun lerp(t: Float): EdgeInsets = EdgeInsets(
        left = lerpInt(begin.left, end.left, t),
        top = lerpInt(begin.top, end.top, t),
        right = lerpInt(begin.right, end.right, t),
        bottom = lerpInt(begin.bottom, end.bottom, t),
    )
}

/** 定义 `Tween` 的确定性插值过程；相同进度和端点必须得到相同结果。 */
public class OffsetTween(begin: IntOffset, end: IntOffset) : Tween<IntOffset>(begin, end) {
    override fun lerp(t: Float): IntOffset = IntOffset(
        x = lerpInt(begin.x, end.x, t),
        y = lerpInt(begin.y, end.y, t),
    )
}

/** 定义 `Tween` 的确定性插值过程；相同进度和端点必须得到相同结果。 */
public class PixelColorTween(begin: PixelColor, end: PixelColor) : Tween<PixelColor>(begin, end) {
    override fun lerp(t: Float): PixelColor = PixelColor.fromArgb(
        a = lerpInt(begin.alpha, end.alpha, t),
        r = lerpInt(begin.red, end.red, t),
        g = lerpInt(begin.green, end.green, t),
        b = lerpInt(begin.blue, end.blue, t),
    )
}

/** 定义 `Tween` 的确定性插值过程；相同进度和端点必须得到相同结果。 */
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
