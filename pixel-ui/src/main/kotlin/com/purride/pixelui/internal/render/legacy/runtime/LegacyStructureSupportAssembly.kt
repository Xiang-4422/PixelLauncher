package com.purride.pixelui.internal

/**
 * legacy 文本之外其余 support 的装配结果。
 *
 * 这层承接布局、视口、测量、节点渲染和根渲染 support，
 * 让默认 factory 可以按“文本 support”和“其余 support”两段装配。
 */
internal data class LegacyStructureSupportAssembly(
    val layoutRenderSupport: PixelLayoutRenderSupport,
    val viewportRenderSupport: PixelViewportRenderSupport,
    val measureSupport: PixelMeasureSupport,
    val nodeRenderSupport: PixelNodeRenderSupport,
    val rootRenderSupport: PixelRootRenderSupport,
)
