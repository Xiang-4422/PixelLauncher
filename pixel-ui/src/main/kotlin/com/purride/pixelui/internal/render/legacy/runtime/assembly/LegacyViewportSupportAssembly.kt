package com.purride.pixelui.internal

/**
 * legacy viewport support 的装配结果。
 *
 * 这层把分页和滚动节点共用的 viewport 渲染 support 收成一个中间结果，
 * 让结构 support 工厂继续退回更高层的组合职责。
 */
internal data class LegacyViewportSupportAssembly(
    val viewportRenderSupport: PixelViewportRenderSupport,
)
