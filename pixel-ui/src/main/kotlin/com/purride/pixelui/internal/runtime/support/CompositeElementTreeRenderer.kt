package com.purride.pixelui.internal

/**
 * 同时持有新 pipeline 与旧 fallback 的 element tree renderer。
 *
 * 第一版固定策略是整树级分流：
 * - 整棵树都能被 pipeline 识别时，走新渲染管线
 * - 否则整棵树回退到现有 bridge + legacy renderer
 */
internal class CompositeElementTreeRenderer(
    private val pipelineRenderer: PipelineElementTreeRenderer,
    private val fallbackRenderer: ElementTreeRenderer,
) : ElementTreeRenderer {
    /**
     * 优先尝试新 pipeline；失败时整树回退到旧渲染链。
     */
    override fun render(request: ElementTreeRenderRequest): PixelRenderResult {
        return if (pipelineRenderer.canRender(request)) {
            pipelineRenderer.render(request)
        } else {
            fallbackRenderer.render(request)
        }
    }
}
