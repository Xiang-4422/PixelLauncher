package com.purride.pixelui.internal

/**
 * 负责创建 bridge 渲染阶段的默认装配结果。
 */
internal object BridgeRenderAssemblyFactory {
    /**
     * 基于 element tree render request 和 bridge tree resolver 创建渲染装配结果。
     */
    fun create(
        request: ElementTreeRenderRequest,
        bridgeTreeResolver: BridgeTreeResolving,
    ): BridgeRenderAssembly {
        val bridgeRoot = bridgeTreeResolver.resolve(
            request = BridgeTreeResolveRequest(
                root = request.root,
            ),
        ) ?: error("当前 Widget 树没有生成可渲染的 bridge node。")
        return BridgeRenderAssembly(
            bridgeRoot = bridgeRoot,
            renderRequest = BridgeRenderRequest(
                root = bridgeRoot,
                logicalWidth = request.logicalWidth,
                logicalHeight = request.logicalHeight,
            ),
        )
    }
}
