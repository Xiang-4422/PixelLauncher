package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * Widget 运行时的默认工厂。
 *
 * 当前默认实现仍然是 retained build runtime + bridge + legacy renderer 的组合，
 * 但宿主层只通过这层拿默认 runtime。
 */
internal object WidgetRenderRuntimeFactory {
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
