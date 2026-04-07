package com.purride.pixelui.internal

/**
 * legacy 节点运行时 support 的装配结果。
 *
 * 这层把测量、节点渲染和根渲染 support 收成同一段中间结果，
 * 让结构 support 工厂可以只负责更高层的组合顺序。
 */
internal data class LegacyNodeSupportAssembly(
    val measureSupport: PixelMeasureSupport,
    val nodeRenderSupport: PixelNodeRenderSupport,
    val rootRenderSupport: PixelRootRenderSupport,
)
