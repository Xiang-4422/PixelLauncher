package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
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
    private val nodeRuntimeSupport = LegacyNodeRuntimeSupport()
    private val assembly = LegacySupportAssemblyFactory.createDefault(
        textRasterizer = textRasterizer,
        measureNode = nodeRuntimeSupport::measure,
        renderNode = nodeRuntimeSupport::renderNode,
    ).also { boundAssembly ->
        nodeRuntimeSupport.bind(boundAssembly)
    }

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
}
