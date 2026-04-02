package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.FlexFit
import com.purride.pixelui.PixelThemeData
import com.purride.pixelui.Theme
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.CustomDraw
import com.purride.pixelui.internal.legacy.PixelBoxNode
import com.purride.pixelui.internal.legacy.PixelButtonNode
import com.purride.pixelui.internal.legacy.PixelColumnNode
import com.purride.pixelui.internal.legacy.PixelFlexFit
import com.purride.pixelui.internal.legacy.PixelListNode
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelNode
import com.purride.pixelui.internal.legacy.PixelPagerNode
import com.purride.pixelui.internal.legacy.PixelRowNode
import com.purride.pixelui.internal.legacy.PixelSingleChildScrollViewNode
import com.purride.pixelui.internal.legacy.PixelSurfaceNode
import com.purride.pixelui.internal.legacy.PixelTextFieldNode
import com.purride.pixelui.internal.legacy.PixelTextNode
import com.purride.pixelui.internal.legacy.weight

internal interface LegacyNodeWidget : Widget {
    val childWidgets: List<Widget>

    fun createLegacyNode(
        context: BuildContext,
        childNodes: List<PixelNode>,
    ): PixelNode
}

internal fun BuildContext.resolveTheme(explicit: PixelThemeData?): PixelThemeData {
    return explicit ?: Theme.maybeOf(this) ?: PixelThemeData.Default
}

internal fun PixelNode.withExtraModifier(extra: PixelModifier): PixelNode {
    val merged = modifier.then(extra)
    return when (this) {
        is PixelTextNode -> copy(modifier = merged)
        is PixelSurfaceNode -> copy(modifier = merged)
        is PixelBoxNode -> copy(modifier = merged)
        is PixelRowNode -> copy(modifier = merged)
        is PixelColumnNode -> copy(modifier = merged)
        is PixelPagerNode -> copy(modifier = merged)
        is PixelListNode -> copy(modifier = merged)
        is PixelSingleChildScrollViewNode -> copy(modifier = merged)
        is PixelTextFieldNode -> copy(modifier = merged)
        is PixelButtonNode -> copy(modifier = merged)
        is CustomDraw -> copy(modifier = merged)
        else -> this
    }
}

internal data class LegacyLeafWidget(
    override val key: Any? = null,
    private val factory: (BuildContext) -> PixelNode,
) : LegacyNodeWidget {
    override val childWidgets: List<Widget> = emptyList()

    override fun createLegacyNode(
        context: BuildContext,
        childNodes: List<PixelNode>,
    ): PixelNode {
        return factory(context)
    }
}

internal data class LegacySingleChildWidget(
    override val key: Any? = null,
    val child: Widget,
    private val factory: (BuildContext, PixelNode) -> PixelNode,
) : LegacyNodeWidget {
    override val childWidgets: List<Widget>
        get() = listOf(child)

    override fun createLegacyNode(
        context: BuildContext,
        childNodes: List<PixelNode>,
    ): PixelNode {
        return factory(context, childNodes.single())
    }
}

internal data class LegacyMultiChildWidget(
    override val key: Any? = null,
    val children: List<Widget>,
    private val factory: (BuildContext, List<PixelNode>) -> PixelNode,
) : LegacyNodeWidget {
    override val childWidgets: List<Widget>
        get() = children

    override fun createLegacyNode(
        context: BuildContext,
        childNodes: List<PixelNode>,
    ): PixelNode {
        return factory(context, childNodes)
    }
}

internal data class FlexWrapperWidget(
    override val key: Any? = null,
    val child: Widget,
    val flex: Int,
    val fit: FlexFit,
) : LegacyNodeWidget {
    override val childWidgets: List<Widget>
        get() = listOf(child)

    override fun createLegacyNode(
        context: BuildContext,
        childNodes: List<PixelNode>,
    ): PixelNode {
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
