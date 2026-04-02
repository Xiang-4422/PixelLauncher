package com.purride.pixelui.internal

import com.purride.pixelcore.AxisBufferComposer
import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.legacy.PixelListNode
import com.purride.pixelui.internal.legacy.PixelPagerNode
import com.purride.pixelui.internal.legacy.PixelSingleChildScrollViewNode
import kotlin.math.max
import kotlin.math.roundToInt

internal class PixelViewportRenderSupport(
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
    fun renderPager(
        node: PixelPagerNode,
        bounds: PixelRect,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        node.controller.sync(
            state = node.state,
            axis = node.axis,
            pageCount = node.pages.size.coerceAtLeast(1),
        )
        pagerTargets += PixelPagerTarget(
            bounds = bounds,
            axis = node.axis,
            state = node.state,
            controller = node.controller,
            onPageChanged = node.onPageChanged,
        )

        val snapshot = node.controller.snapshot(node.state)
        val pageWidth = bounds.width.coerceAtLeast(1)
        val pageHeight = bounds.height.coerceAtLeast(1)
        val anchorPage = node.pages.getOrNull(snapshot.anchorPage) ?: return
        val anchorPageResult = renderPagerPage(
            page = anchorPage,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
        )

        val adjacentPageResult = snapshot.adjacentPage?.let { pageIndex ->
            node.pages.getOrNull(pageIndex)?.let { adjacentPage ->
                renderPagerPage(
                    page = adjacentPage,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                )
            }
        }

        val anchorShiftX = if (snapshot.axis == PixelAxis.HORIZONTAL) snapshot.dragOffsetPx.toInt() else 0
        val anchorShiftY = if (snapshot.axis == PixelAxis.VERTICAL) snapshot.dragOffsetPx.toInt() else 0
        val adjacentShiftX = when (snapshot.axis) {
            PixelAxis.HORIZONTAL -> if (snapshot.dragOffsetPx > 0f) anchorShiftX - pageWidth else anchorShiftX + pageWidth
            PixelAxis.VERTICAL -> 0
        }
        val adjacentShiftY = when (snapshot.axis) {
            PixelAxis.HORIZONTAL -> 0
            PixelAxis.VERTICAL -> if (snapshot.dragOffsetPx > 0f) anchorShiftY - pageHeight else anchorShiftY + pageHeight
        }

        translateTargets(anchorPageResult.clickTargets, bounds, anchorShiftX, anchorShiftY, clickTargets)
        translatePagerTargets(anchorPageResult.pagerTargets, bounds, anchorShiftX, anchorShiftY, pagerTargets)
        translateListTargets(anchorPageResult.listTargets, bounds, anchorShiftX, anchorShiftY, listTargets)
        translateTextInputTargets(anchorPageResult.textInputTargets, bounds, anchorShiftX, anchorShiftY, textInputTargets)

        adjacentPageResult?.let { result ->
            translateTargets(result.clickTargets, bounds, adjacentShiftX, adjacentShiftY, clickTargets)
            translatePagerTargets(result.pagerTargets, bounds, adjacentShiftX, adjacentShiftY, pagerTargets)
            translateListTargets(result.listTargets, bounds, adjacentShiftX, adjacentShiftY, listTargets)
            translateTextInputTargets(result.textInputTargets, bounds, adjacentShiftX, adjacentShiftY, textInputTargets)
        }

        val composed = AxisBufferComposer.compose(
            primary = anchorPageResult.buffer,
            secondary = adjacentPageResult?.buffer,
            axis = snapshot.axis,
            offsetPx = snapshot.dragOffsetPx,
        )
        buffer.blit(
            source = composed,
            destX = bounds.left,
            destY = bounds.top,
        )
    }

    fun renderList(
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

        translateTargets(listClickTargets, bounds, 0, 0, clickTargets)
        translatePagerTargets(listPagerTargets, bounds, 0, 0, pagerTargets)
        translateListTargets(nestedListTargets, bounds, 0, 0, listTargets)
        translateTextInputTargets(listTextInputTargets, bounds, 0, 0, textInputTargets)

        buffer.blit(
            source = listBuffer,
            destX = bounds.left,
            destY = bounds.top,
        )
    }

    fun renderSingleChildScrollView(
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

        translateTargets(scrollClickTargets, bounds, 0, 0, clickTargets)
        translatePagerTargets(scrollPagerTargets, bounds, 0, 0, pagerTargets)
        translateListTargets(nestedListTargets, bounds, 0, 0, listTargets)
        translateTextInputTargets(scrollTextInputTargets, bounds, 0, 0, textInputTargets)

        buffer.blit(
            source = scrollBuffer,
            destX = bounds.left,
            destY = bounds.top,
        )
    }

    private fun renderPagerPage(
        page: LegacyRenderNode,
        pageWidth: Int,
        pageHeight: Int,
    ): PixelRenderResult {
        val pageBuffer = PixelBuffer(width = pageWidth, height = pageHeight).apply { clear() }
        val pageClickTargets = mutableListOf<PixelClickTarget>()
        val pagePagerTargets = mutableListOf<PixelPagerTarget>()
        val pageListTargets = mutableListOf<PixelListTarget>()
        val pageTextInputTargets = mutableListOf<PixelTextInputTarget>()
        renderNode(
            page,
            PixelRect(left = 0, top = 0, width = pageWidth, height = pageHeight),
            PixelConstraints(maxWidth = pageWidth, maxHeight = pageHeight),
            pageBuffer,
            pageClickTargets,
            pagePagerTargets,
            pageListTargets,
            pageTextInputTargets,
        )
        return PixelRenderResult(
            buffer = pageBuffer,
            clickTargets = pageClickTargets,
            pagerTargets = pagePagerTargets,
            listTargets = pageListTargets,
            textInputTargets = pageTextInputTargets,
        )
    }

    private fun translateTargets(
        targets: List<PixelClickTarget>,
        parentBounds: PixelRect,
        pageShiftX: Int,
        pageShiftY: Int,
        into: MutableList<PixelClickTarget>,
    ) {
        targets.forEach { target ->
            target.bounds
                .translate(
                    deltaX = parentBounds.left + pageShiftX,
                    deltaY = parentBounds.top + pageShiftY,
                )
                .intersect(parentBounds)
                ?.let { translatedBounds ->
                    into += PixelClickTarget(
                        bounds = translatedBounds,
                        onClick = target.onClick,
                    )
                }
        }
    }

    private fun translatePagerTargets(
        targets: List<PixelPagerTarget>,
        parentBounds: PixelRect,
        pageShiftX: Int,
        pageShiftY: Int,
        into: MutableList<PixelPagerTarget>,
    ) {
        targets.forEach { target ->
            target.bounds
                .translate(
                    deltaX = parentBounds.left + pageShiftX,
                    deltaY = parentBounds.top + pageShiftY,
                )
                .intersect(parentBounds)
                ?.let { translatedBounds ->
                    into += target.copy(bounds = translatedBounds)
                }
        }
    }

    private fun translateListTargets(
        targets: List<PixelListTarget>,
        parentBounds: PixelRect,
        pageShiftX: Int,
        pageShiftY: Int,
        into: MutableList<PixelListTarget>,
    ) {
        targets.forEach { target ->
            target.bounds
                .translate(
                    deltaX = parentBounds.left + pageShiftX,
                    deltaY = parentBounds.top + pageShiftY,
                )
                .intersect(parentBounds)
                ?.let { translatedBounds ->
                    into += target.copy(bounds = translatedBounds)
                }
        }
    }

    private fun translateTextInputTargets(
        targets: List<PixelTextInputTarget>,
        parentBounds: PixelRect,
        pageShiftX: Int,
        pageShiftY: Int,
        into: MutableList<PixelTextInputTarget>,
    ) {
        targets.forEach { target ->
            target.bounds
                .translate(
                    deltaX = parentBounds.left + pageShiftX,
                    deltaY = parentBounds.top + pageShiftY,
                )
                .intersect(parentBounds)
                ?.let { translatedBounds ->
                    into += target.copy(bounds = translatedBounds)
                }
        }
    }
}
