package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * 负责创建 retained render support 的默认装配结果。
 */
internal object RetainedRenderSupportAssemblyFactory {
    /**
     * 创建默认的 retained render support 装配结果。
     */
    fun createDefault(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    ): RetainedRenderSupportAssembly {
        return RetainedRenderSupportAssembly(
            widgetAdapter = UnsupportedWidgetAdapter,
            elementTreeRenderer = PipelineElementTreeRenderer(),
        )
    }
}
