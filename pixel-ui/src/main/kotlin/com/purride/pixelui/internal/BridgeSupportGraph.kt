package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer
/**
 * bridge 层的统一装配入口。
 *
 * retained 主链只通过这个 graph 拿两种能力：
 * 1. retained widget adapter
 * 2. element tree -> 像素渲染结果
 *
 * 这样 retained 入口不再同时知道 bridge factory、bridge resolver 和 bridge runtime 的
 * 具体拼装细节。
 */
internal class BridgeSupportGraph(
    textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
) {
    val widgetAdapter: WidgetAdapter = BridgeWidgetAdapter

    private val renderRuntime = BridgeRenderRuntime(textRasterizer = textRasterizer)

    fun renderElementTree(
        root: Element?,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        val bridgeRoot = BridgeTreeResolver.resolve(root)
            ?: error("当前 Widget 树没有生成可渲染的 bridge node。")
        return renderRuntime.render(
            root = bridgeRoot,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )
    }
}
