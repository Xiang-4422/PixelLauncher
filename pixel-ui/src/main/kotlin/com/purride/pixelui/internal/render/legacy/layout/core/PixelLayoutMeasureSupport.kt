package com.purride.pixelui.internal

import com.purride.pixelui.internal.legacy.PixelColumnNode
import com.purride.pixelui.internal.legacy.PixelPositionedNode
import com.purride.pixelui.internal.legacy.PixelRowNode

/**
 * 负责 legacy 布局节点对测量阶段暴露的辅助计算。
 */
internal class PixelLayoutMeasureSupport(
    private val flexLayoutSupport: PixelFlexLayoutSupport,
    private val positionedLayoutSupport: PixelPositionedLayoutSupport,
) {
    /**
     * 计算 positioned 子项的最大可用宽度。
     */
    fun measurePositionedMaxWidth(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
    ): Int = positionedLayoutSupport.maxWidth(node, constraints)

    /**
     * 计算 positioned 子项的最大可用高度。
     */
    fun measurePositionedMaxHeight(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
    ): Int = positionedLayoutSupport.maxHeight(node, constraints)

    /**
     * 计算 positioned 子项的最终宽度。
     */
    fun measurePositionedWidth(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
        childSize: PixelSize,
    ): Int = positionedLayoutSupport.width(node, constraints, childSize)

    /**
     * 计算 positioned 子项的最终高度。
     */
    fun measurePositionedHeight(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
        childSize: PixelSize,
    ): Int = positionedLayoutSupport.height(node, constraints, childSize)

    /**
     * 测量 row 子项尺寸，供测量阶段复用。
     */
    fun measureRowChildren(
        node: PixelRowNode,
        constraints: PixelConstraints,
    ): List<PixelSize> = flexLayoutSupport.measureRowChildren(node, constraints)

    /**
     * 测量 column 子项尺寸，供测量阶段复用。
     */
    fun measureColumnChildren(
        node: PixelColumnNode,
        constraints: PixelConstraints,
    ): List<PixelSize> = flexLayoutSupport.measureColumnChildren(node, constraints)

    /**
     * 解析子项权重，供测量阶段判断主轴是否需要吃满。
     */
    fun childWeightOf(node: LegacyRenderNode): Float = flexLayoutSupport.childWeight(node)
}
