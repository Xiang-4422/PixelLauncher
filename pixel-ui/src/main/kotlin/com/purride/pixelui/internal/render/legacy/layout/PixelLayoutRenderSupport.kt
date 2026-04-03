package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.legacy.PixelBoxNode
import com.purride.pixelui.internal.legacy.PixelColumnNode
import com.purride.pixelui.internal.legacy.PixelPositionedNode
import com.purride.pixelui.internal.legacy.PixelRowNode
import com.purride.pixelui.internal.legacy.PixelSurfaceNode

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
    private val rowRenderSupport = PixelRowRenderSupport(
        flexLayoutSupport = flexLayoutSupport,
        alignmentLayoutSupport = alignmentLayoutSupport,
        renderNode = renderNode,
    )
    private val columnRenderSupport = PixelColumnRenderSupport(
        flexLayoutSupport = flexLayoutSupport,
        alignmentLayoutSupport = alignmentLayoutSupport,
        renderNode = renderNode,
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
        rowRenderSupport.render(
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
        columnRenderSupport.render(
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
