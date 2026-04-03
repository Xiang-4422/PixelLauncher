package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.legacy.PixelListNode
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 负责 legacy `ListView` 节点的布局、绘制和目标平移。
 */
internal class PixelListRenderSupport(
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
    /**
     * 渲染 list 节点。
     */
    fun render(
        node: PixelListNode,
        bounds: PixelRect,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        val viewportWidth = bounds.width.coerceAtLeast(1)
        val viewportHeight = bounds.height.coerceAtLeast(1)
        val itemConstraints = PixelConstraints(
            maxWidth = viewportWidth,
            maxHeight = viewportHeight,
        )
        val itemSizes = node.items.map { child -> measureNode(child, itemConstraints) }
        val contentHeight = itemSizes.sumOf { size -> size.height } + (max(0, itemSizes.size - 1) * node.spacing)
        val itemTopOffsets = IntArray(itemSizes.size)
        var nextItemTop = 0
        itemSizes.forEachIndexed { index, size ->
            itemTopOffsets[index] = nextItemTop
            nextItemTop += size.height + node.spacing
        }
        node.state.itemTopOffsetsPx = itemTopOffsets
        node.state.itemHeightsPx = itemSizes.map { it.height }.toIntArray()
        node.controller.sync(
            state = node.state,
            viewportHeightPx = viewportHeight,
            contentHeightPx = contentHeight,
        )

        listTargets += PixelListTarget(
            bounds = bounds,
            viewportHeightPx = viewportHeight,
            contentHeightPx = contentHeight,
            state = node.state,
            controller = node.controller,
        )

        val listBuffer = PixelBuffer(width = viewportWidth, height = viewportHeight).apply { clear() }
        val listClickTargets = mutableListOf<PixelClickTarget>()
        val listPagerTargets = mutableListOf<PixelPagerTarget>()
        val nestedListTargets = mutableListOf<PixelListTarget>()
        val listTextInputTargets = mutableListOf<PixelTextInputTarget>()
        var cursorY = -node.state.scrollOffsetPx.roundToInt()

        node.items.zip(itemSizes).forEach { (child, childSize) ->
            val childBounds = PixelRect(
                left = 0,
                top = cursorY,
                width = childSize.width,
                height = childSize.height,
            )
            if (childBounds.bottom > 0 && childBounds.top < viewportHeight) {
                renderNode(
                    child,
                    childBounds,
                    PixelConstraints(
                        maxWidth = viewportWidth,
                        maxHeight = childSize.height,
                    ),
                    listBuffer,
                    listClickTargets,
                    listPagerTargets,
                    nestedListTargets,
                    listTextInputTargets,
                )
            }
            cursorY += childSize.height + node.spacing
        }

        PixelTargetTranslateSupport.translateClickTargets(listClickTargets, bounds, 0, 0, clickTargets)
        PixelTargetTranslateSupport.translatePagerTargets(listPagerTargets, bounds, 0, 0, pagerTargets)
        PixelTargetTranslateSupport.translateListTargets(nestedListTargets, bounds, 0, 0, listTargets)
        PixelTargetTranslateSupport.translateTextInputTargets(listTextInputTargets, bounds, 0, 0, textInputTargets)

        buffer.blit(
            source = listBuffer,
            destX = bounds.left,
            destY = bounds.top,
        )
    }
}
