package com.purride.pixelui.internal

/**
 * bridge 渲染运行时。
 *
 * 这层只负责把已经解析好的 bridge 渲染树交给 legacy renderer 输出像素结果，
 * 不再承担 retained element tree 的解析职责。
 */
internal class BridgeRenderRuntime(
    private val legacyTreeRenderer: LegacyTreeRenderer,
) : BridgeTreeRenderer {
    /**
     * 把 bridge 渲染请求转交给 legacy renderer。
     */
    override fun render(request: BridgeRenderRequest): PixelRenderResult {
        return legacyTreeRenderer.render(
            request = LegacyRenderRequest(
                root = request.root,
                logicalWidth = request.logicalWidth,
                logicalHeight = request.logicalHeight,
            ),
        )
    }
}
