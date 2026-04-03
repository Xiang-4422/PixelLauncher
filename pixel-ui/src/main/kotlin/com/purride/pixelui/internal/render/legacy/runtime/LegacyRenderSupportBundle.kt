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
    private val assembly = LegacySupportAssemblyFactory.createDefault(
        textRasterizer = textRasterizer,
        measureNode = ::measure,
        renderNode = ::renderNode,
    )

    override fun renderRoot(
        root: LegacyRenderNode,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        return assembly.rootRenderSupport.renderRoot(
            root = root,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )
    }

    private fun measure(node: LegacyRenderNode, constraints: PixelConstraints): PixelSize {
        return assembly.measureSupport.measure(
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
        assembly.nodeRenderSupport.render(
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
