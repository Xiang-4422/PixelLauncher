package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.legacy.PixelBoxNode
import com.purride.pixelui.internal.legacy.PixelPositionedNode

/**
 * 负责 legacy `Box/Stack` 及其 `Positioned` 子项的渲染。
 */
internal class PixelStackRenderSupport(
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
    private val alignmentLayoutSupport: PixelAlignmentLayoutSupport,
    private val positionedLayoutSupport: PixelPositionedLayoutSupport,
) {
    /**
     * 渲染 box/stack 节点。
     */
    fun renderBox(
        node: PixelBoxNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        node.children.forEach { child ->
            if (child is PixelPositionedNode) {
                renderPositionedChild(
                    node = child,
                    outerBounds = bounds,
                    outerConstraints = constraints,
                    buffer = buffer,
                    clickTargets = clickTargets,
                    pagerTargets = pagerTargets,
                    listTargets = listTargets,
                    textInputTargets = textInputTargets,
                )
            } else {
                val childSize = measureNode(child, constraints)
                val childBounds = alignmentLayoutSupport.alignedBounds(
                    outerBounds = bounds,
                    childSize = childSize,
                    alignment = node.alignment,
                )
                renderNode(
                    child,
                    childBounds,
                    constraints,
                    buffer,
                    clickTargets,
                    pagerTargets,
                    listTargets,
                    textInputTargets,
                )
            }
        }
    }

    /**
     * 渲染 positioned 子节点。
     */
    private fun renderPositionedChild(
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
