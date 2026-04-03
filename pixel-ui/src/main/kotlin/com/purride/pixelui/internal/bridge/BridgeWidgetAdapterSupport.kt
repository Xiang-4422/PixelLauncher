package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelNode

/**
 * bridge widget 适配阶段用到的辅助逻辑。
 */
internal fun adaptBridgeWidget(widget: Widget): BridgeWidget? {
    return when (widget) {
        is BridgeWidget -> widget
        is PixelNode -> StaticBridgeNodeWidget(widget)
        else -> null
    }
}

/**
 * 把静态 legacy 节点包装成 bridge widget。
 */
private data class StaticBridgeNodeWidget(
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
