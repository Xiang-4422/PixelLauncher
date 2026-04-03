package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * retained widget runtime 的默认工厂。
 *
 * 这层负责把 retained build runtime 与 retained render support 组装成
 * `RetainedWidgetRenderRuntime`，避免宿主侧默认 runtime 工厂继续感知 retained
 * 内部装配细节。
 */
internal object RetainedWidgetRuntimeFactory {
    fun createDefault(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
        onVisualUpdate: () -> Unit = { },
    ): WidgetRenderRuntime {
        val renderSupport = RetainedRenderSupportFactory.createDefault(
            textRasterizer = textRasterizer,
        )
        return RetainedWidgetRenderRuntime(
            buildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
                onVisualUpdate = onVisualUpdate,
                widgetAdapter = renderSupport.widgetAdapter,
            ),
            elementTreeRenderer = renderSupport.elementTreeRenderer,
        )
    }
}
