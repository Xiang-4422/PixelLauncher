package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis
import kotlin.math.abs

/**
 * direct pipeline 的分页手势启动规则。
 */
internal object PagerGesturePolicy {
    private const val DEFAULT_AXIS_BIAS = 1.2f

    /**
     * 判断一次触摸位移是否应该升级成分页拖拽。
     */
    fun shouldStartDrag(
        axis: PixelAxis,
        deltaX: Float,
        deltaY: Float,
        touchSlopPx: Float,
        axisBias: Float = DEFAULT_AXIS_BIAS,
    ): Boolean {
        val absDeltaX = abs(deltaX)
        val absDeltaY = abs(deltaY)
        return when (axis) {
            PixelAxis.HORIZONTAL -> absDeltaX > touchSlopPx && absDeltaX > absDeltaY * axisBias
            PixelAxis.VERTICAL -> absDeltaY > touchSlopPx && absDeltaY > absDeltaX * axisBias
        }
    }
}
