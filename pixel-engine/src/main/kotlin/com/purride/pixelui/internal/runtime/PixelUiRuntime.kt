package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBufferPool
import com.purride.pixelui.Widget

/**
 * pixel-engine UI layer 对宿主层暴露的内部运行时。
 *
 * 渲染一帧的调用链：
 * ```
 * PixelUiRuntime.render
 *   -> ElementTreeBuildRuntime.resolveElementTree
 *   -> ElementTreeRenderer.render
 * ```
 */
internal class PixelUiRuntime(
    onVisualUpdate: () -> Unit = { },
) {
    private val bufferPool: PixelBufferPool = PixelBufferPool()
    private val elementTreeRenderer: ElementTreeRenderer = PipelineElementTreeRenderer(bufferPool = bufferPool)
    private val buildRuntime: ElementTreeBuildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
        onVisualUpdate = onVisualUpdate,
        widgetAdapter = UnsupportedWidgetAdapter,
    )

    fun render(request: WidgetRenderRequest): PixelRenderResult {
        val root = buildRuntime.resolveElementTree(request.root)
        return elementTreeRenderer.render(
            request = ElementTreeRenderRequest(
                root = root,
                logicalWidth = request.logicalWidth,
                logicalHeight = request.logicalHeight,
            ),
        )
    }

    fun render(root: Widget, logicalWidth: Int, logicalHeight: Int): PixelRenderResult {
        return render(
            request = WidgetRenderRequest(
                root = root,
                logicalWidth = logicalWidth,
                logicalHeight = logicalHeight,
            ),
        )
    }

    fun dispose() {
        elementTreeRenderer.dispose()
        buildRuntime.dispose()
        bufferPool.clear()
    }
}
