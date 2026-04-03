package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * bridge 渲染入口的默认工厂。
 *
 * 当前默认实现仍然是 `BridgeRenderRuntime`，但 element tree renderer 只通过
 * 这层拿默认 bridge renderer。
 */
internal object BridgeTreeRendererFactory {
    fun createDefault(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    ): BridgeTreeRenderer {
        return BridgeRenderRuntime(textRasterizer = textRasterizer)
    }
}
