package com.purride.pixelui

import com.purride.pixelcore.PixelTone

/**
 * 容器样式。
 *
 * 当前先把 `Container` 最常用的三项视觉参数收进样式对象：
 * 填充色、边框色、内容对齐。
 */
data class PixelContainerStyle(
    val fillTone: PixelTone = PixelTone.OFF,
    val borderTone: PixelTone? = PixelTone.ON,
    val alignment: Alignment = Alignment.CENTER,
) {
    companion object {
        val Default = PixelContainerStyle()
    }
}

/**
 * 轻量主题入口。
 *
 * 这一版不做完整的 Flutter `Theme` 继承体系，先把页面层最常重复传递的
 * 文本、按钮、输入框和容器默认样式收敛到一个对象里。
 */
data class PixelThemeData(
    val textStyle: PixelTextStyle = PixelTextStyle.Default,
    val accentTextStyle: PixelTextStyle = PixelTextStyle.Accent,
    val buttonStyle: PixelButtonStyle = PixelButtonStyle.Default,
    val accentButtonStyle: PixelButtonStyle = PixelButtonStyle.Accent,
    val disabledButtonStyle: PixelButtonStyle = PixelButtonStyle.Disabled,
    val textFieldStyle: PixelTextFieldStyle = PixelTextFieldStyle.Default,
    val readOnlyTextFieldStyle: PixelTextFieldStyle = PixelTextFieldStyle.Default.copy(
        readOnlyBorderTone = PixelTone.ACCENT,
    ),
    val disabledTextFieldStyle: PixelTextFieldStyle = PixelTextFieldStyle.Default.copy(
        disabledBorderTone = PixelTone.ON,
        disabledTextStyle = PixelTextStyle(tone = PixelTone.OFF),
        disabledPlaceholderStyle = PixelTextStyle(tone = PixelTone.OFF),
    ),
    val containerStyle: PixelContainerStyle = PixelContainerStyle.Default,
    val accentContainerStyle: PixelContainerStyle = PixelContainerStyle(
        fillTone = PixelTone.OFF,
        borderTone = PixelTone.ACCENT,
        alignment = Alignment.CENTER,
    ),
) {
    companion object {
        val Default = PixelThemeData()
    }
}
