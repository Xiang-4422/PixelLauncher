package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * 负责创建宿主顶层 `PixelUiRuntime` 的默认装配结果。
 */
internal object PixelUiRuntimeAssemblyFactory {
    /**
     * 创建默认的 `PixelUiRuntime` 装配结果。
     */
    fun createDefault(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
        onVisualUpdate: () -> Unit = { },
    ): PixelUiRuntimeAssembly {
        return PixelUiRuntimeAssembly(
            widgetRuntimeAssembly = WidgetRenderRuntimeFactory.createDefaultAssembly(
                textRasterizer = textRasterizer,
                onVisualUpdate = onVisualUpdate,
            ),
        )
    }
}
