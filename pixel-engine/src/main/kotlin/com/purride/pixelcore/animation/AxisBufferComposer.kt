package com.purride.pixelcore

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 轴向像素合成器。
 *
 * 它只负责把“当前缓冲”和“相邻缓冲”按偏移量拼成一帧，不关心这些缓冲为什么会相邻。
 */
object AxisBufferComposer {
    fun compose(
        primary: PixelBuffer,
        secondary: PixelBuffer?,
        axis: PixelAxis,
        offsetPx: Float,
    ): PixelBuffer {
        if (secondary == null || abs(offsetPx) < COMPOSITION_EPSILON_PX) {
            return primary
        }

        val out = PixelBuffer(width = primary.width, height = primary.height)
        out.clear()

        when (axis) {
            PixelAxis.HORIZONTAL -> composeHorizontal(
                out = out,
                primary = primary,
                secondary = secondary,
                offsetPx = offsetPx,
            )

            PixelAxis.VERTICAL -> composeVertical(
                out = out,
                primary = primary,
                secondary = secondary,
                offsetPx = offsetPx,
            )
        }
        return out
    }

    private fun composeHorizontal(
        out: PixelBuffer,
        primary: PixelBuffer,
        secondary: PixelBuffer,
        offsetPx: Float,
    ) {
        val currentShiftX = offsetPx.roundToInt().coerceIn(-primary.width, primary.width)
        out.blit(primary, destX = currentShiftX, destY = 0)

        val adjacentShiftX = if (offsetPx > 0f) {
            currentShiftX - primary.width
        } else {
            currentShiftX + primary.width
        }
        out.blit(secondary, destX = adjacentShiftX, destY = 0)
    }

    private fun composeVertical(
        out: PixelBuffer,
        primary: PixelBuffer,
        secondary: PixelBuffer,
        offsetPx: Float,
    ) {
        val currentShiftY = offsetPx.roundToInt().coerceIn(-primary.height, primary.height)
        out.blit(primary, destX = 0, destY = currentShiftY)

        val adjacentShiftY = if (offsetPx > 0f) {
            currentShiftY - primary.height
        } else {
            currentShiftY + primary.height
        }
        out.blit(secondary, destX = 0, destY = adjacentShiftY)
    }

    private const val COMPOSITION_EPSILON_PX = 0.5f
}
