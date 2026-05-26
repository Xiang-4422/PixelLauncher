package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.internal.PaintContext
import com.purride.pixelui.internal.drawLinePixels
import com.purride.pixelui.internal.paintStrokePoint
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

public class PixelCanvas internal constructor(
    private val context: PaintContext,
    private val offsetX: Int,
    private val offsetY: Int,
    public val width: Int,
    public val height: Int,
) {
    private val intersections = mutableListOf<Int>()

    public fun setPixel(x: Int, y: Int, color: PixelColor) {
        context.buffer.setPixel(offsetX + x, offsetY + y, color)
    }

    public fun drawLine(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        color: PixelColor,
        strokeWidth: Int = 1,
    ) {
        drawLinePixels(context, offsetX, offsetY, startX, startY, endX, endY, color, strokeWidth)
    }

    public fun drawRect(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        color: PixelColor,
        strokeWidth: Int = 1,
    ) {
        if (strokeWidth <= 1) {
            context.buffer.drawRect(offsetX + left, offsetY + top, width, height, color)
            return
        }
        if (width <= 0 || height <= 0) return
        val right = left + width - 1
        val bottom = top + height - 1
        drawLine(left, top, right, top, color, strokeWidth)
        drawLine(right, top, right, bottom, color, strokeWidth)
        drawLine(right, bottom, left, bottom, color, strokeWidth)
        drawLine(left, bottom, left, top, color, strokeWidth)
    }

    public fun fillRect(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        color: PixelColor,
    ) {
        context.buffer.fillRect(offsetX + left, offsetY + top, width, height, color)
    }

    public fun fillGradientRect(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        gradient: PixelGradient,
    ) {
        if (width <= 0 || height <= 0) return
        for (y in top until top + height) {
            for (x in left until left + width) {
                setPixel(x, y, gradient.colorAt(x, y))
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
    ) {
        if (radius < 0) return
        if (filled) {
            val r2 = radius * radius
            for (dy in -radius..radius) {
                val dx2 = r2 - dy * dy
                if (dx2 < 0) continue
                val dx = sqrt(dx2.toDouble()).toInt()
                for (x in -dx..dx) {
                    setPixel(centerX + x, centerY + dy, color)
                }
            }
            return
        }
        var x = radius
        var y = 0
        var err = 0
        while (x >= y) {
            val strokeRadius = strokeWidth.coerceAtLeast(1) / 2
            paintStrokePoint(context, offsetX, offsetY, centerX + x, centerY + y, strokeRadius, color)
            paintStrokePoint(context, offsetX, offsetY, centerX + y, centerY + x, strokeRadius, color)
            paintStrokePoint(context, offsetX, offsetY, centerX - y, centerY + x, strokeRadius, color)
            paintStrokePoint(context, offsetX, offsetY, centerX - x, centerY + y, strokeRadius, color)
            paintStrokePoint(context, offsetX, offsetY, centerX - x, centerY - y, strokeRadius, color)
            paintStrokePoint(context, offsetX, offsetY, centerX - y, centerY - x, strokeRadius, color)
            paintStrokePoint(context, offsetX, offsetY, centerX + y, centerY - x, strokeRadius, color)
            paintStrokePoint(context, offsetX, offsetY, centerX + x, centerY - y, strokeRadius, color)
            y += 1
            if (err <= 0) {
                err += 2 * y + 1
            } else {
                x -= 1
                err += 2 * (y - x) + 1
            }
        }
    }

    public fun drawPolygon(
        points: List<PixelPoint>,
        color: PixelColor,
        filled: Boolean = true,
    ) {
        if (points.size < 2) return
        if (!filled || points.size < 3) {
            points.indices.forEach { index ->
                val start = points[index]
                val end = points[(index + 1) % points.size]
                drawLine(start.x, start.y, end.x, end.y, color)
            }
            return
        }
        val minY = max(0, points.minOf { it.y })
        val maxY = min(height - 1, points.maxOf { it.y })
        if (maxY < minY) return
        for (y in minY..maxY) {
            intersections.clear()
            for (index in points.indices) {
                val a = points[index]
                val b = points[(index + 1) % points.size]
                if (a.y == b.y) continue
                val minEdgeY = min(a.y, b.y)
                val maxEdgeY = max(a.y, b.y)
                if (y < minEdgeY || y >= maxEdgeY) continue
                val x = a.x + ((y - a.y).toLong() * (b.x - a.x) / (b.y - a.y)).toInt()
                intersections += x
            }
            intersections.sort()
            var i = 0
            while (i + 1 < intersections.size) {
                val startX = max(0, intersections[i])
                val endX = min(width - 1, intersections[i + 1])
                for (x in startX..endX) setPixel(x, y, color)
                i += 2
            }
        }
    }

    public fun drawPath(
        path: PixelPath,
        color: PixelColor,
        closed: Boolean = false,
        strokeWidth: Int = 1,
    ) {
        var current: PixelPoint? = null
        var subpathStart: PixelPoint? = null
        var lastPoint: PixelPoint? = null
        for (command in path.commands) {
            when (command) {
                is PixelPathCommand.MoveTo -> {
                    current = command.point
                    subpathStart = command.point
                    lastPoint = command.point
                }
                is PixelPathCommand.LineTo -> {
                    current?.let { start -> drawLine(start.x, start.y, command.point.x, command.point.y, color, strokeWidth) }
                    current = command.point
                    lastPoint = command.point
                }
                PixelPathCommand.Close -> {
                    val start = current
                    val end = subpathStart
                    if (start != null && end != null) {
                        drawLine(start.x, start.y, end.x, end.y, color, strokeWidth)
                        current = end
                    }
                }
            }
        }
        val start = subpathStart
        val end = lastPoint
        if (closed && start != null && end != null && start != end) {
            drawLine(end.x, end.y, start.x, start.y, color, strokeWidth)
        }
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
