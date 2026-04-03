package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.legacy.CustomDraw
import com.purride.pixelui.internal.legacy.PixelButtonNode
import com.purride.pixelui.internal.legacy.toSurfaceNode

/**
 * 负责 legacy 节点里少量特殊分支的渲染分发。
 */
internal class PixelNodeSpecialRenderDispatch(
    private val renderNode: (
        node: LegacyRenderNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) -> Unit,
) {
    /**
     * 尝试处理需要特殊分支渲染的节点。
     *
     * 返回 `true` 表示已经消费当前节点。
     */
    fun renderIfHandled(
        node: LegacyRenderNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ): Boolean {
        return when (node) {
            is PixelButtonNode -> {
                renderNode(
                    node.toSurfaceNode(),
                    bounds,
                    constraints,
                    buffer,
                    clickTargets,
                    pagerTargets,
                    listTargets,
                    textInputTargets,
                )
                true
            }

            is CustomDraw -> true
            else -> false
        }
    }
}
