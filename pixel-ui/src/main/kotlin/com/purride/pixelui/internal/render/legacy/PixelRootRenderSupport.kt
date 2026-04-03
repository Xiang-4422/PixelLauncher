package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer

/**
 * 根渲染 support：负责一次完整渲染会话的组装。
 *
 * 这样 PixelRenderRuntime 可以只保留 wiring，不再自己拼装 buffer、targets
 * 和 root bounds。
 */
internal class PixelRootRenderSupport(
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
    fun renderRoot(
        root: LegacyRenderNode,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        val session = PixelRenderSession(
            buffer = PixelBuffer(width = logicalWidth, height = logicalHeight),
        )
        session.buffer.clear()
        val rootConstraints = PixelConstraints(
            maxWidth = logicalWidth,
            maxHeight = logicalHeight,
        )
        val measuredRoot = measureNode(root, rootConstraints)
        val rootBounds = PixelRect(
            left = 0,
            top = 0,
            width = measuredRoot.width.coerceAtMost(logicalWidth),
            height = measuredRoot.height.coerceAtMost(logicalHeight),
        )
        renderNode(
            root,
            rootBounds,
            rootConstraints,
            session.buffer,
            session.clickTargets,
            session.pagerTargets,
            session.listTargets,
            session.textInputTargets,
        )
        return session.toRenderResult()
    }
}
