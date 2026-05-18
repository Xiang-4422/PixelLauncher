package com.purride.pixelui

import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelcore.PixelTone

/**
 * 像素文本样式。
 *
 * 第一版先稳定文本色阶和文本栅格器两项能力。
 * 后续如果要补字号、字距、文本 token 或主题样式，可以继续沿这个对象扩展。
 */
public data class PixelTextStyle(
    val tone: PixelTone = PixelTone.ON,
    val textRasterizer: PixelTextRasterizer? = null,
    val lineSpacing: Int = 0,
) {
    public companion object {
        public val Default: PixelTextStyle = PixelTextStyle()
        public val Accent: PixelTextStyle = PixelTextStyle(tone = PixelTone.ACCENT)
    }
}
