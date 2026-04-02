package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * retained runtime 默认支持集合工厂。
 *
 * 当前默认实现仍然由 bridge 层提供，但 retained runtime 只通过这层拿默认支持，
 * 不再直接构造具体的 bridge graph。
 */
internal object RetainedRenderSupportFactory {
    fun createDefault(
        textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
    ): RetainedRenderSupport {
        return BridgeSupportGraph(textRasterizer = textRasterizer)
    }
}
