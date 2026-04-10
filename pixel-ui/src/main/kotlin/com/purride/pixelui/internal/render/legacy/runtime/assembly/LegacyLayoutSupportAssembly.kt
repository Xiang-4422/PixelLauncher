package com.purride.pixelui.internal

/**
 * legacy 布局 support 的装配结果。
 *
 * 这层把布局渲染和布局测量共用的 support 收成一个中间结果，
 * 让结构 support 工厂只关心更高层的组合关系。
 */
internal data class LegacyLayoutSupportAssembly(
    val layoutRenderSupport: PixelLayoutRenderSupport,
    val layoutMeasureSupport: PixelLayoutMeasureSupport,
)
