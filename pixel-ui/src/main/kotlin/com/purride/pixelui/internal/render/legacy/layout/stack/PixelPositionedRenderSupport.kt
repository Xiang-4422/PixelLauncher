package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.legacy.PixelPositionedNode

/**
 * 负责 legacy `Positioned` 子节点的约束解析、定位和渲染。
 */
internal class PixelPositionedRenderSupport(
    private val measureNode: (LegacyRenderNode, PixelConstraints) -> PixelSize,
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
    private val positionedLayoutSupport: PixelPositionedLayoutSupport,
) {
    /**
     * 在给定外层 bounds 中渲染一个 positioned 子节点。
     */
    fun render(
        node: PixelPositionedNode,
        outerBounds: PixelRect,
        outerConstraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        val childConstraints = PixelConstraints(
            maxWidth = positionedLayoutSupport.maxWidth(node, outerConstraints),
            maxHeight = positionedLayoutSupport.maxHeight(node, outerConstraints),
        )
        val childSize = measureNode(node.child, childConstraints)
        val width = positionedLayoutSupport.width(node, outerConstraints, childSize).coerceAtLeast(0)
        val height = positionedLayoutSupport.height(node, outerConstraints, childSize).coerceAtLeast(0)
        val left = when {
            node.left != null -> outerBounds.left + node.left
            node.right != null -> outerBounds.right - node.right - width
            else -> outerBounds.left
        }
        val top = when {
            node.top != null -> outerBounds.top + node.top
            node.bottom != null -> outerBounds.bottom - node.bottom - height
            else -> outerBounds.top
        }
        val childBounds = PixelRect(
            left = left,
            top = top,
            width = width.coerceAtMost(outerBounds.right - left),
            height = height.coerceAtMost(outerBounds.bottom - top),
        )
        renderNode(
            node.child,
            childBounds,
            childConstraints,
            buffer,
            clickTargets,
            pagerTargets,
            listTargets,
            textInputTargets,
        )
    }
}
