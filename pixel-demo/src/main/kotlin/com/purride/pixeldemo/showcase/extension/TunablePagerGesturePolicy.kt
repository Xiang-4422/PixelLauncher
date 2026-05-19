package com.purride.pixeldemo.showcase.extension

import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.gesture.PagerGesturePolicy

/**
 * 可在运行时动态调整参数的分页手势策略——用于 custom_pager_policy demo 场景。
 *
 * 通过注入 [axisBiasProvider] lambda 读取当前偏置值，让 UI 层的按钮
 * 能实时改变手势灵敏度，无需重建整个 host 配置。
 *
 * @param axisBiasProvider 返回当前主轴偏置的函数（取值建议 0.5..3.0）。
 */
class TunablePagerGesturePolicy(
    private val axisBiasProvider: () -> Float,
) : PagerGesturePolicy() {

    override fun shouldStartDrag(
        axis: PixelAxis,
        deltaX: Float,
        deltaY: Float,
        touchSlopPx: Float,
        axisBias: Float,
    ): Boolean = super.shouldStartDrag(
        axis = axis,
        deltaX = deltaX,
        deltaY = deltaY,
        touchSlopPx = touchSlopPx,
        axisBias = axisBiasProvider(),
    )
}
