package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis
import kotlin.math.abs

/**
 * Pager 手势仲裁规则。
 *
 * 当前阶段把“什么时候应该把一次触摸升级成分页拖拽”独立成纯 Kotlin 策略，
 * 这样既便于单测，也能避免把触摸判定细节散落在宿主 View 里。
 */
internal object PagerGesturePolicy {
    private const val DEFAULT_AXIS_BIAS = 1.2f

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
