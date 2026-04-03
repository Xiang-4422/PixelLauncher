package com.purride.pixelui.internal

import com.purride.pixelcore.AxisBufferComposer
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.legacy.PixelListNode
import com.purride.pixelui.internal.legacy.PixelPagerNode
import com.purride.pixelui.internal.legacy.PixelSingleChildScrollViewNode

/**
 * 负责 legacy viewport 节点的渲染调度。
 */
internal class PixelViewportRenderSupport(
    callbacks: LegacyRenderCallbacks,
    private val scrollAxisUnboundedMax: Int,
) {
    private val measureNode = callbacks.measureNode
    private val renderNode = callbacks.renderNode
    private val sessionSupport = PixelViewportSessionSupport(renderNode = renderNode)
    private val resultSupport = PixelViewportResultSupport()
    private val pagerRenderSupport = PixelPagerRenderSupport(
        sessionSupport = sessionSupport,
        resultSupport = resultSupport,
    )
    private val verticalScrollRenderSupport = PixelVerticalScrollRenderSupport(
        measureNode = measureNode,
        renderNode = renderNode,
        scrollAxisUnboundedMax = scrollAxisUnboundedMax,
        resultSupport = resultSupport,
    )

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
        pagerRenderSupport.renderPager(
            bounds = bounds,
            node = node,
            buffer = buffer,
            clickTargets = clickTargets,
            pagerTargets = pagerTargets,
            listTargets = listTargets,
            textInputTargets = textInputTargets,
        )
    }

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
        verticalScrollRenderSupport.renderList(
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
        verticalScrollRenderSupport.renderSingleChildScrollView(
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
