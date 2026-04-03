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
    override fun render(
        root: BridgeRenderNode,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        return legacyTreeRenderer.render(
            root = root,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )
    }
}
