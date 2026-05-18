package com.purride.pixelcore

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 轴向像素合成器。
 *
 * 它只负责把“当前缓冲”和“相邻缓冲”按偏移量拼成一帧，不关心这些缓冲为什么会相邻。
 */
public object AxisBufferComposer {
    /**
     * 判断当前配置是否真的需要合成（secondary 存在且偏移超过 epsilon）。
     *
     * 调用方可以据此决定是否预先从池里 acquire 出 `out` buffer，避免
     * 在 0 偏移帧上做无用功。
     */
    public fun isCompositionNeeded(secondary: PixelBuffer?, offsetPx: Float): Boolean {
        return secondary != null && abs(offsetPx) >= COMPOSITION_EPSILON_PX
    }

    /**
     * 把 primary/secondary 拼成一帧。
     *
     * 当 [out] 为 null 时，会内部新建一份缓冲（兼容旧用法）；推荐传入一个
     * 来自 PixelBufferPool 的 buffer 以避开每帧分配。当合成本身不必要
     * （secondary 为空或偏移不足 epsilon）时，直接返回 primary，
     * 此时 [out] 不会被写入，仍由调用方负责归还。
     */
    public fun compose(
        primary: PixelBuffer,
        secondary: PixelBuffer?,
        axis: PixelAxis,
        offsetPx: Float,
        out: PixelBuffer? = null,
    ): PixelBuffer {
        if (!isCompositionNeeded(secondary, offsetPx)) {
            return primary
        }

        val target = out ?: PixelBuffer(width = primary.width, height = primary.height)
        target.clear()

        when (axis) {
            PixelAxis.HORIZONTAL -> composeHorizontal(
                out = target,
                primary = primary,
                secondary = secondary!!,
                offsetPx = offsetPx,
            )

            PixelAxis.VERTICAL -> composeVertical(
                out = target,
                primary = primary,
                secondary = secondary!!,
                offsetPx = offsetPx,
            )
        }
        return target
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
