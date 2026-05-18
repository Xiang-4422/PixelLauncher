package com.purride.pixeldemo.app.gesture

import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.gesture.PagerGesturePolicy

/**
 * 可在运行时动态调整参数的分页手势策略——用于 GESTURE_TUNING demo 场景。
 *
 * 通过注入 [axisBiasProvider] lambda 读取当前偏置值，让 UI 层的滑块按钮
 * 能实时改变手势灵敏度，无需重建整个 host 配置。
 *
 * @param axisBiasProvider 返回当前主轴偏置的函数（取值建议 0.5..3.0）。
 *   偏置越大，分页手势越需要更明显的主轴位移才能触发；偏置越小，越容易误触发。
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
        // 用外部 provider 的值替换默认 axisBias
        axisBias = axisBiasProvider(),
    )
}
