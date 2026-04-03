package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer

/**
 * legacy render support 之间共享的测量与渲染回调集合。
 */
internal data class LegacyRenderCallbacks(
    val measureNode: (LegacyRenderNode, PixelConstraints) -> PixelSize,
    val renderNode: (
        node: LegacyRenderNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) -> Unit,
)
