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
    private val buildRuntime = RetainedBuildRuntime(
        onVisualUpdate = onVisualUpdate,
        fallbackInflater = BridgeWidgetAdapterFactory::inflate,
    )
    private val renderRuntime = PixelRenderRuntime(textRasterizer = textRasterizer)

    fun render(
        root: Widget,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        val elementRoot = buildRuntime.resolveElementTree(root)
        val bridgeRoot = BridgeTreeResolver.resolve(elementRoot)
            ?: error("当前 Widget 树没有生成可渲染的 bridge node。")
        return renderRuntime.render(
            root = bridgeRoot,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )
    }

    fun dispose() {
        buildRuntime.dispose()
    }
}
