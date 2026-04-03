package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer

/**
 * 负责 legacy 节点渲染前的 modifier 解析和目标采集。
 */
internal class PixelNodeRenderSupport(
    private val textRenderSupport: PixelTextRenderSupport,
    private val textFieldRenderSupport: PixelTextFieldRenderSupport,
    private val layoutRenderSupport: PixelLayoutRenderSupport,
    private val viewportRenderSupport: PixelViewportRenderSupport,
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
    private val nodeRenderDispatch = PixelNodeRenderDispatch(
        textRenderSupport = textRenderSupport,
        textFieldRenderSupport = textFieldRenderSupport,
        layoutRenderSupport = layoutRenderSupport,
        viewportRenderSupport = viewportRenderSupport,
        renderNode = renderNode,
    )

    /**
     * 解析 modifier 后，把节点渲染分发到具体 support。
     */
    fun render(
        node: LegacyRenderNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        val modifierContext = PixelNodeModifierContextFactory.create(
            node = node,
            bounds = bounds,
            constraints = constraints,
        )
        modifierContext.modifierInfo.onClick?.let { onClick ->
            clickTargets += PixelClickTarget(bounds = bounds, onClick = onClick)
        }
        nodeRenderDispatch.renderNodeByType(
            node = node,
            paddedBounds = modifierContext.paddedBounds,
            innerConstraints = modifierContext.innerConstraints,
            buffer = buffer,
            clickTargets = clickTargets,
            pagerTargets = pagerTargets,
            listTargets = listTargets,
            textInputTargets = textInputTargets,
        )
    }
}
