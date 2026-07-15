package com.purride.pixelui

/**
 * 像素滚动物理参数。
 *
 * 默认值保持既有列表滚动行为：夹紧边界、线性减速、没有回弹。
 */
public data class PixelScrollPhysics(
    val decelerationPxPerSecondSquared: Float = 2400f,
    val minFlingVelocityPxPerSecond: Float = 12f,
    val snapEpsilonPx: Float = 0.25f,
    val bounceEnabled: Boolean = false,
    val bounceOverscrollLimitPx: Float = 0f,
    val bounceResistance: Float = 0.5f,
) {
    /** 集中提供 `PixelScrollPhysics` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 提供 `PixelScrollPhysics` 的 `Clamp` 稳定默认值或常量。 */
        public val Clamp: PixelScrollPhysics = PixelScrollPhysics()
        /** 提供 `PixelScrollPhysics` 的 `Default` 稳定默认值或常量。 */
        public val Default: PixelScrollPhysics = Clamp
    }
}
