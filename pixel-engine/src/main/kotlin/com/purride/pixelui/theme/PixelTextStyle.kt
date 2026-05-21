package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelTextRasterizer

/**
 * 像素文本样式。
 *
 * 颜色通过 [color] 直接指定；引擎只做一件事——把 widget 树渲染成 ARGB 像素网格。
 */
public data class PixelTextStyle(
    val color: PixelColor = PixelColor.fromRgb(255, 255, 255),
    val textRasterizer: PixelTextRasterizer? = null,
    val lineSpacing: Int = 0,
    val letterSpacing: Int = 0,
    val lineHeight: Int? = null,
    val fontScale: Int = 1,
) {
    public companion object {
        public val Default: PixelTextStyle = PixelTextStyle()
    }
}
