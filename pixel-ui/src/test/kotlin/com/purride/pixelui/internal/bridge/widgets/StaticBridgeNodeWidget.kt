package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Widget

/**
 * 把已经存在的 bridge render node 包装成 bridge widget。
 */
internal data class StaticBridgeNodeWidget(
    private val node: BridgeRenderNode,
) : BridgeWidget {
    override val key: Any?
        get() = node.key

    override val childWidgets: List<Widget> = emptyList()

    /**
     * 直接返回预先给定的 bridge render node。
     */
    override fun createBridgeNode(
        context: BuildContext,
        childNodes: BridgeNodeChildren,
    ): BridgeRenderNode {
        return node
    }
}
