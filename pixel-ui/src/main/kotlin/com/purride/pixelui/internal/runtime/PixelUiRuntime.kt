package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.Widget

/**
 * `pixel-ui` 对宿主层暴露的内部总运行时。
 *
 * 当前它仍然由 retained build runtime、默认 retained support、bridge renderer
 * 和 legacy renderer 共同组成，但宿主只依赖这一层，不再直接知道 retained
 * runtime 的具体实现类。
 */
internal class PixelUiRuntime(
    textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    onVisualUpdate: () -> Unit = { },
) {
    private val assembly = WidgetRenderRuntimeFactory.createDefaultAssembly(
        textRasterizer = textRasterizer,
        onVisualUpdate = onVisualUpdate,
    )
    private val runtime: WidgetRenderRuntime
        get() = assembly.runtime

    /**
     * 渲染显式的 Widget runtime 请求。
     */
    fun render(request: WidgetRenderRequest): PixelRenderResult {
        return runtime.render(request)
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
            request = WidgetRenderRequest(
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
        assembly.runtime.dispose()
    }
}
