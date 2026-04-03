package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Widget

/**
 * 不包含子节点的 bridge widget。
 */
internal data class LegacyLeafWidget(
    override val key: Any? = null,
    private val factory: (BuildContext) -> BridgeRenderNode,
) : BridgeWidget {
    override val childWidgets: List<Widget> = emptyList()

    /**
     * 直接通过工厂生成 bridge render node。
     */
    override fun createBridgeNode(
        context: BuildContext,
        childNodes: BridgeNodeChildren,
    ): BridgeRenderNode {
        return factory(context)
    }
}
