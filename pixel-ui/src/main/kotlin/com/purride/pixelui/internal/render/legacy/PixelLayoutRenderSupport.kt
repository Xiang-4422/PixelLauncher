package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.legacy.PixelAlignment
import com.purride.pixelui.internal.legacy.PixelBoxNode
import com.purride.pixelui.internal.legacy.PixelColumnNode
import com.purride.pixelui.internal.legacy.PixelCrossAxisAlignment
import com.purride.pixelui.internal.legacy.PixelFlexFit
import com.purride.pixelui.internal.legacy.PixelMainAxisAlignment
import com.purride.pixelui.internal.legacy.PixelPositionedNode
import com.purride.pixelui.internal.legacy.PixelRowNode
import com.purride.pixelui.internal.legacy.PixelSurfaceNode
import com.purride.pixelui.internal.legacy.PixelWeightElement
import kotlin.math.max

internal class PixelLayoutRenderSupport(
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
) {
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
        val childBounds = alignedBounds(
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
                val childBounds = alignedBounds(
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
        val childSizes = measureRowChildren(node, constraints)
        val contentWidth = childSizes.sumOf { it.width } + (max(0, node.children.size - 1) * node.spacing)
        val horizontalMainAxis = mainAxisArrangement(
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
                top = crossAxisStart(
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
        val childSizes = measureColumnChildren(node, constraints)
        val contentHeight = childSizes.sumOf { it.height } + (max(0, node.children.size - 1) * node.spacing)
        val verticalMainAxis = mainAxisArrangement(
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
                left = crossAxisStart(
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

    fun measurePositionedMaxWidth(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
    ): Int = positionedMaxWidth(node, constraints)

    fun measurePositionedMaxHeight(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
    ): Int = positionedMaxHeight(node, constraints)

    fun measurePositionedWidth(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
        childSize: PixelSize,
    ): Int = positionedWidth(node, constraints, childSize)

    fun measurePositionedHeight(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
        childSize: PixelSize,
    ): Int = positionedHeight(node, constraints, childSize)

    fun measureRowChildrenForLayout(
        node: PixelRowNode,
        constraints: PixelConstraints,
    ): List<PixelSize> = measureRowChildren(node, constraints)

    fun measureColumnChildrenForLayout(
        node: PixelColumnNode,
        constraints: PixelConstraints,
    ): List<PixelSize> = measureColumnChildren(node, constraints)

    fun childWeightOf(node: LegacyRenderNode): Float = childWeight(node)

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
            maxWidth = positionedMaxWidth(node, outerConstraints),
            maxHeight = positionedMaxHeight(node, outerConstraints),
        )
        val childSize = measureNode(node.child, childConstraints)
        val width = positionedWidth(node, outerConstraints, childSize).coerceAtLeast(0)
        val height = positionedHeight(node, outerConstraints, childSize).coerceAtLeast(0)
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

    private fun measureRowChildren(
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

    private fun measureColumnChildren(
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

    private fun childWeight(node: LegacyRenderNode): Float {
        return node.modifier.elements
            .filterIsInstance<PixelWeightElement>()
            .lastOrNull()
            ?.weight
            ?.coerceAtLeast(0f)
            ?: 0f
    }

    private fun childFlexFit(node: LegacyRenderNode): PixelFlexFit {
        return node.modifier.elements
            .filterIsInstance<PixelWeightElement>()
            .lastOrNull()
            ?.fit
            ?: PixelFlexFit.TIGHT
    }

    private fun crossAxisStart(
        containerStart: Int,
        containerExtent: Int,
        childExtent: Int,
        alignment: PixelCrossAxisAlignment,
    ): Int {
        val remaining = (containerExtent - childExtent).coerceAtLeast(0)
        return when (alignment) {
            PixelCrossAxisAlignment.START -> containerStart
            PixelCrossAxisAlignment.CENTER -> containerStart + (remaining / 2)
            PixelCrossAxisAlignment.END -> containerStart + remaining
            PixelCrossAxisAlignment.STRETCH -> containerStart
        }
    }

    private data class MainAxisArrangement(
        val start: Int,
        val spacingAfterChild: Int,
    )

    private fun mainAxisArrangement(
        containerStart: Int,
        containerExtent: Int,
        contentExtent: Int,
        spacing: Int,
        childCount: Int,
        alignment: PixelMainAxisAlignment,
    ): MainAxisArrangement {
        val remaining = (containerExtent - contentExtent).coerceAtLeast(0)
        return when (alignment) {
            PixelMainAxisAlignment.START -> MainAxisArrangement(containerStart, spacing)
            PixelMainAxisAlignment.CENTER -> MainAxisArrangement(containerStart + (remaining / 2), spacing)
            PixelMainAxisAlignment.END -> MainAxisArrangement(containerStart + remaining, spacing)
            PixelMainAxisAlignment.SPACE_BETWEEN -> {
                if (childCount <= 1) {
                    MainAxisArrangement(containerStart, spacing)
                } else {
                    MainAxisArrangement(containerStart, spacing + (remaining / (childCount - 1)))
                }
            }
            PixelMainAxisAlignment.SPACE_AROUND -> {
                if (childCount <= 0) {
                    MainAxisArrangement(containerStart, spacing)
                } else {
                    val unit = remaining / childCount
                    MainAxisArrangement(containerStart + (unit / 2), spacing + unit)
                }
            }
            PixelMainAxisAlignment.SPACE_EVENLY -> {
                val slotCount = childCount + 1
                val unit = if (slotCount <= 0) 0 else remaining / slotCount
                MainAxisArrangement(containerStart + unit, spacing + unit)
            }
        }
    }

    private fun positionedMaxWidth(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
    ): Int {
        return when {
            node.width != null -> node.width
            node.left != null && node.right != null -> (constraints.maxWidth - node.left - node.right).coerceAtLeast(0)
            else -> constraints.maxWidth
        }
    }

    private fun positionedMaxHeight(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
    ): Int {
        return when {
            node.height != null -> node.height
            node.top != null && node.bottom != null -> (constraints.maxHeight - node.top - node.bottom).coerceAtLeast(0)
            else -> constraints.maxHeight
        }
    }

    private fun positionedWidth(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
        childSize: PixelSize,
    ): Int {
        return when {
            node.width != null -> node.width
            node.left != null && node.right != null -> (constraints.maxWidth - node.left - node.right).coerceAtLeast(0)
            else -> childSize.width
        }
    }

    private fun positionedHeight(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
        childSize: PixelSize,
    ): Int {
        return when {
            node.height != null -> node.height
            node.top != null && node.bottom != null -> (constraints.maxHeight - node.top - node.bottom).coerceAtLeast(0)
            else -> childSize.height
        }
    }

    private fun alignedBounds(
        outerBounds: PixelRect,
        childSize: PixelSize,
        alignment: PixelAlignment,
    ): PixelRect {
        val clampedWidth = childSize.width.coerceAtMost(outerBounds.width)
        val clampedHeight = childSize.height.coerceAtMost(outerBounds.height)
        val centeredLeft = outerBounds.left + ((outerBounds.width - childSize.width).coerceAtLeast(0) / 2)
        val endLeft = outerBounds.left + (outerBounds.width - childSize.width).coerceAtLeast(0)
        val centeredTop = outerBounds.top + ((outerBounds.height - childSize.height).coerceAtLeast(0) / 2)
        val endTop = outerBounds.top + (outerBounds.height - childSize.height).coerceAtLeast(0)

        val left = when (alignment) {
            PixelAlignment.TOP_START,
            PixelAlignment.CENTER_START,
            PixelAlignment.BOTTOM_START -> outerBounds.left
            PixelAlignment.TOP_CENTER,
            PixelAlignment.CENTER,
            PixelAlignment.BOTTOM_CENTER -> centeredLeft
            PixelAlignment.TOP_END,
            PixelAlignment.CENTER_END,
            PixelAlignment.BOTTOM_END -> endLeft
        }
        val top = when (alignment) {
            PixelAlignment.TOP_START,
            PixelAlignment.TOP_CENTER,
            PixelAlignment.TOP_END -> outerBounds.top
            PixelAlignment.CENTER_START,
            PixelAlignment.CENTER,
            PixelAlignment.CENTER_END -> centeredTop
            PixelAlignment.BOTTOM_START,
            PixelAlignment.BOTTOM_CENTER,
            PixelAlignment.BOTTOM_END -> endTop
        }

        return PixelRect(
            left = left,
            top = top,
            width = clampedWidth,
            height = clampedHeight,
        )
    }
}
