package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.FlexFit
import com.purride.pixelui.PixelThemeData
import com.purride.pixelui.Theme
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelFlexFit
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.weight

internal interface BridgeWidget : Widget {
    val childWidgets: List<Widget>

    fun createBridgeNode(
        context: BuildContext,
        childNodes: List<BridgeRenderNode>,
    ): BridgeRenderNode
}

internal fun BuildContext.resolveTheme(explicit: PixelThemeData?): PixelThemeData {
    return explicit ?: Theme.maybeOf(this) ?: PixelThemeData.Default
}

internal data class LegacyLeafWidget(
    override val key: Any? = null,
    private val factory: (BuildContext) -> BridgeRenderNode,
) : BridgeWidget {
    override val childWidgets: List<Widget> = emptyList()

    override fun createBridgeNode(
        context: BuildContext,
        childNodes: List<BridgeRenderNode>,
    ): BridgeRenderNode {
        return factory(context)
    }
}

internal data class LegacySingleChildWidget(
    override val key: Any? = null,
    val child: Widget,
    private val factory: (BuildContext, BridgeRenderNode) -> BridgeRenderNode,
) : BridgeWidget {
    override val childWidgets: List<Widget>
        get() = listOf(child)

    override fun createBridgeNode(
        context: BuildContext,
        childNodes: List<BridgeRenderNode>,
    ): BridgeRenderNode {
        return factory(context, childNodes.single())
    }
}

internal data class LegacyMultiChildWidget(
    override val key: Any? = null,
    val children: List<Widget>,
    private val factory: (BuildContext, List<BridgeRenderNode>) -> BridgeRenderNode,
) : BridgeWidget {
    override val childWidgets: List<Widget>
        get() = children

    override fun createBridgeNode(
        context: BuildContext,
        childNodes: List<BridgeRenderNode>,
    ): BridgeRenderNode {
        return factory(context, childNodes)
    }
}

internal data class FlexWrapperWidget(
    override val key: Any? = null,
    val child: Widget,
    val flex: Int,
    val fit: FlexFit,
) : BridgeWidget {
    override val childWidgets: List<Widget>
        get() = listOf(child)

    override fun createBridgeNode(
        context: BuildContext,
        childNodes: List<BridgeRenderNode>,
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
