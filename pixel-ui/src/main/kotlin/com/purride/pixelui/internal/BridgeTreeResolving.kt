package com.purride.pixelui.internal

/**
 * retained element tree 到 bridge 渲染树的解析协议。
 */
internal interface BridgeTreeResolving {
    fun resolve(root: Element?): BridgeRenderNode?
}
