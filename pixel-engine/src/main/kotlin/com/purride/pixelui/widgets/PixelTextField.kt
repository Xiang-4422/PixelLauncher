package com.purride.pixelui

import com.purride.pixelcore.PixelColor

/**
 * 文本输入样式。
 *
 * 颜色直接用 [PixelColor] 指定；引擎只做像素渲染，不再区分 tone / colorMode。
 */
public data class PixelTextFieldStyle(
    val fillColor: PixelColor? = null,
    val borderColor: PixelColor? = PixelColor.fromRgb(255, 255, 255),
    val focusedBorderColor: PixelColor? = PixelColor.fromRgb(255, 255, 0),
    val disabledBorderColor: PixelColor? = PixelColor.fromRgb(100, 100, 100),
    val readOnlyBorderColor: PixelColor? = PixelColor.fromRgb(255, 255, 0),
    val textStyle: PixelTextStyle = PixelTextStyle.Default,
    val placeholderStyle: PixelTextStyle = PixelTextStyle(color = PixelColor.fromRgb(160, 160, 160)),
    val disabledTextStyle: PixelTextStyle = PixelTextStyle(color = PixelColor.fromRgb(80, 80, 80)),
    val disabledPlaceholderStyle: PixelTextStyle = PixelTextStyle(color = PixelColor.fromRgb(80, 80, 80)),
    val cursorColor: PixelColor = PixelColor.fromRgb(255, 255, 0),
    val padding: Int = 2,
) {
    public companion object {
        public val Default: PixelTextFieldStyle = PixelTextFieldStyle()
    }
}
