package com.purride.pixelui.internal

/**
 * retained element tree 到 bridge 渲染树的解析协议。
 */
internal interface BridgeTreeResolving {
    /**
     * 把 retained element tree 解析成 bridge 根节点。
     */
    fun resolve(request: BridgeTreeResolveRequest): BridgeRenderNode?

    /**
     * 兼容旧的直接 root 调用写法。
     */
    fun resolve(root: Element?): BridgeRenderNode? = resolve(
        request = BridgeTreeResolveRequest(root = root),
    )
}
