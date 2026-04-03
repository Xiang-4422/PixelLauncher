package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTextRasterizer

/**
 * legacy 文本相关 support 的默认工厂。
 *
 * 这层只负责文本与文本输入 support 的装配，
 * 让更高一层的 assembly factory 不必同时承担全部 support 的构造细节。
 */
internal object LegacyTextSupportFactory {
    /**
     * 创建默认的文本 support 装配结果。
     */
    fun createDefault(
        textRasterizer: PixelTextRasterizer,
    ): LegacyTextSupportAssembly {
        val textRenderSupport = PixelTextRenderSupport(
            defaultTextRasterizer = textRasterizer,
        )
        val textFieldRenderSupport = PixelTextFieldRenderSupport(
            defaultTextRasterizer = textRasterizer,
            textRenderSupport = textRenderSupport,
        )
        return LegacyTextSupportAssembly(
            textRenderSupport = textRenderSupport,
            textFieldRenderSupport = textFieldRenderSupport,
        )
    }
}
