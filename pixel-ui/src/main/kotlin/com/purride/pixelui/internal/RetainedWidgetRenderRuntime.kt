package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.Widget

/**
 * retained Widget 树到 legacy renderer 的过渡运行时。
 *
 * 当前阶段它显式负责两步：
 * 1. 用 retained build runtime 把 Widget 树解析成 retained element tree
 * 2. 再由 bridge 把结果交给纯 legacy renderer 输出像素结果
 *
 * 这样 `PixelRenderRuntime` 可以继续收敛成纯渲染器，不再同时承担
 * Widget 解析职责。
 */
internal class RetainedWidgetRenderRuntime(
    textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    onVisualUpdate: () -> Unit = { },
) {
    private val renderSupport: RetainedRenderSupport = RetainedRenderSupportFactory.createDefault(
        textRasterizer = textRasterizer,
    )
    private val buildRuntime = RetainedBuildRuntime(
        onVisualUpdate = onVisualUpdate,
        widgetAdapter = renderSupport.widgetAdapter,
    )
    private val elementTreeRenderer = renderSupport.elementTreeRenderer

    fun render(
        root: Widget,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        val elementRoot = buildRuntime.resolveElementTree(root)
        return elementTreeRenderer.render(
            root = elementRoot,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )
    }

    fun dispose() {
        buildRuntime.dispose()
    }
}
