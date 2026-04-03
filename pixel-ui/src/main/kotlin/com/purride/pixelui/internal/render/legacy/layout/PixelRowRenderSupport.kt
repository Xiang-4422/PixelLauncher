package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.legacy.PixelCrossAxisAlignment
import com.purride.pixelui.internal.legacy.PixelRowNode
import kotlin.math.max

/**
 * 负责 legacy `Row` 节点的布局与渲染。
 */
internal class PixelRowRenderSupport(
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
     * 渲染 row 节点。
     */
    fun render(
        node: PixelRowNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        val childSizes = flexLayoutSupport.measureRowChildren(node, constraints)
        val contentWidth = childSizes.sumOf { it.width } + (max(0, node.children.size - 1) * node.spacing)
        val horizontalMainAxis = alignmentLayoutSupport.mainAxisArrangement(
            containerStart = bounds.left,
            containerExtent = bounds.width,
            contentExtent = contentWidth,
            spacing = node.spacing,
            childCount = childSizes.size,
            alignment = node.mainAxisAlignment,
        )
        var cursorX = horizontalMainAxis.start
        node.children.zip(childSizes).forEach { (child, childSize) ->
            val childHeight = if (node.crossAxisAlignment == PixelCrossAxisAlignment.STRETCH) bounds.height else childSize.height
            val childBounds = PixelRect(
                left = cursorX,
                top = alignmentLayoutSupport.crossAxisStart(
                    containerStart = bounds.top,
                    containerExtent = bounds.height,
                    childExtent = childHeight,
                    alignment = node.crossAxisAlignment,
                ),
                width = childSize.width,
                height = childHeight,
            )
            renderNode(
                child,
                childBounds,
                PixelConstraints(
                    maxWidth = childSize.width,
                    maxHeight = bounds.height,
                ),
                buffer,
                clickTargets,
                pagerTargets,
                listTargets,
                textInputTargets,
            )
            cursorX += childSize.width + horizontalMainAxis.spacingAfterChild
        }
    }
}
