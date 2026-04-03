package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.legacy.PixelBoxNode
import com.purride.pixelui.internal.legacy.PixelColumnNode
import com.purride.pixelui.internal.legacy.PixelListNode
import com.purride.pixelui.internal.legacy.PixelPagerNode
import com.purride.pixelui.internal.legacy.PixelPositionedNode
import com.purride.pixelui.internal.legacy.PixelRowNode
import com.purride.pixelui.internal.legacy.PixelSingleChildScrollViewNode
import com.purride.pixelui.internal.legacy.PixelSurfaceNode
import com.purride.pixelui.internal.legacy.PixelTextFieldNode
import com.purride.pixelui.internal.legacy.PixelTextNode

/**
 * 负责把 legacy 节点类型分发到对应的 render support。
 */
internal class PixelNodeRenderDispatch(
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
    private val specialRenderDispatch = PixelNodeSpecialRenderDispatch(renderNode = renderNode)

    /**
     * 根据节点类型执行实际渲染。
     */
    fun renderNodeByType(
        node: LegacyRenderNode,
        paddedBounds: PixelRect,
        innerConstraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        if (specialRenderDispatch.renderIfHandled(
                node = node,
                bounds = paddedBounds,
                constraints = innerConstraints,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )
        ) {
            return
        }

        when (node) {
            is PixelTextNode -> textRenderSupport.renderText(
                node = node,
                bounds = paddedBounds,
                buffer = buffer,
            )

            is PixelSurfaceNode -> layoutRenderSupport.renderSurface(
                node = node,
                bounds = paddedBounds,
                constraints = innerConstraints,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelBoxNode -> layoutRenderSupport.renderBox(
                node = node,
                bounds = paddedBounds,
                constraints = innerConstraints,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelPositionedNode -> Unit

            is PixelRowNode -> layoutRenderSupport.renderRow(
                node = node,
                bounds = paddedBounds,
                constraints = innerConstraints,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelColumnNode -> layoutRenderSupport.renderColumn(
                node = node,
                bounds = paddedBounds,
                constraints = innerConstraints,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelPagerNode -> viewportRenderSupport.renderPager(
                node = node,
                bounds = paddedBounds,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelListNode -> viewportRenderSupport.renderList(
                node = node,
                bounds = paddedBounds,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelSingleChildScrollViewNode -> viewportRenderSupport.renderSingleChildScrollView(
                node = node,
                bounds = paddedBounds,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelTextFieldNode -> textFieldRenderSupport.render(
                node = node,
                bounds = paddedBounds,
                buffer = buffer,
                textInputTargets = textInputTargets,
            )
        }
    }
}
