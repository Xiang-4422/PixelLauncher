package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBufferPool
import com.purride.pixelcore.PixelColorMode
import com.purride.pixelui.Widget

/**
 * pixel-engine UI layer 对宿主层暴露的内部运行时。
 *
 * 直接持有 retained build runtime 和 pipeline renderer，渲染一帧的调用链：
 *
 * ```
 * PixelUiRuntime.render
 *   -> ElementTreeBuildRuntime.resolveElementTree
 *   -> ElementTreeRenderer.render
 * ```
 *
 * 文本栅格器通过 [com.purride.pixelui.DefaultTextRasterizer] InheritedWidget
 * 在 widget 树里注入（宿主层 `HostRootWidget` 自动包装），runtime 自身不持有
 * 文本相关状态，因此切换字体不需要重建 runtime。
 */
internal class PixelUiRuntime(
    onVisualUpdate: () -> Unit = { },
) {
    /**
     * 单 runtime 共享一个 PixelBuffer 池，避免每帧分配 main buffer 和各
     * RenderObject 的 scratch buffer 给 GC。PipelineOwner 自己持有上一帧
     * 引用并在下一帧脏的时候自动归还。
     */
    private val bufferPool: PixelBufferPool = PixelBufferPool()
    private val elementTreeRenderer: ElementTreeRenderer = PipelineElementTreeRenderer(bufferPool = bufferPool)
    private val buildRuntime: ElementTreeBuildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
        onVisualUpdate = onVisualUpdate,
        widgetAdapter = UnsupportedWidgetAdapter,
    )

    /**
     * 渲染显式的 Widget runtime 请求。
     */
    fun render(request: WidgetRenderRequest): PixelRenderResult {
        val root = buildRuntime.resolveElementTree(request.root)
        return elementTreeRenderer.render(
            request = ElementTreeRenderRequest(
                root = root,
                logicalWidth = request.logicalWidth,
                logicalHeight = request.logicalHeight,
                colorMode = request.colorMode,
            ),
        )
    }

    /**
     * 渲染一棵 Widget 根树。
     */
    fun render(
        root: Widget,
        logicalWidth: Int,
        logicalHeight: Int,
        colorMode: PixelColorMode = PixelColorMode.Mono,
    ): PixelRenderResult {
        return render(
            request = WidgetRenderRequest(
                root = root,
                logicalWidth = logicalWidth,
                logicalHeight = logicalHeight,
                colorMode = colorMode,
            ),
        )
    }

    /**
     * 释放内部 retained build runtime 和 buffer 池。
     */
    fun dispose() {
        elementTreeRenderer.dispose()
        buildRuntime.dispose()
        bufferPool.clear()
    }
}
