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
    /**
     * 创建默认的 retained widget runtime。
     */
    fun createDefault(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
        onVisualUpdate: () -> Unit = { },
    ): WidgetRenderRuntime {
        val assembly = createDefaultAssembly(
            textRasterizer = textRasterizer,
            onVisualUpdate = onVisualUpdate,
        )
        return RetainedWidgetRenderRuntime(
            buildRuntime = assembly.buildRuntime,
            elementTreeRenderer = assembly.elementTreeRenderer,
        )
    }

    /**
     * 创建默认的 retained widget runtime 装配结果。
     */
    fun createDefaultAssembly(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
        onVisualUpdate: () -> Unit = { },
    ): RetainedWidgetRuntimeAssembly {
        val renderSupport = RetainedRenderSupportFactory.createDefault(
            textRasterizer = textRasterizer,
        )
        return RetainedWidgetRuntimeAssembly(
            buildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
                onVisualUpdate = onVisualUpdate,
                widgetAdapter = renderSupport.widgetAdapter,
            ),
            elementTreeRenderer = renderSupport.elementTreeRenderer,
        )
    }
}
