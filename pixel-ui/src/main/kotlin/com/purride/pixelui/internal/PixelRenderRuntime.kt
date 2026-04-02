package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

internal class PixelRenderRuntime(
    private val textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
) {
    private val textRenderSupport = PixelTextRenderSupport(defaultTextRasterizer = textRasterizer)
    private val textFieldRenderSupport = PixelTextFieldRenderSupport(
        defaultTextRasterizer = textRasterizer,
        textRenderSupport = textRenderSupport,
    )
    private val layoutRenderSupport = PixelLayoutRenderSupport(
        measureNode = ::measure,
        renderNode = ::renderNode,
    )
    private val viewportRenderSupport = PixelViewportRenderSupport(
        measureNode = ::measure,
        renderNode = ::renderNode,
        scrollAxisUnboundedMax = SCROLL_AXIS_UNBOUNDED_MAX,
    )
    private val measureSupport = PixelMeasureSupport(
        measureNode = ::measure,
        textRenderSupport = textRenderSupport,
        textFieldRenderSupport = textFieldRenderSupport,
        layoutRenderSupport = layoutRenderSupport,
    )
    private val nodeRenderSupport = PixelNodeRenderSupport(
        textRenderSupport = textRenderSupport,
        textFieldRenderSupport = textFieldRenderSupport,
        layoutRenderSupport = layoutRenderSupport,
        viewportRenderSupport = viewportRenderSupport,
        renderNode = ::renderNode,
    )
    private val rootRenderSupport = PixelRootRenderSupport(
        measureNode = ::measure,
        renderNode = ::renderNode,
    )

    companion object {
        /**
         * 纵向滚动容器在滚动轴上的“近似无界”测量上限。
         *
         * 第一版还没有真正的无界约束模型，所以先用一个足够大的逻辑像素值，
         * 让单子节点滚动容器可以测出比视口更高的自然内容高度。
         */
        private const val SCROLL_AXIS_UNBOUNDED_MAX = 4096
    }
    fun render(
        root: LegacyRenderNode,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        return rootRenderSupport.renderRoot(
            root = root,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )
    }

    private fun measure(node: LegacyRenderNode, constraints: PixelConstraints): PixelSize {
        return measureSupport.measure(
            node = node,
            constraints = constraints,
            modifierInfo = PixelModifierSupport.resolve(node.modifier),
        )
    }

    private fun renderNode(
        node: LegacyRenderNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: com.purride.pixelcore.PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        nodeRenderSupport.render(
            node = node,
            bounds = bounds,
            constraints = constraints,
            buffer = buffer,
            clickTargets = clickTargets,
            pagerTargets = pagerTargets,
            listTargets = listTargets,
            textInputTargets = textInputTargets,
        )
    }

}
