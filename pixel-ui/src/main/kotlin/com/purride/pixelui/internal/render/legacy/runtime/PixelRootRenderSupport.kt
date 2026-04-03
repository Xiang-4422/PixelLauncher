package com.purride.pixelui.internal

/**
 * 根渲染 support：负责一次完整渲染会话的组装。
 *
 * 这样 PixelRenderRuntime 可以只保留 wiring，不再自己拼装 buffer、targets
 * 和 root bounds。
 */
internal class PixelRootRenderSupport(
    callbacks: LegacyRenderCallbacks,
) {
    private val measureNode = callbacks.measureNode
    private val renderNode = callbacks.renderNode

    /**
     * 渲染根节点并收集一次完整会话结果。
     */
    fun renderRoot(
        root: LegacyRenderNode,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        val session = PixelRenderSessionFactory.create(
            width = logicalWidth,
            height = logicalHeight,
        )
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
