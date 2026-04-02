package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.Widget

/**
 * bridge 层的统一装配入口。
 *
 * retained 主链只通过这个 graph 拿两种能力：
 * 1. fallback widget inflater
 * 2. element tree -> 像素渲染结果
 *
 * 这样 retained 入口不再同时知道 bridge factory、bridge resolver 和 bridge runtime 的
 * 具体拼装细节。
 */
internal class BridgeSupportGraph(
    textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
) {
    private val renderRuntime = BridgeRenderRuntime(textRasterizer = textRasterizer)

    fun fallbackInflater(widget: Widget): Element? {
        return BridgeWidgetAdapterFactory.inflate(widget)
    }

    fun renderElementTree(
        root: Element?,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        return renderRuntime.render(
            root = root,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )
    }
}
