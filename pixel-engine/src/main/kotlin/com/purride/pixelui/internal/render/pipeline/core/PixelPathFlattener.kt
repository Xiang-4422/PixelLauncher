package com.purride.pixelui.internal

import com.purride.pixelui.PixelPath
import com.purride.pixelui.PixelPathCommand
import com.purride.pixelui.PixelPoint
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

internal fun visitPixelPathSegments(
    path: PixelPath,
    closed: Boolean,
    visitor: (startX: Int, startY: Int, endX: Int, endY: Int) -> Unit,
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
                current?.let { start ->
                    visitor(start.x, start.y, command.point.x, command.point.y)
                }
                current = command.point
                lastPoint = command.point
            }
            is PixelPathCommand.QuadraticTo -> {
                current?.let { start ->
                    visitQuadraticSegments(start, command.control, command.end, visitor)
                }
                current = command.end
                lastPoint = command.end
            }
            is PixelPathCommand.CubicTo -> {
                current?.let { start ->
                    visitCubicSegments(start, command.control1, command.control2, command.end, visitor)
                }
                current = command.end
                lastPoint = command.end
            }
            PixelPathCommand.Close -> {
                val start = current
                val end = subpathStart
                if (start != null && end != null) {
                    visitor(start.x, start.y, end.x, end.y)
                    current = end
                    lastPoint = end
                }
            }
        }
    }
    val start = subpathStart
    val end = lastPoint
    if (closed && start != null && end != null && start != end) {
        visitor(end.x, end.y, start.x, start.y)
    }
}

private fun visitQuadraticSegments(
    start: PixelPoint,
    control: PixelPoint,
    end: PixelPoint,
    visitor: (Int, Int, Int, Int) -> Unit,
) {
    val steps = curveStepCount(start, control, end)
    var previousX = start.x
    var previousY = start.y
    for (step in 1..steps) {
        val t = step.toDouble() / steps.toDouble()
        val inverse = 1.0 - t
        val x = (inverse * inverse * start.x + 2.0 * inverse * t * control.x + t * t * end.x).roundToInt()
        val y = (inverse * inverse * start.y + 2.0 * inverse * t * control.y + t * t * end.y).roundToInt()
        if (x != previousX || y != previousY) {
            visitor(previousX, previousY, x, y)
            previousX = x
            previousY = y
        }
    }
}

private fun visitCubicSegments(
    start: PixelPoint,
    control1: PixelPoint,
    control2: PixelPoint,
    end: PixelPoint,
    visitor: (Int, Int, Int, Int) -> Unit,
) {
    val steps = curveStepCount(start, control1, control2, end)
    var previousX = start.x
    var previousY = start.y
    for (step in 1..steps) {
        val t = step.toDouble() / steps.toDouble()
        val inverse = 1.0 - t
        val inverseSquared = inverse * inverse
        val tSquared = t * t
        val x = (
            inverseSquared * inverse * start.x +
                3.0 * inverseSquared * t * control1.x +
                3.0 * inverse * tSquared * control2.x +
                tSquared * t * end.x
            ).roundToInt()
        val y = (
            inverseSquared * inverse * start.y +
                3.0 * inverseSquared * t * control1.y +
                3.0 * inverse * tSquared * control2.y +
                tSquared * t * end.y
            ).roundToInt()
        if (x != previousX || y != previousY) {
            visitor(previousX, previousY, x, y)
            previousX = x
            previousY = y
        }
    }
}

private fun curveStepCount(
    start: PixelPoint,
    control: PixelPoint,
    end: PixelPoint,
): Int {
    return curveStepCount(
        controlPolygonLength = distance(start, control) + distance(control, end),
    )
}

private fun curveStepCount(
    start: PixelPoint,
    control1: PixelPoint,
    control2: PixelPoint,
    end: PixelPoint,
): Int {
    return curveStepCount(
        controlPolygonLength = distance(start, control1) +
            distance(control1, control2) +
            distance(control2, end),
    )
}

private fun curveStepCount(controlPolygonLength: Double): Int {
    return ceil(controlPolygonLength / 2.0).toInt().coerceIn(1, 256)
}

private fun distance(start: PixelPoint, end: PixelPoint): Double {
    return hypot(
        (end.x - start.x).toDouble(),
        (end.y - start.y).toDouble(),
    )
}
