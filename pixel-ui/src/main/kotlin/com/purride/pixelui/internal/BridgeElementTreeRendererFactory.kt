package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * bridge element tree renderer 的默认工厂。
 *
 * 默认实现仍然由 `BridgeRenderRuntime` 驱动，但上层 support 不再直接感知
 * `BridgeRenderRuntime` 的具体构造细节。
 */
internal object BridgeElementTreeRendererFactory {
    fun createDefault(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    ): ElementTreeRenderer {
        return BridgeElementTreeRenderer(
            renderRuntime = BridgeRenderRuntime(textRasterizer = textRasterizer),
        )
    }
}
