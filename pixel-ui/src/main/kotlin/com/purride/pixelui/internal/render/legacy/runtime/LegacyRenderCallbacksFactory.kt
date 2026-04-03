package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer

/**
 * 负责创建 legacy render callbacks。
 */
internal object LegacyRenderCallbacksFactory {
    /**
     * 基于 measure/render 回调创建默认 callbacks。
     */
    fun create(
        measureNode: (LegacyRenderNode, PixelConstraints) -> PixelSize,
        renderNode: (
            LegacyRenderNode,
            PixelRect,
            PixelConstraints,
            PixelBuffer,
            MutableList<PixelClickTarget>,
            MutableList<PixelPagerTarget>,
            MutableList<PixelListTarget>,
            MutableList<PixelTextInputTarget>,
        ) -> Unit,
    ): LegacyRenderCallbacks {
        return LegacyRenderCallbacks(
            measureNode = measureNode,
            renderNode = renderNode,
        )
    }
}
