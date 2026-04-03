package com.purride.pixelui.internal

import com.purride.pixelcore.AxisBufferComposer
import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.legacy.PixelPagerNode

/**
 * 负责 legacy pager 节点的分页渲染与目标平移。
 */
internal class PixelPagerRenderSupport(
    private val sessionSupport: PixelViewportSessionSupport,
) {
    /**
     * 渲染 pager 节点。
     */
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

        PixelTargetTranslateSupport.translateClickTargets(anchorPageResult.clickTargets, bounds, anchorShiftX, anchorShiftY, clickTargets)
        PixelTargetTranslateSupport.translatePagerTargets(anchorPageResult.pagerTargets, bounds, anchorShiftX, anchorShiftY, pagerTargets)
        PixelTargetTranslateSupport.translateListTargets(anchorPageResult.listTargets, bounds, anchorShiftX, anchorShiftY, listTargets)
        PixelTargetTranslateSupport.translateTextInputTargets(anchorPageResult.textInputTargets, bounds, anchorShiftX, anchorShiftY, textInputTargets)

        adjacentPageResult?.let { result ->
            PixelTargetTranslateSupport.translateClickTargets(result.clickTargets, bounds, adjacentShiftX, adjacentShiftY, clickTargets)
            PixelTargetTranslateSupport.translatePagerTargets(result.pagerTargets, bounds, adjacentShiftX, adjacentShiftY, pagerTargets)
            PixelTargetTranslateSupport.translateListTargets(result.listTargets, bounds, adjacentShiftX, adjacentShiftY, listTargets)
            PixelTargetTranslateSupport.translateTextInputTargets(result.textInputTargets, bounds, adjacentShiftX, adjacentShiftY, textInputTargets)
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

    /**
     * 把单页内容渲染到独立子会话中。
     */
    private fun renderPagerPage(
        page: LegacyRenderNode,
        pageWidth: Int,
        pageHeight: Int,
    ): PixelRenderResult {
        return sessionSupport.renderSubtree(
            root = page,
            width = pageWidth,
            height = pageHeight,
            bounds = PixelRect(left = 0, top = 0, width = pageWidth, height = pageHeight),
            constraints = PixelConstraints(maxWidth = pageWidth, maxHeight = pageHeight),
        )
    }
}
