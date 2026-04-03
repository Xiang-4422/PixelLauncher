package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.legacy.PixelSingleChildScrollViewNode
import kotlin.math.roundToInt

/**
 * 负责 legacy `SingleChildScrollView` 节点的布局、绘制和目标平移。
 */
internal class PixelSingleChildScrollRenderSupport(
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
    private val scrollAxisUnboundedMax: Int,
) {
    /**
     * 渲染 single child scroll view 节点。
     */
    fun render(
        node: PixelSingleChildScrollViewNode,
        bounds: PixelRect,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        val viewportWidth = bounds.width.coerceAtLeast(1)
        val viewportHeight = bounds.height.coerceAtLeast(1)
        val childConstraints = PixelConstraints(
            maxWidth = viewportWidth,
            maxHeight = scrollAxisUnboundedMax,
        )
        val childSize = measureNode(node.child, childConstraints)
        val contentHeight = childSize.height
        node.state.itemTopOffsetsPx = intArrayOf(0)
        node.state.itemHeightsPx = intArrayOf(childSize.height)
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

        val scrollBuffer = PixelBuffer(width = viewportWidth, height = viewportHeight).apply { clear() }
        val scrollClickTargets = mutableListOf<PixelClickTarget>()
        val scrollPagerTargets = mutableListOf<PixelPagerTarget>()
        val nestedListTargets = mutableListOf<PixelListTarget>()
        val scrollTextInputTargets = mutableListOf<PixelTextInputTarget>()
        val childBounds = PixelRect(
            left = 0,
            top = -node.state.scrollOffsetPx.roundToInt(),
            width = childSize.width,
            height = childSize.height,
        )

        renderNode(
            node.child,
            childBounds,
            childConstraints,
            scrollBuffer,
            scrollClickTargets,
            scrollPagerTargets,
            nestedListTargets,
            scrollTextInputTargets,
        )

        PixelTargetTranslateSupport.translateClickTargets(scrollClickTargets, bounds, 0, 0, clickTargets)
        PixelTargetTranslateSupport.translatePagerTargets(scrollPagerTargets, bounds, 0, 0, pagerTargets)
        PixelTargetTranslateSupport.translateListTargets(nestedListTargets, bounds, 0, 0, listTargets)
        PixelTargetTranslateSupport.translateTextInputTargets(scrollTextInputTargets, bounds, 0, 0, textInputTargets)

        buffer.blit(
            source = scrollBuffer,
            destX = bounds.left,
            destY = bounds.top,
        )
    }
}
