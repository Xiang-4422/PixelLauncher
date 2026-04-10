package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.legacy.PixelSurfaceNode

/**
 * 负责 legacy `Surface` 节点的背景、边框和内层对齐渲染。
 */
internal class PixelSurfaceRenderSupport(
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
) {
    /**
     * 渲染 surface 节点及其内层对齐子项。
     */
    fun render(
        node: PixelSurfaceNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        buffer.fillRect(
            left = bounds.left,
            top = bounds.top,
            rectWidth = bounds.width,
            rectHeight = bounds.height,
            value = node.fillTone.value,
        )
        node.borderTone?.let { tone ->
            buffer.drawRect(
                left = bounds.left,
                top = bounds.top,
                rectWidth = bounds.width,
                rectHeight = bounds.height,
                value = tone.value,
            )
        }

        val child = node.child ?: return
        val childConstraints = constraints.shrink(
            paddingLeft = node.padding,
            paddingTop = node.padding,
            paddingRight = node.padding,
            paddingBottom = node.padding,
        )
        val innerBounds = bounds.inset(
            paddingLeft = node.padding,
            paddingTop = node.padding,
            paddingRight = node.padding,
            paddingBottom = node.padding,
        )
        val childSize = measureNode(child, childConstraints)
        val childBounds = alignmentLayoutSupport.alignedBounds(
            outerBounds = innerBounds,
            childSize = childSize,
            alignment = node.alignment,
        )
        renderNode(
            child,
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
