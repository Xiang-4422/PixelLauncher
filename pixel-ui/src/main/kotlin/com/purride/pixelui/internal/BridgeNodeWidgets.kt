package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.CustomDraw
import com.purride.pixelui.internal.legacy.PixelBoxNode
import com.purride.pixelui.internal.legacy.PixelButtonNode
import com.purride.pixelui.internal.legacy.PixelColumnNode
import com.purride.pixelui.internal.legacy.PixelListNode
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelNode
import com.purride.pixelui.internal.legacy.PixelPagerNode
import com.purride.pixelui.internal.legacy.PixelRowNode
import com.purride.pixelui.internal.legacy.PixelSingleChildScrollViewNode
import com.purride.pixelui.internal.legacy.PixelSurfaceNode
import com.purride.pixelui.internal.legacy.PixelTextFieldNode
import com.purride.pixelui.internal.legacy.PixelTextNode

internal fun adaptBridgeWidget(widget: Widget): BridgeWidget? {
    return when (widget) {
        is BridgeWidget -> widget
        is PixelNode -> StaticBridgeNodeWidget(widget)
        else -> null
    }
}

private data class StaticBridgeNodeWidget(
    private val node: BridgeRenderNode,
) : BridgeWidget {
    override val key: Any?
        get() = node.key

    override val childWidgets: List<Widget> = emptyList()

    override fun createBridgeNode(
        context: BuildContext,
        childNodes: List<BridgeRenderNode>,
    ): BridgeRenderNode {
        return node
    }
}

internal fun LegacyRenderNode.withExtraModifier(extra: PixelModifier): LegacyRenderNode {
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
