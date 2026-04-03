package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelTextRasterizer

/**
 * legacy renderer 当前阶段的默认 support bundle。
 *
 * 目的不是增加抽象层，而是把 support wiring 从 `PixelRenderRuntime` 主文件里拿出去，
 * 让主文件只剩一个稳定 façade。
 */
internal class LegacyRenderSupportBundle(
    textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
) : LegacyRenderSupport {
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

    override fun renderRoot(
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
        buffer: PixelBuffer,
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

    private companion object {
        /**
         * 纵向滚动容器在滚动轴上的“近似无界”测量上限。
         */
        private const val SCROLL_AXIS_UNBOUNDED_MAX = 4096
    }
}
