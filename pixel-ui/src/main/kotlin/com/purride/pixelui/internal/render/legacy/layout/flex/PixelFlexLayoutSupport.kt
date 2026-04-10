package com.purride.pixelui.internal

import com.purride.pixelui.internal.legacy.PixelColumnNode
import com.purride.pixelui.internal.legacy.PixelFlexFit
import com.purride.pixelui.internal.legacy.PixelRowNode
import com.purride.pixelui.internal.legacy.PixelWeightElement
import kotlin.math.max

/**
 * 负责 legacy flex 布局里权重子项的测量和分配。
 */
internal class PixelFlexLayoutSupport(
    private val measureNode: (LegacyRenderNode, PixelConstraints) -> PixelSize,
) {
    /**
     * 测量 row 的子节点尺寸，包含带权重子项的宽度分配。
     */
    fun measureRowChildren(
        node: PixelRowNode,
        constraints: PixelConstraints,
    ): List<PixelSize> {
        val sizes = MutableList(node.children.size) { PixelSize(width = 0, height = 0) }
        val spacingWidth = max(0, node.children.size - 1) * node.spacing
        var occupiedWidth = 0
        var totalWeight = 0f

        node.children.forEachIndexed { index, child ->
            val weight = childWeight(child)
            if (weight > 0f) {
                totalWeight += weight
            } else {
                val childSize = measureNode(child, constraints)
                sizes[index] = childSize
                occupiedWidth += childSize.width
            }
        }

        val remainingWidth = (constraints.maxWidth - spacingWidth - occupiedWidth).coerceAtLeast(0)
        if (totalWeight <= 0f) return sizes

        var assignedWidth = 0
        var weightedSeen = 0
        val weightedCount = node.children.count { childWeight(it) > 0f }
        node.children.forEachIndexed { index, child ->
            val weight = childWeight(child)
            if (weight <= 0f) return@forEachIndexed
            val fit = childFlexFit(child)
            weightedSeen += 1
            val allocatedWidth = if (weightedSeen == weightedCount) {
                remainingWidth - assignedWidth
            } else {
                ((remainingWidth * (weight / totalWeight)).toInt()).coerceAtLeast(0)
            }
            assignedWidth += allocatedWidth
            val measured = measureNode(
                child,
                PixelConstraints(
                    maxWidth = allocatedWidth,
                    maxHeight = constraints.maxHeight,
                ),
            )
            sizes[index] = PixelSize(
                width = if (fit == PixelFlexFit.TIGHT) allocatedWidth else measured.width.coerceAtMost(allocatedWidth),
                height = measured.height,
            )
        }
        return sizes
    }

    /**
     * 测量 column 的子节点尺寸，包含带权重子项的高度分配。
     */
    fun measureColumnChildren(
        node: PixelColumnNode,
        constraints: PixelConstraints,
    ): List<PixelSize> {
        val sizes = MutableList(node.children.size) { PixelSize(width = 0, height = 0) }
        val spacingHeight = max(0, node.children.size - 1) * node.spacing
        var occupiedHeight = 0
        var totalWeight = 0f

        node.children.forEachIndexed { index, child ->
            val weight = childWeight(child)
            if (weight > 0f) {
                totalWeight += weight
            } else {
                val childSize = measureNode(child, constraints)
                sizes[index] = childSize
                occupiedHeight += childSize.height
            }
        }

        val remainingHeight = (constraints.maxHeight - spacingHeight - occupiedHeight).coerceAtLeast(0)
        if (totalWeight <= 0f) return sizes

        var assignedHeight = 0
        var weightedSeen = 0
        val weightedCount = node.children.count { childWeight(it) > 0f }
        node.children.forEachIndexed { index, child ->
            val weight = childWeight(child)
            if (weight <= 0f) return@forEachIndexed
            val fit = childFlexFit(child)
            weightedSeen += 1
            val allocatedHeight = if (weightedSeen == weightedCount) {
                remainingHeight - assignedHeight
            } else {
                ((remainingHeight * (weight / totalWeight)).toInt()).coerceAtLeast(0)
            }
            assignedHeight += allocatedHeight
            val measured = measureNode(
                child,
                PixelConstraints(
                    maxWidth = constraints.maxWidth,
                    maxHeight = allocatedHeight,
                ),
            )
            sizes[index] = PixelSize(
                width = measured.width,
                height = if (fit == PixelFlexFit.TIGHT) allocatedHeight else measured.height.coerceAtMost(allocatedHeight),
            )
        }
        return sizes
    }

    /**
     * 解析子节点上的权重信息。
     */
    fun childWeight(node: LegacyRenderNode): Float {
        return node.modifier.elements
            .filterIsInstance<PixelWeightElement>()
            .lastOrNull()
            ?.weight
            ?.coerceAtLeast(0f)
            ?: 0f
    }

    /**
     * 解析子节点上的 flex fit。
     */
    fun childFlexFit(node: LegacyRenderNode): PixelFlexFit {
        return node.modifier.elements
            .filterIsInstance<PixelWeightElement>()
            .lastOrNull()
            ?.fit
            ?: PixelFlexFit.TIGHT
    }
}
