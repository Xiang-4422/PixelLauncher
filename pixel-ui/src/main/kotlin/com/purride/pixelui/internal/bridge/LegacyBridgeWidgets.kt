package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.FlexFit
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelFlexFit
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.weight

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

/**
 * 恰好包含一个子节点的 bridge widget。
 */
internal data class LegacySingleChildWidget(
    override val key: Any? = null,
    val child: Widget,
    private val factory: (BuildContext, BridgeRenderNode) -> BridgeRenderNode,
) : BridgeWidget {
    override val childWidgets: List<Widget>
        get() = listOf(child)

    /**
     * 使用唯一子节点生成 bridge render node。
     */
    override fun createBridgeNode(
        context: BuildContext,
        childNodes: BridgeNodeChildren,
    ): BridgeRenderNode {
        return factory(context, childNodes.single())
    }
}

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

/**
 * 把弹性布局语义折叠进 legacy modifier 的 bridge widget。
 */
internal data class FlexWrapperWidget(
    override val key: Any? = null,
    val child: Widget,
    val flex: Int,
    val fit: FlexFit,
) : BridgeWidget {
    override val childWidgets: List<Widget>
        get() = listOf(child)

    /**
     * 给唯一子节点附加 flex modifier。
     */
    override fun createBridgeNode(
        context: BuildContext,
        childNodes: BridgeNodeChildren,
    ): BridgeRenderNode {
        return childNodes.single().withExtraModifier(
            PixelModifier.Empty.weight(
                weight = flex.coerceAtLeast(1).toFloat(),
                fit = when (fit) {
                    FlexFit.TIGHT -> PixelFlexFit.TIGHT
                    FlexFit.LOOSE -> PixelFlexFit.LOOSE
                },
            ),
        )
    }
}
