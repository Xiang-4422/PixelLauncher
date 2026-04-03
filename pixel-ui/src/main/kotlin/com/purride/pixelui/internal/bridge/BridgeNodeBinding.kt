package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext

/**
 * 管理 bridge widget 到 bridge render node 的解析协作。
 *
 * 这层负责：
 * 1. 在解析 node 前清理当前 bridge element 的 listenable 依赖
 * 2. 调用 bridge widget 生成最终的 bridge render node
 */
internal class BridgeNodeBinding(
    private val host: BridgeAdapterElement,
) {
    /**
     * 解析当前 bridge element 对应的 render node。
     */
    fun resolve(
        widget: BridgeWidget,
        context: BuildContext,
        childNodes: BridgeNodeChildren,
    ): BridgeRenderNode {
        host.owner.clearListenableDependencies(host)
        return widget.createBridgeNode(
            context = context,
            childNodes = childNodes,
        )
    }
}
