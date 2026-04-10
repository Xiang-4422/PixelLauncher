package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * 负责创建 legacy render support 的默认装配结果。
 */
internal object LegacyRenderSupportAssemblyFactory {
    /**
     * 创建默认的 legacy render support 装配结果。
     */
    fun createDefault(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    ): LegacyRenderSupportAssembly {
        return LegacyRenderSupportAssembly(
            renderSupport = LegacyRenderSupportBundle(
                textRasterizer = textRasterizer,
            ),
        )
    }
}
