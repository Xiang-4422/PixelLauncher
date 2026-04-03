package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer

/**
 * 负责把 viewport 子渲染结果平移并合并回外层上下文。
 */
internal class PixelViewportResultSupport {
    /**
     * 把一个子渲染结果的 targets 平移到外层 bounds，并写入外层收集器。
     */
    fun appendTranslatedTargets(
        result: PixelRenderResult,
        bounds: PixelRect,
        shiftX: Int,
        shiftY: Int,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        PixelTargetTranslateSupport.translateClickTargets(result.clickTargets, bounds, shiftX, shiftY, clickTargets)
        PixelTargetTranslateSupport.translatePagerTargets(result.pagerTargets, bounds, shiftX, shiftY, pagerTargets)
        PixelTargetTranslateSupport.translateListTargets(result.listTargets, bounds, shiftX, shiftY, listTargets)
        PixelTargetTranslateSupport.translateTextInputTargets(result.textInputTargets, bounds, shiftX, shiftY, textInputTargets)
    }

    /**
     * 把子渲染结果的 buffer 贴回外层 buffer。
     */
    fun blit(
        result: PixelRenderResult,
        bounds: PixelRect,
        buffer: PixelBuffer,
    ) {
        buffer.blit(
            source = result.buffer,
            destX = bounds.left,
            destY = bounds.top,
        )
    }
}
