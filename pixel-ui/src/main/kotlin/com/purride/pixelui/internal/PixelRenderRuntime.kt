package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

internal class PixelRenderRuntime(
    private val textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
) : LegacyTreeRenderer {
    private val supportBundle = LegacyRenderSupportBundle(textRasterizer = textRasterizer)

    override fun render(
        root: LegacyRenderNode,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        return supportBundle.renderRoot(
            root = root,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )
    }
}
