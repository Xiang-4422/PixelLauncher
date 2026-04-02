package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * retained element tree 到 legacy renderer 的 bridge 运行时。
 *
 * 这层只负责两件事：
 * 1. 把 retained element tree 解析成 bridge/legacy 渲染树
 * 2. 把 bridge/legacy 渲染树交给纯 legacy renderer 输出像素结果
 *
 * 这样 retained 入口可以继续只承担“build runtime + bridge runtime”的串联职责。
 */
internal class BridgeRenderRuntime(
    textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
) {
    private val legacyRenderRuntime = PixelRenderRuntime(textRasterizer = textRasterizer)

    fun render(
        root: Element?,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        val bridgeRoot = BridgeTreeResolver.resolve(root)
            ?: error("当前 Widget 树没有生成可渲染的 bridge node。")
        return legacyRenderRuntime.render(
            root = bridgeRoot,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )
    }
}
