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
        val modifierInfo = PixelModifierSupport.resolve(node.modifier)
        modifierInfo.onClick?.let { onClick ->
            clickTargets += PixelClickTarget(bounds = bounds, onClick = onClick)
        }
        val paddedBounds = bounds.inset(
            paddingLeft = modifierInfo.paddingLeft,
            paddingTop = modifierInfo.paddingTop,
            paddingRight = modifierInfo.paddingRight,
            paddingBottom = modifierInfo.paddingBottom,
        )
        val innerConstraints = constraints.shrink(
            paddingLeft = modifierInfo.paddingLeft,
            paddingTop = modifierInfo.paddingTop,
            paddingRight = modifierInfo.paddingRight,
            paddingBottom = modifierInfo.paddingBottom,
        )
        nodeRenderDispatch.renderNodeByType(
            node = node,
            paddedBounds = paddedBounds,
            innerConstraints = innerConstraints,
            buffer = buffer,
            clickTargets = clickTargets,
            pagerTargets = pagerTargets,
            listTargets = listTargets,
            textInputTargets = textInputTargets,
        )
    }
}
