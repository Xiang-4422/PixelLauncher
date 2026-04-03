package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * legacy support 的默认工厂。
 *
 * 当前默认实现仍然是 `LegacyRenderSupportBundle`，但 façade 只通过这层拿默认 support。
 */
internal object LegacyRenderSupportFactory {
    fun createDefault(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    ): LegacyRenderSupport {
        return LegacyRenderSupportBundle(textRasterizer = textRasterizer)
    }
}
