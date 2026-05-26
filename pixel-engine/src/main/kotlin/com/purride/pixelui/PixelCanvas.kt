package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.internal.PaintContext
import com.purride.pixelui.internal.drawLinePixels
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
    ) {
        drawLinePixels(context, offsetX, offsetY, startX, startY, endX, endY, color)
    }

    public fun drawRect(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        color: PixelColor,
    ) {
        context.buffer.drawRect(offsetX + left, offsetY + top, width, height, color)
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

    public fun drawCircle(
        centerX: Int,
        centerY: Int,
        radius: Int,
        color: PixelColor,
        filled: Boolean = true,
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
            setPixel(centerX + x, centerY + y, color)
            setPixel(centerX + y, centerY + x, color)
            setPixel(centerX - y, centerY + x, color)
            setPixel(centerX - x, centerY + y, color)
            setPixel(centerX - x, centerY - y, color)
            setPixel(centerX - y, centerY - x, color)
            setPixel(centerX + y, centerY - x, color)
            setPixel(centerX + x, centerY - y, color)
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
                    current?.let { start -> drawLine(start.x, start.y, command.point.x, command.point.y, color) }
                    current = command.point
                    lastPoint = command.point
                }
                PixelPathCommand.Close -> {
                    val start = current
                    val end = subpathStart
                    if (start != null && end != null) {
                        drawLine(start.x, start.y, end.x, end.y, color)
                        current = end
                    }
                }
            }
        }
        val start = subpathStart
        val end = lastPoint
        if (closed && start != null && end != null && start != end) {
            drawLine(end.x, end.y, start.x, start.y, color)
        }
    }
}
