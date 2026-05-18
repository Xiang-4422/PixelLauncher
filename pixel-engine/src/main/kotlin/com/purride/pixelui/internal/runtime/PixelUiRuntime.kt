package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBufferPool
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.Widget

/**
 * pixel-engine UI layer 对宿主层暴露的内部运行时。
 *
 * 它直接持有 retained build runtime 和新 pipeline renderer，渲染一帧的调用链为：
 *
 * ```
 * PixelUiRuntime.render
 *   -> ElementTreeBuildRuntime.resolveElementTree
 *   -> ElementTreeRenderer.render
 * ```
 *
 * 不再经过任何 Assembly / Factory 透传层。
 *
 * 当前 [textRasterizer] 参数被保留以兼容宿主侧调用约定，但默认 RenderText
 * 链路目前仍在 widget 层硬编码 `PixelBitmapFont.Default`；后续阶段会把该入参
 * 真正路由到默认 rasterizer。
 */
internal class PixelUiRuntime(
    @Suppress("UNUSED_PARAMETER") textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    onVisualUpdate: () -> Unit = { },
) {
    /**
     * 单 runtime 共享一个 PixelBuffer 池，避免每帧分配 main buffer 和各
     * RenderObject 的 scratch buffer 给 GC。宿主在丢弃一帧渲染结果时
     * 需要通过 [releaseRenderResult] 显式把 main buffer 还回。
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
     * 把上一帧渲染结果的 main buffer 归还到池。宿主在用新一帧 result
     * 覆盖 lastRenderResult 之前调用，可以让池真正实现循环复用。
     *
     * 传入 null 时是 no-op，方便宿主写 `lastRenderResult?.let(runtime::releaseRenderResult)`。
     */
    fun releaseRenderResult(result: PixelRenderResult?) {
        result ?: return
        bufferPool.release(result.buffer)
    }

    /**
     * 释放内部 retained build runtime 和 buffer 池。
     */
    fun dispose() {
        buildRuntime.dispose()
        bufferPool.clear()
    }
}
