package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.Widget

/**
 * `pixel-ui` 对宿主层暴露的内部总运行时。
 *
 * 当前它由 retained build runtime、默认 retained support、新 pipeline renderer、
 * bridge fallback 和 legacy fallback 共同组成，但宿主只依赖这一层，不直接知道
 * 下游具体实现。
 */
internal class PixelUiRuntime(
    textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    onVisualUpdate: () -> Unit = { },
) {
    private val assembly = PixelUiRuntimeAssemblyFactory.createDefault(
        textRasterizer = textRasterizer,
        onVisualUpdate = onVisualUpdate,
    )

    /**
     * 渲染显式的 Widget runtime 请求。
     */
    fun render(request: WidgetRenderRequest): PixelRenderResult {
        return assembly.render(request)
    }

    /**
     * 渲染一棵 Widget 根树。
     */
    fun render(
        root: Widget,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        return render(
            request = WidgetRenderRequestFactory.create(
                root = root,
                logicalWidth = logicalWidth,
                logicalHeight = logicalHeight,
            ),
        )
    }

    /**
     * 释放内部 Widget runtime。
     */
    fun dispose() {
        assembly.dispose()
    }
}
