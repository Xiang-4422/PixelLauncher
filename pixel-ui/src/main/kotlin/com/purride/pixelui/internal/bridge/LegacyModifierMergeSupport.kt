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
 * 给 legacy render node 追加 modifier 的辅助逻辑。
 */
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
