package com.purride.pixelui

import com.purride.pixelcore.PixelColor

/**
 * 像素按钮样式。
 *
 * 填充色、边框色和文本样式直接用 [PixelColor] 指定；引擎只做像素渲染，
 * 不再区分 tone / colorMode。
 */
public data class PixelButtonStyle(
    val fillColor: PixelColor? = null,
    val borderColor: PixelColor? = PixelColor.fromRgb(255, 255, 255),
    val textStyle: PixelTextStyle = PixelTextStyle.Default,
    val alignment: Alignment = Alignment.CENTER,
) {
    public companion object {
        public val Default: PixelButtonStyle = PixelButtonStyle()
    }
}

/**
 * 无边框文字按钮样式。
 *
 * 默认 padding 为零，按钮尺寸由文字自然决定；需要扩大点击区域时由调用方显式设置。
 */
public data class PixelTextButtonStyle(
    val textStyle: PixelTextStyle = PixelTextStyle.Default,
    val alignment: Alignment = Alignment.CENTER,
    val padding: EdgeInsets = EdgeInsets.all(0),
) {
    public companion object {
        public val Default: PixelTextButtonStyle = PixelTextButtonStyle()
    }
}
