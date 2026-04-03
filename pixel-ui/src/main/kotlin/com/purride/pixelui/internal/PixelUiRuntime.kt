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
    private val runtime: WidgetRenderRuntime = WidgetRenderRuntimeFactory.createDefault(
        textRasterizer = textRasterizer,
        onVisualUpdate = onVisualUpdate,
    )

    fun render(
        root: Widget,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        return runtime.render(
            root = root,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )
    }

    fun dispose() {
        runtime.dispose()
    }
}
