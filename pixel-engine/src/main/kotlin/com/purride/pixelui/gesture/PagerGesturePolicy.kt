package com.purride.pixelui.gesture

import com.purride.pixelcore.PixelAxis
import kotlin.math.abs

/**
 * 分页手势启动规则。
 *
 * 决定一次触摸位移是否应该升级成 PageView 的拖拽。默认实现按"主轴位移
 * 超过 touchSlop 且明显大于次轴位移 * axisBias"判断；宿主可以继承此类
 * 重写 [shouldStartDrag] 提供更激进/保守的策略，然后通过
 * `PixelHostSetupConfig.pagerGesturePolicy` 注入。
 */
public open class PagerGesturePolicy {
    /**
     * 判断一次触摸位移是否应该升级成分页拖拽。
     *
     * @param axis 分页的主轴
     * @param deltaX 自触摸起点的 X 累积位移（像素）
     * @param deltaY 自触摸起点的 Y 累积位移（像素）
     * @param touchSlopPx 系统 touchSlop（像素）
     * @param axisBias 主轴优先程度（默认 1.2）
     */
    public open fun shouldStartDrag(
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

    public companion object {
        public const val DEFAULT_AXIS_BIAS: Float = 1.2f

        /**
         * 默认策略实例。绝大多数宿主直接复用即可。
         */
        @JvmField
        public val Default: PagerGesturePolicy = PagerGesturePolicy()
    }
}
