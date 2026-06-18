package com.purride.pixelui.internal.host

import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelKeyEvent
import kotlin.math.abs

internal class PixelJoystickFocusRouter(
    private val deadZone: Float = 0.5f,
    private val repeatDelayMs: Long = 300L,
    private val repeatIntervalMs: Long = 160L,
) {
    private var activeDirection: PixelKey? = null
    private var nextRepeatAtMs: Long = 0L

    fun onAxes(
        xAxis: Float,
        yAxis: Float,
        hatX: Float = 0f,
        hatY: Float = 0f,
        eventTimeMs: Long,
    ): PixelKeyEvent? {
        val direction = resolveDirection(xAxis = xAxis, yAxis = yAxis, hatX = hatX, hatY = hatY)
        if (direction == null) {
            activeDirection = null
            nextRepeatAtMs = 0L
            return null
        }
        if (direction != activeDirection) {
            activeDirection = direction
            nextRepeatAtMs = eventTimeMs + repeatDelayMs
            return PixelKeyEvent(direction)
        }
        if (eventTimeMs < nextRepeatAtMs) {
            return null
        }
        nextRepeatAtMs = eventTimeMs + repeatIntervalMs
        return PixelKeyEvent(direction)
    }

    private fun resolveDirection(
        xAxis: Float,
        yAxis: Float,
        hatX: Float,
        hatY: Float,
    ): PixelKey? {
        val x = dominantAxis(hatX, xAxis)
        val y = dominantAxis(hatY, yAxis)
        val absX = abs(x)
        val absY = abs(y)
        if (absX < deadZone && absY < deadZone) return null
        return if (absX > absY) {
            if (x < 0f) PixelKey.ARROW_LEFT else PixelKey.ARROW_RIGHT
        } else {
            if (y < 0f) PixelKey.ARROW_UP else PixelKey.ARROW_DOWN
        }
    }

    private fun dominantAxis(first: Float, second: Float): Float {
        return if (abs(first) >= abs(second)) first else second
    }
}
