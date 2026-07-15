package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelBlendMode
import com.purride.pixelui.advanced.PixelExperimentalApi
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.internal.PaintContext
import com.purride.pixelui.internal.PixelPolygonRasterizer
import com.purride.pixelui.internal.drawLinePixels
import com.purride.pixelui.internal.paintStrokePoint
import com.purride.pixelui.internal.visitPixelPathSegments
import kotlin.math.sqrt

/**
 * 定义 `PixelCanvas` 在 `PixelCanvas` 中承担的数据与行为边界。
 *
 * Stable drawing facade supplied to a `CustomPaint` callback.
 *
 * @property context Stable paint capability sharing the current frame buffer and pool.
 * @property offsetX Absolute horizontal origin of this local canvas.
 * @property offsetY Absolute vertical origin of this local canvas.
 * @property width Width of the local drawing surface in logical pixels.
 * @property height Height of the local drawing surface in logical pixels.
 */
@OptIn(PixelExperimentalApi::class)
public class PixelCanvas internal constructor(
    private val context: PixelPaintContext,
    private val offsetX: Int,
    private val offsetY: Int,
    public val width: Int,
    public val height: Int,
) {
    /** Internal helper context used only by legacy rasterization functions behind this facade. */
    private val internalContext = PaintContext(
        buffer = context.buffer,
        bufferPool = context.bufferPool,
    )

    /** Reused scan converter that avoids allocating a rasterizer for every polygon call. */
    private val polygonRasterizer = PixelPolygonRasterizer()

    /** 更新 `PixelCanvas` 的 `setPixel` 状态并保持派生数据一致。
 *
 * Writes one local pixel after applying this canvas's retained-tree offset.
 */
    public fun setPixel(
        x: Int,
        y: Int,
        color: PixelColor,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        context.buffer.setPixel(offsetX + x, offsetY + y, color, blendMode)
    }

    /** 执行 `PixelCanvas` 的 `drawLine` 渲染或命中阶段。
 *
 * Draws a line with explicit color, stroke width, and blend mode.
 */
    public fun drawLine(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        color: PixelColor,
        strokeWidth: Int = 1,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        drawLinePixels(internalContext, offsetX, offsetY, startX, startY, endX, endY, color, strokeWidth, blendMode)
    }

    /** 执行 `PixelCanvas` 的 `drawLine` 渲染或命中阶段。
 *
 * Draws a line using the shared shape style contract.
 */
    public fun drawLine(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        style: PixelShapeStyle,
    ) {
        drawLine(startX, startY, endX, endY, style.color, style.strokeWidth, style.blendMode)
    }

    /** 执行 `PixelCanvas` 的 `drawRect` 渲染或命中阶段。
 *
 * Draws a rectangular outline, expanding thick strokes through four line segments.
 */
    public fun drawRect(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        color: PixelColor,
        strokeWidth: Int = 1,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        if (strokeWidth <= 1) {
            context.buffer.drawRect(offsetX + left, offsetY + top, width, height, color, blendMode)
            return
        }
        if (width <= 0 || height <= 0) return
        /** Inclusive right edge used by the line rasterizer. */
        val right = left + width - 1
        /** Inclusive bottom edge used by the line rasterizer. */
        val bottom = top + height - 1
        drawLine(left, top, right, top, color, strokeWidth, blendMode)
        drawLine(right, top, right, bottom, color, strokeWidth, blendMode)
        drawLine(right, bottom, left, bottom, color, strokeWidth, blendMode)
        drawLine(left, bottom, left, top, color, strokeWidth, blendMode)
    }

    /** 执行 `PixelCanvas` 的 `fillRect` 公开行为；具体参数、返回和副作用见下文。
 *
 * Fills a local rectangular region with one color and blend mode.
 */
    public fun fillRect(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        color: PixelColor,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        context.buffer.fillRect(offsetX + left, offsetY + top, width, height, color, blendMode)
    }

    /** 执行 `PixelCanvas` 的 `fillGradientRect` 公开行为；具体参数、返回和副作用见下文。
 *
 * Fills a local rectangle by sampling [gradient] at every logical pixel.
 */
    public fun fillGradientRect(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        gradient: PixelGradient,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        if (width <= 0 || height <= 0) return
        for (y in top until top + height) {
            for (x in left until left + width) {
                setPixel(x, y, gradient.colorAt(x, y), blendMode)
            }
        }
    }

    /** 执行 `PixelCanvas` 的 `drawCircle` 渲染或命中阶段。
 *
 * Draws a filled or outlined circle with explicit paint values.
 */
    public fun drawCircle(
        centerX: Int,
        centerY: Int,
        radius: Int,
        color: PixelColor,
        filled: Boolean = true,
        strokeWidth: Int = 1,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        if (radius < 0) return
        if (filled) {
            /** Squared radius used to avoid a square root for rejected scan-line points. */
            val r2 = radius * radius
            for (dy in -radius..radius) {
                /** Remaining squared horizontal radius for the current scan line. */
                val dx2 = r2 - dy * dy
                if (dx2 < 0) continue
                /** Symmetric horizontal extent of the current filled scan line. */
                val dx = sqrt(dx2.toDouble()).toInt()
                for (x in -dx..dx) {
                    setPixel(centerX + x, centerY + dy, color, blendMode)
                }
            }
            return
        }
        /** Current horizontal coordinate in the midpoint-circle octant. */
        var x = radius
        /** Current vertical coordinate in the midpoint-circle octant. */
        var y = 0
        /** Midpoint error accumulator deciding when the horizontal coordinate decreases. */
        var err = 0
        while (x >= y) {
            /** Radius used to expand each outline sample for thick strokes. */
            val strokeRadius = strokeWidth.coerceAtLeast(1) / 2
            paintStrokePoint(internalContext, offsetX, offsetY, centerX + x, centerY + y, strokeRadius, color, blendMode)
            paintStrokePoint(internalContext, offsetX, offsetY, centerX + y, centerY + x, strokeRadius, color, blendMode)
            paintStrokePoint(internalContext, offsetX, offsetY, centerX - y, centerY + x, strokeRadius, color, blendMode)
            paintStrokePoint(internalContext, offsetX, offsetY, centerX - x, centerY + y, strokeRadius, color, blendMode)
            paintStrokePoint(internalContext, offsetX, offsetY, centerX - x, centerY - y, strokeRadius, color, blendMode)
            paintStrokePoint(internalContext, offsetX, offsetY, centerX - y, centerY - x, strokeRadius, color, blendMode)
            paintStrokePoint(internalContext, offsetX, offsetY, centerX + y, centerY - x, strokeRadius, color, blendMode)
            paintStrokePoint(internalContext, offsetX, offsetY, centerX + x, centerY - y, strokeRadius, color, blendMode)
            y += 1
            if (err <= 0) {
                err += 2 * y + 1
            } else {
                x -= 1
                err += 2 * (y - x) + 1
            }
        }
    }

    /** 执行 `PixelCanvas` 的 `drawCircle` 渲染或命中阶段。
 *
 * Draws a circle using the shared shape style contract.
 */
    public fun drawCircle(
        centerX: Int,
        centerY: Int,
        radius: Int,
        style: PixelShapeStyle,
    ) {
        drawCircle(centerX, centerY, radius, style.color, style.filled, style.strokeWidth, style.blendMode)
    }

    /** 执行 `PixelCanvas` 的 `drawPolygon` 渲染或命中阶段。
 *
 * Draws or fills a polygon with explicit color and blend mode.
 */
    public fun drawPolygon(
        points: List<PixelPoint>,
        color: PixelColor,
        filled: Boolean = true,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        polygonRasterizer.drawPolygon(
            context = internalContext,
            offsetX = offsetX,
            offsetY = offsetY,
            width = width,
            height = height,
            points = points,
            color = color,
            filled = filled,
            strokeWidth = 1,
            blendMode = blendMode,
        )
    }

    /** 执行 `PixelCanvas` 的 `drawPolygon` 渲染或命中阶段。
 *
 * Draws or fills a polygon using the shared shape style contract.
 */
    public fun drawPolygon(
        points: List<PixelPoint>,
        style: PixelShapeStyle,
    ) {
        polygonRasterizer.drawPolygon(
            context = internalContext,
            offsetX = offsetX,
            offsetY = offsetY,
            width = width,
            height = height,
            points = points,
            color = style.color,
            filled = style.filled,
            strokeWidth = style.strokeWidth,
            blendMode = style.blendMode,
        )
    }

    /** 执行 `PixelCanvas` 的 `drawPath` 渲染或命中阶段。
 *
 * Rasterizes every segment in [path] with explicit stroke values.
 */
    public fun drawPath(
        path: PixelPath,
        color: PixelColor,
        closed: Boolean = false,
        strokeWidth: Int = 1,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        visitPixelPathSegments(path, closed) { startX, startY, endX, endY ->
            drawLine(startX, startY, endX, endY, color, strokeWidth, blendMode)
        }
    }

    /** 执行 `PixelCanvas` 的 `drawPath` 渲染或命中阶段。
 *
 * Rasterizes [path] using the shared shape style contract.
 */
    public fun drawPath(
        path: PixelPath,
        style: PixelShapeStyle,
        closed: Boolean = false,
    ) {
        drawPath(path, style.color, closed, style.strokeWidth, style.blendMode)
    }
}

/** Samples this gradient at one local logical coordinate. */
private fun PixelGradient.colorAt(x: Int, y: Int): PixelColor {
    return when (this) {
        is PixelGradient.Linear -> colorAt(linearOffset(x, y), sortedStops)
        is PixelGradient.Radial -> {
            /** Normalized radial distance from the configured center. */
            val offset = if (radius == 0) {
                1f
            } else {
                /** Horizontal distance from the radial center. */
                val dx = x - center.x
                /** Vertical distance from the radial center. */
                val dy = y - center.y
                (sqrt((dx * dx + dy * dy).toDouble()) / radius.toDouble()).toFloat()
            }
            colorAt(offset, sortedStops)
        }
    }
}

/** Projects one coordinate onto this linear gradient's normalized direction vector. */
private fun PixelGradient.Linear.linearOffset(x: Int, y: Int): Float {
    /** Horizontal direction component of the gradient vector. */
    val dx = end.x - start.x
    /** Vertical direction component of the gradient vector. */
    val dy = end.y - start.y
    /** Squared vector length used for normalized projection. */
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0) return 1f
    /** Horizontal coordinate relative to the gradient start. */
    val px = x - start.x
    /** Vertical coordinate relative to the gradient start. */
    val py = y - start.y
    return ((px * dx + py * dy).toFloat() / lengthSquared.toFloat()).coerceIn(0f, 1f)
}

/** Interpolates a sorted gradient stop list at one normalized offset. */
private fun colorAt(offset: Float, stops: List<PixelGradientStop>): PixelColor {
    /** Clamped sampling position that keeps malformed callers inside the stop range. */
    val t = offset.coerceIn(0f, 1f)
    /** Stop immediately preceding the current search position. */
    var previous = stops.first()
    if (t <= previous.offset) return previous.color
    for (index in 1 until stops.size) {
        /** Candidate stop immediately following [previous]. */
        val next = stops[index]
        if (t <= next.offset) {
            /** Distance between the surrounding stop offsets. */
            val span = next.offset - previous.offset
            /** Sampling position normalized within the surrounding stop interval. */
            val localT = if (span <= 0f) 1f else (t - previous.offset) / span
            return lerpColor(previous.color, next.color, localT)
        }
        previous = next
    }
    return stops.last().color
}

/** Interpolates all ARGB channels between two colors. */
private fun lerpColor(start: PixelColor, end: PixelColor, t: Float): PixelColor {
    /** Interpolates and clamps one eight-bit color channel. */
    fun lerpChannel(a: Int, b: Int): Int {
        return (a + ((b - a) * t)).toInt().coerceIn(0, 255)
    }
    return PixelColor.fromArgb(
        a = lerpChannel(start.alpha, end.alpha),
        r = lerpChannel(start.red, end.red),
        g = lerpChannel(start.green, end.green),
        b = lerpChannel(start.blue, end.blue),
    )
}
