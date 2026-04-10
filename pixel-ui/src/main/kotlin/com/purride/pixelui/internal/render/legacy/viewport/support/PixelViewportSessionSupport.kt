package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer

/**
 * viewport 内部的局部缓冲渲染 support。
 */
internal class PixelViewportSessionSupport(
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
    fun renderSubtree(
        root: LegacyRenderNode,
        width: Int,
        height: Int,
        bounds: PixelRect = PixelRect(left = 0, top = 0, width = width, height = height),
        constraints: PixelConstraints = PixelConstraints(maxWidth = width, maxHeight = height),
    ): PixelRenderResult {
        val session = PixelRenderSessionFactory.create(
            width = width,
            height = height,
        )
        renderNode(
            root,
            bounds,
            constraints,
            session.buffer,
            session.clickTargets,
            session.pagerTargets,
            session.listTargets,
            session.textInputTargets,
        )
        return session.toRenderResult()
    }
}
