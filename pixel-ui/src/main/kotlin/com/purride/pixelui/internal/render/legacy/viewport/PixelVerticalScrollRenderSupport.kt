package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.legacy.PixelListNode
import com.purride.pixelui.internal.legacy.PixelSingleChildScrollViewNode

/**
 * 负责 legacy 纵向滚动节点的渲染调度。
 */
internal class PixelVerticalScrollRenderSupport(
    measureNode: (LegacyRenderNode, PixelConstraints) -> PixelSize,
    renderNode: (
        node: LegacyRenderNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) -> Unit,
    scrollAxisUnboundedMax: Int,
) {
    private val listRenderSupport = PixelListRenderSupport(
        measureNode = measureNode,
        renderNode = renderNode,
    )
    private val singleChildScrollRenderSupport = PixelSingleChildScrollRenderSupport(
        measureNode = measureNode,
        renderNode = renderNode,
        scrollAxisUnboundedMax = scrollAxisUnboundedMax,
    )

    /**
     * 渲染 list 节点。
     */
    fun renderList(
        node: PixelListNode,
        bounds: PixelRect,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        listRenderSupport.render(
            node = node,
            bounds = bounds,
            buffer = buffer,
            clickTargets = clickTargets,
            pagerTargets = pagerTargets,
            listTargets = listTargets,
            textInputTargets = textInputTargets,
        )
    }

    /**
     * 渲染 single child scroll view 节点。
     */
    fun renderSingleChildScrollView(
        node: PixelSingleChildScrollViewNode,
        bounds: PixelRect,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        singleChildScrollRenderSupport.render(
            node = node,
            bounds = bounds,
            buffer = buffer,
            clickTargets = clickTargets,
            pagerTargets = pagerTargets,
            listTargets = listTargets,
            textInputTargets = textInputTargets,
        )
    }
}
