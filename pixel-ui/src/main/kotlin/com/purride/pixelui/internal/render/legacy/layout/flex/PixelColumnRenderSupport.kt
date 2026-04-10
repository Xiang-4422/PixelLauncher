package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.legacy.PixelColumnNode
import com.purride.pixelui.internal.legacy.PixelCrossAxisAlignment
import kotlin.math.max

/**
 * 负责 legacy `Column` 节点的布局与渲染。
 */
internal class PixelColumnRenderSupport(
    private val flexLayoutSupport: PixelFlexLayoutSupport,
    private val alignmentLayoutSupport: PixelAlignmentLayoutSupport,
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
     * 渲染 column 节点。
     */
    fun render(
        node: PixelColumnNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        val childSizes = flexLayoutSupport.measureColumnChildren(node, constraints)
        val contentHeight = childSizes.sumOf { it.height } + (max(0, node.children.size - 1) * node.spacing)
        val verticalMainAxis = alignmentLayoutSupport.mainAxisArrangement(
            containerStart = bounds.top,
            containerExtent = bounds.height,
            contentExtent = contentHeight,
            spacing = node.spacing,
            childCount = childSizes.size,
            alignment = node.mainAxisAlignment,
        )
        var cursorY = verticalMainAxis.start
        node.children.zip(childSizes).forEach { (child, childSize) ->
            val childWidth = if (node.crossAxisAlignment == PixelCrossAxisAlignment.STRETCH) bounds.width else childSize.width
            val childBounds = PixelRect(
                left = alignmentLayoutSupport.crossAxisStart(
                    containerStart = bounds.left,
                    containerExtent = bounds.width,
                    childExtent = childWidth,
                    alignment = node.crossAxisAlignment,
                ),
                top = cursorY,
                width = childWidth,
                height = childSize.height,
            )
            renderNode(
                child,
                childBounds,
                PixelConstraints(
                    maxWidth = bounds.width,
                    maxHeight = childSize.height,
                ),
                buffer,
                clickTargets,
                pagerTargets,
                listTargets,
                textInputTargets,
            )
            cursorY += childSize.height + verticalMainAxis.spacingAfterChild
        }
    }
}
