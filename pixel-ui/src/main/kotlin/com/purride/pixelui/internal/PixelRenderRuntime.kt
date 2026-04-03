package com.purride.pixelui.internal

internal class PixelRenderRuntime(
    private val renderSupport: LegacyRenderSupport,
) : LegacyTreeRenderer {
    override fun render(
        root: LegacyRenderNode,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        return renderSupport.renderRoot(
            root = root,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        )
    }
}
