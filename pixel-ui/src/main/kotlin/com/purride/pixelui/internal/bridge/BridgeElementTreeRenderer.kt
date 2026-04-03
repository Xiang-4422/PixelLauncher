package com.purride.pixelui.internal

/**
 * bridge 层对 retained element tree 的渲染入口。
 *
 * 这层负责把 retained element tree 解析成 bridge 渲染树，再交给 bridge runtime
 * 输出最终像素结果。
 */
internal class BridgeElementTreeRenderer(
    private val bridgeTreeResolver: BridgeTreeResolving,
    private val bridgeTreeRenderer: BridgeTreeRenderer,
) : ElementTreeRenderer {
    override fun render(
        root: Element?,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        val bridgeRoot = bridgeTreeResolver.resolve(root)
            ?: error("当前 Widget 树没有生成可渲染的 bridge node。")
        return bridgeTreeRenderer.render(
            root = bridgeRoot,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )
    }
}
