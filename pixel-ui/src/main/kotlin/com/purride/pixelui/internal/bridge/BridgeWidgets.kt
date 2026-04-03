package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.FlexFit
import com.purride.pixelui.PixelThemeData
import com.purride.pixelui.Theme
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelFlexFit
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.weight

/**
 * 可被 bridge element 解析成 bridge render node 的 widget 协议。
 */
internal interface BridgeWidget : Widget {
    /**
     * 当前 bridge widget 暴露给 retained build tree 的子 widget 列表。
     */
    val childWidgets: List<Widget>

    /**
     * 用当前上下文和已解析好的子节点构建 bridge render node。
     */
    fun createBridgeNode(
        context: BuildContext,
        childNodes: BridgeNodeChildren,
    ): BridgeRenderNode
}

/**
 * 在 build 阶段解析当前上下文可见主题。
 */
internal fun BuildContext.resolveTheme(explicit: PixelThemeData?): PixelThemeData {
    return explicit ?: Theme.maybeOf(this) ?: PixelThemeData.Default
}

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
