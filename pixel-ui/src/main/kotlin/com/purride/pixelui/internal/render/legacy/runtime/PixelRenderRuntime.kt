package com.purride.pixelui.internal

/**
 * 纯 legacy 渲染 façade。
 *
 * 这层只负责接收 legacy render request，并把具体渲染工作转交给
 * `LegacyRenderSupport`。
 */
internal class PixelRenderRuntime(
    private val renderSupport: LegacyRenderSupport,
) : LegacyTreeRenderer {
    /**
     * 渲染单次 legacy 渲染请求。
     */
    override fun render(request: LegacyRenderRequest): PixelRenderResult {
        return renderSupport.renderRoot(
            root = request.root,
            logicalWidth = request.logicalWidth,
            logicalHeight = request.logicalHeight,
        )
    }
}
