package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBlendMode
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelPoint
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal class PixelPolygonRasterizer {
    private val intersections = mutableListOf<Int>()

    fun drawPolygon(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
        width: Int,
        height: Int,
        points: List<PixelPoint>,
        color: PixelColor,
        filled: Boolean,
        strokeWidth: Int = 1,
        blendMode: PixelBlendMode = PixelBlendMode.SrcOver,
    ) {
        val safeStrokeWidth = strokeWidth.coerceAtLeast(1)
        if (points.size < 2 || width <= 0 || height <= 0) return
        if (!filled || points.size < 3) {
            drawOutline(
                context,
                offsetX,
                offsetY,
                points,
                color,
                blendMode,
                skipFilledPixels = false,
                width,
                height,
                safeStrokeWidth,
            )
            return
        }
        drawFill(context, offsetX, offsetY, width, height, points, color, blendMode)
        drawOutline(
            context,
            offsetX,
            offsetY,
            points,
            color,
            blendMode,
            skipFilledPixels = true,
            width,
            height,
            safeStrokeWidth,
        )
    }

    private fun drawFill(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
        width: Int,
        height: Int,
        points: List<PixelPoint>,
        color: PixelColor,
        blendMode: PixelBlendMode,
    ) {
        val minY = max(0, points.minOf { it.y })
        val maxY = min(height - 1, points.maxOf { it.y })
        if (maxY < minY) return
        for (y in minY..maxY) {
            collectScanlineIntersections(points, y)
            var i = 0
            while (i + 1 < intersections.size) {
                val startX = max(0, intersections[i])
                val endX = min(width - 1, intersections[i + 1])
                for (x in startX..endX) {
                    context.buffer.setPixel(offsetX + x, offsetY + y, color, blendMode)
                }
                i += 2
            }
        }
    }

    private fun drawOutline(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
        points: List<PixelPoint>,
        color: PixelColor,
        blendMode: PixelBlendMode,
        skipFilledPixels: Boolean,
        width: Int,
        height: Int,
        strokeWidth: Int,
    ) {
        for (index in points.indices) {
            val start = points[index]
            val end = points[(index + 1) % points.size]
            if (start == end) continue
            drawLine(context, offsetX, offsetY, start, end, color, blendMode, skipFilledPixels, width, height, strokeWidth, points)
        }
    }

    private fun drawLine(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
        start: PixelPoint,
        end: PixelPoint,
        color: PixelColor,
        blendMode: PixelBlendMode,
        skipFilledPixels: Boolean,
        width: Int,
        height: Int,
        strokeWidth: Int,
        points: List<PixelPoint>,
    ) {
        val strokeRadius = strokeWidth / 2
        var x0 = start.x
        var y0 = start.y
        val x1 = end.x
        val y1 = end.y
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            if (!skipFilledPixels || !isFilledPixel(points, x0, y0, width, height)) {
                paintStrokePoint(context, offsetX, offsetY, x0, y0, strokeRadius, color, blendMode)
            }
            if (x0 == x1 && y0 == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x0 += sx }
            if (e2 <= dx) { err += dx; y0 += sy }
        }
    }

    private fun isFilledPixel(points: List<PixelPoint>, x: Int, y: Int, width: Int, height: Int): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        collectScanlineIntersections(points, y)
        var i = 0
        while (i + 1 < intersections.size) {
            val startX = max(0, intersections[i])
            val endX = min(width - 1, intersections[i + 1])
            if (x in startX..endX) return true
            i += 2
        }
        return false
    }

    private fun collectScanlineIntersections(points: List<PixelPoint>, y: Int) {
        intersections.clear()
        for (index in points.indices) {
            val a = points[index]
            val b = points[(index + 1) % points.size]
            if (a.y == b.y) continue
            val minEdgeY = min(a.y, b.y)
            val maxEdgeY = max(a.y, b.y)
            if (y < minEdgeY || y >= maxEdgeY) continue
            intersections += scanlineIntersectionX(a, b, y)
        }
        intersections.sort()
    }

    private fun scanlineIntersectionX(a: PixelPoint, b: PixelPoint, y: Int): Int {
        val fraction = (y - a.y).toDouble() / (b.y - a.y).toDouble()
        return floor(a.x + fraction * (b.x - a.x)).toInt()
    }
}
