package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.legacy.PixelBoxNode
import com.purride.pixelui.internal.legacy.PixelColumnNode
import com.purride.pixelui.internal.legacy.PixelCrossAxisAlignment
import com.purride.pixelui.internal.legacy.PixelPositionedNode
import com.purride.pixelui.internal.legacy.PixelRowNode
import com.purride.pixelui.internal.legacy.PixelSurfaceNode
import kotlin.math.max

/**
 * 负责 legacy 容器类节点的布局测量与渲染调度。
 */
internal class PixelLayoutRenderSupport(
    callbacks: LegacyRenderCallbacks,
) {
    private val measureNode = callbacks.measureNode
    private val renderNode = callbacks.renderNode
    private val flexLayoutSupport = PixelFlexLayoutSupport(measureNode = measureNode)
    private val positionedLayoutSupport = PixelPositionedLayoutSupport()
    private val alignmentLayoutSupport = PixelAlignmentLayoutSupport()
    private val surfaceRenderSupport = PixelSurfaceRenderSupport(
        measureNode = measureNode,
        renderNode = renderNode,
        alignmentLayoutSupport = alignmentLayoutSupport,
    )
    private val stackRenderSupport = PixelStackRenderSupport(
        measureNode = measureNode,
        renderNode = renderNode,
        alignmentLayoutSupport = alignmentLayoutSupport,
        positionedLayoutSupport = positionedLayoutSupport,
    )

    /**
     * 渲染 surface 节点及其内层对齐子项。
     */
    fun renderSurface(
        node: PixelSurfaceNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        surfaceRenderSupport.render(
            node = node,
            bounds = bounds,
            constraints = constraints,
            buffer = buffer,
            clickTargets = clickTargets,
            pagerTargets = pagerTargets,
            listTargets = listTargets,
            textInputTargets = textInputTargets,
        )
    }

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
        stackRenderSupport.renderBox(
            node = node,
            bounds = bounds,
            constraints = constraints,
            buffer = buffer,
            clickTargets = clickTargets,
            pagerTargets = pagerTargets,
            listTargets = listTargets,
            textInputTargets = textInputTargets,
        )
    }

    /**
     * 渲染 row 节点。
     */
    fun renderRow(
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

    /**
     * 渲染 column 节点。
     */
    fun renderColumn(
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

    /**
     * 暴露 positioned 最大宽度计算，供 measure support 复用。
     */
    fun measurePositionedMaxWidth(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
    ): Int = positionedLayoutSupport.maxWidth(node, constraints)

    /**
     * 暴露 positioned 最大高度计算，供 measure support 复用。
     */
    fun measurePositionedMaxHeight(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
    ): Int = positionedLayoutSupport.maxHeight(node, constraints)

    /**
     * 暴露 positioned 最终宽度计算，供 measure support 复用。
     */
    fun measurePositionedWidth(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
        childSize: PixelSize,
    ): Int = positionedLayoutSupport.width(node, constraints, childSize)

    /**
     * 暴露 positioned 最终高度计算，供 measure support 复用。
     */
    fun measurePositionedHeight(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
        childSize: PixelSize,
    ): Int = positionedLayoutSupport.height(node, constraints, childSize)

    /**
     * 暴露 row 子项测量，供 measure support 复用。
     */
    fun measureRowChildrenForLayout(
        node: PixelRowNode,
        constraints: PixelConstraints,
    ): List<PixelSize> = flexLayoutSupport.measureRowChildren(node, constraints)

    /**
     * 暴露 column 子项测量，供 measure support 复用。
     */
    fun measureColumnChildrenForLayout(
        node: PixelColumnNode,
        constraints: PixelConstraints,
    ): List<PixelSize> = flexLayoutSupport.measureColumnChildren(node, constraints)

    /**
     * 暴露子项权重解析，供 measure support 复用。
     */
    fun childWeightOf(node: LegacyRenderNode): Float = flexLayoutSupport.childWeight(node)

}
