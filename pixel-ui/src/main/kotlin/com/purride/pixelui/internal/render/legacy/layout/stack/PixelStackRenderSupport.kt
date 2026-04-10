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
    private val positionedRenderSupport: PixelPositionedRenderSupport,
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
                positionedRenderSupport.render(
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
}
