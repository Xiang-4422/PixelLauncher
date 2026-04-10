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
    private val renderNode = callbacks.renderNode
    private val rootLayoutSupport = PixelRootLayoutSupport(
        measureNode = callbacks.measureNode,
    )

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
        val rootConstraints = rootLayoutSupport.rootConstraints(
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )
        val rootBounds = rootLayoutSupport.rootBounds(
            root = root,
            constraints = rootConstraints,
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
