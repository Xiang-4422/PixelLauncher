package com.purride.pixelui.internal

import com.purride.pixelui.internal.legacy.CustomDraw
import com.purride.pixelui.internal.legacy.PixelBoxNode
import com.purride.pixelui.internal.legacy.PixelButtonNode
import com.purride.pixelui.internal.legacy.PixelColumnNode
import com.purride.pixelui.internal.legacy.PixelListNode
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelPagerNode
import com.purride.pixelui.internal.legacy.PixelRowNode
import com.purride.pixelui.internal.legacy.PixelSingleChildScrollViewNode
import com.purride.pixelui.internal.legacy.PixelSurfaceNode
import com.purride.pixelui.internal.legacy.PixelTextFieldNode
import com.purride.pixelui.internal.legacy.PixelTextNode

/**
 * 负责把额外 modifier 合并回 legacy render node。
 */
internal object LegacyModifierApplier {
    /**
     * 返回合并 modifier 后的新节点。
     */
    fun apply(node: LegacyRenderNode, extra: PixelModifier): LegacyRenderNode {
        val merged = node.modifier.then(extra)
        return when (node) {
            is PixelTextNode -> node.copy(modifier = merged)
            is PixelSurfaceNode -> node.copy(modifier = merged)
            is PixelBoxNode -> node.copy(modifier = merged)
            is PixelRowNode -> node.copy(modifier = merged)
            is PixelColumnNode -> node.copy(modifier = merged)
            is PixelPagerNode -> node.copy(modifier = merged)
            is PixelListNode -> node.copy(modifier = merged)
            is PixelSingleChildScrollViewNode -> node.copy(modifier = merged)
            is PixelTextFieldNode -> node.copy(modifier = merged)
            is PixelButtonNode -> node.copy(modifier = merged)
            is CustomDraw -> node.copy(modifier = merged)
            else -> node
        }
    }
}
