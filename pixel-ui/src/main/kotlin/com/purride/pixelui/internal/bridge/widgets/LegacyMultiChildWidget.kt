package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Widget

/**
 * 包含多个子节点的 bridge widget。
 */
internal data class LegacyMultiChildWidget(
    override val key: Any? = null,
    val children: List<Widget>,
    private val factory: (BuildContext, List<BridgeRenderNode>) -> BridgeRenderNode,
) : BridgeWidget {
    override val childWidgets: List<Widget>
        get() = children

    /**
     * 使用所有子节点生成 bridge render node。
     */
    override fun createBridgeNode(
        context: BuildContext,
        childNodes: BridgeNodeChildren,
    ): BridgeRenderNode {
        return factory(context, childNodes.asList())
    }
}
