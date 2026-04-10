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
    /**
     * 把 retained element tree 解析成 bridge tree，再交给 bridge renderer。
     */
    override fun render(request: ElementTreeRenderRequest): PixelRenderResult {
        val assembly = BridgeRenderAssemblyFactory.create(
            request = request,
            bridgeTreeResolver = bridgeTreeResolver,
        )
        return assembly.renderWith(renderer = bridgeTreeRenderer)
    }
}
