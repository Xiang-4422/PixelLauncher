package com.purride.pixelui.internal

/**
 * legacy viewport support 的默认工厂。
 *
 * 这层集中创建 pager/list/scroll 共用的 viewport 渲染 support，
 * 避免结构 support 工厂继续承担具体 viewport wiring。
 */
internal object LegacyViewportSupportFactory {
    /**
     * 创建默认的 viewport support 装配结果。
     */
    fun createDefault(
        callbacks: LegacyRenderCallbacks,
        scrollAxisUnboundedMax: Int,
    ): LegacyViewportSupportAssembly {
        val viewportRenderSupport = PixelViewportRenderSupport(
            callbacks = callbacks,
            scrollAxisUnboundedMax = scrollAxisUnboundedMax,
        )
        return LegacyViewportSupportAssembly(
            viewportRenderSupport = viewportRenderSupport,
        )
    }
}
