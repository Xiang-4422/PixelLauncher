package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * Widget 运行时的默认工厂。
 *
 * 当前默认实现是 retained build runtime + pipeline renderer 的组合；默认路径不再
 * 自动装配其他后端。
 */
internal object WidgetRenderRuntimeFactory {
    /**
     * 创建宿主默认使用的 widget runtime。
     */
    fun createDefault(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
        onVisualUpdate: () -> Unit = { },
    ): WidgetRenderRuntime {
        return createDefaultAssembly(
            textRasterizer = textRasterizer,
            onVisualUpdate = onVisualUpdate,
        ).toRuntime()
    }

    /**
     * 创建宿主默认使用的 widget runtime 装配结果。
     */
    fun createDefaultAssembly(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
        onVisualUpdate: () -> Unit = { },
    ): WidgetRuntimeAssembly {
        return WidgetRuntimeAssemblyFactory.create(
            runtime = RetainedWidgetRuntimeFactory.createDefault(
                textRasterizer = textRasterizer,
                onVisualUpdate = onVisualUpdate,
            ),
        )
    }
}
