package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelBlendMode
import com.purride.pixelui.internal.PaintContext
import com.purride.pixelui.internal.PixelPolygonRasterizer
import com.purride.pixelui.internal.drawLinePixels
import com.purride.pixelui.internal.paintStrokePoint
import com.purride.pixelui.internal.visitPixelPathSegments
import kotlin.math.sqrt

public class PixelCanvas internal constructor(
    private val context: PaintContext,
    private val offsetX: Int,
    private val offsetY: Int,
    public val width: Int,
    public val height: Int,
) {
    private val polygonRasterizer = PixelPolygonRasterizer()

    public fun setPixel(
        x: Int,
        y: Int,
        color: PixelColor,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        context.buffer.setPixel(offsetX + x, offsetY + y, color, blendMode)
    }

    public fun drawLine(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        color: PixelColor,
        strokeWidth: Int = 1,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        drawLinePixels(context, offsetX, offsetY, startX, startY, endX, endY, color, strokeWidth, blendMode)
    }

    public fun drawLine(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        style: PixelShapeStyle,
    ) {
        drawLine(startX, startY, endX, endY, style.color, style.strokeWidth, style.blendMode)
    }

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
        val right = left + width - 1
        val bottom = top + height - 1
        drawLine(left, top, right, top, color, strokeWidth, blendMode)
        drawLine(right, top, right, bottom, color, strokeWidth, blendMode)
        drawLine(right, bottom, left, bottom, color, strokeWidth, blendMode)
        drawLine(left, bottom, left, top, color, strokeWidth, blendMode)
    }

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
            val r2 = radius * radius
            for (dy in -radius..radius) {
                val dx2 = r2 - dy * dy
                if (dx2 < 0) continue
                val dx = sqrt(dx2.toDouble()).toInt()
                for (x in -dx..dx) {
                    setPixel(centerX + x, centerY + dy, color, blendMode)
                }
            }
            return
        }
        var x = radius
        var y = 0
        var err = 0
        while (x >= y) {
            val strokeRadius = strokeWidth.coerceAtLeast(1) / 2
            paintStrokePoint(context, offsetX, offsetY, centerX + x, centerY + y, strokeRadius, color, blendMode)
            paintStrokePoint(context, offsetX, offsetY, centerX + y, centerY + x, strokeRadius, color, blendMode)
            paintStrokePoint(context, offsetX, offsetY, centerX - y, centerY + x, strokeRadius, color, blendMode)
            paintStrokePoint(context, offsetX, offsetY, centerX - x, centerY + y, strokeRadius, color, blendMode)
            paintStrokePoint(context, offsetX, offsetY, centerX - x, centerY - y, strokeRadius, color, blendMode)
            paintStrokePoint(context, offsetX, offsetY, centerX - y, centerY - x, strokeRadius, color, blendMode)
            paintStrokePoint(context, offsetX, offsetY, centerX + y, centerY - x, strokeRadius, color, blendMode)
            paintStrokePoint(context, offsetX, offsetY, centerX + x, centerY - y, strokeRadius, color, blendMode)
            y += 1
            if (err <= 0) {
                err += 2 * y + 1
            } else {
                x -= 1
                err += 2 * (y - x) + 1
            }
        }
    }

    public fun drawCircle(
        centerX: Int,
        centerY: Int,
        radius: Int,
        style: PixelShapeStyle,
    ) {
        drawCircle(centerX, centerY, radius, style.color, style.filled, style.strokeWidth, style.blendMode)
    }

    public fun drawPolygon(
        points: List<PixelPoint>,
        color: PixelColor,
        filled: Boolean = true,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        polygonRasterizer.drawPolygon(
            context = context,
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

    public fun drawPolygon(
        points: List<PixelPoint>,
        style: PixelShapeStyle,
    ) {
        polygonRasterizer.drawPolygon(
            context = context,
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

    public fun drawPath(
        path: PixelPath,
        style: PixelShapeStyle,
        closed: Boolean = false,
    ) {
        drawPath(path, style.color, closed, style.strokeWidth, style.blendMode)
    }
}

private fun PixelGradient.colorAt(x: Int, y: Int): PixelColor {
    return when (this) {
        is PixelGradient.Linear -> colorAt(linearOffset(x, y), sortedStops)
        is PixelGradient.Radial -> {
            val offset = if (radius == 0) {
                1f
            } else {
                val dx = x - center.x
                val dy = y - center.y
                (sqrt((dx * dx + dy * dy).toDouble()) / radius.toDouble()).toFloat()
            }
            colorAt(offset, sortedStops)
        }
    }
}

private fun PixelGradient.Linear.linearOffset(x: Int, y: Int): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0) return 1f
    val px = x - start.x
    val py = y - start.y
    return ((px * dx + py * dy).toFloat() / lengthSquared.toFloat()).coerceIn(0f, 1f)
}

private fun colorAt(offset: Float, stops: List<PixelGradientStop>): PixelColor {
    val t = offset.coerceIn(0f, 1f)
    var previous = stops.first()
    if (t <= previous.offset) return previous.color
    for (index in 1 until stops.size) {
        val next = stops[index]
        if (t <= next.offset) {
            val span = next.offset - previous.offset
            val localT = if (span <= 0f) 1f else (t - previous.offset) / span
            return lerpColor(previous.color, next.color, localT)
        }
        previous = next
    }
    return stops.last().color
}

private fun lerpColor(start: PixelColor, end: PixelColor, t: Float): PixelColor {
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
