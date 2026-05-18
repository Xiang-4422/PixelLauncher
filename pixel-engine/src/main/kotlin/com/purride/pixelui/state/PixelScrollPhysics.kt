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
    public companion object {
        public val Clamp: PixelScrollPhysics = PixelScrollPhysics()
        public val Default: PixelScrollPhysics = Clamp
    }
}
