package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
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
    private val elementTreeRenderer: ElementTreeRenderer = PipelineElementTreeRenderer()
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
     * 释放内部 retained build runtime。
     */
    fun dispose() {
        buildRuntime.dispose()
    }
}
