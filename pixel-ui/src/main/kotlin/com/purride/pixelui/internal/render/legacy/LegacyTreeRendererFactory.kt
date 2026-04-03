package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * legacy 渲染入口的默认工厂。
 *
 * 当前默认实现仍然是 `PixelRenderRuntime`，但 bridge 层只通过这层拿默认 renderer。
 */
internal object LegacyTreeRendererFactory {
    fun createDefault(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    ): LegacyTreeRenderer {
        return PixelRenderRuntime(
            renderSupport = LegacyRenderSupportFactory.createDefault(
                textRasterizer = textRasterizer,
            ),
        )
    }
}
