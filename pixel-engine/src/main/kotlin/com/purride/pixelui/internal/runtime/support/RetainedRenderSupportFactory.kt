package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * retained runtime 默认支持集合工厂。
 *
 * retained runtime 只通过这层拿默认支持，不直接知道 pipeline 具体装配细节。
 */
internal object RetainedRenderSupportFactory {
    /**
     * 创建 retained runtime 默认使用的支持集合。
     */
    fun createDefault(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    ): RetainedRenderSupport {
        return createDefaultAssembly(
            textRasterizer = textRasterizer,
        ).toRenderSupport()
    }

    /**
     * 创建 retained render support 的默认装配结果。
     */
    fun createDefaultAssembly(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    ): RetainedRenderSupportAssembly {
        return RetainedRenderSupportAssemblyFactory.createDefault(
            textRasterizer = textRasterizer,
        )
    }
}
