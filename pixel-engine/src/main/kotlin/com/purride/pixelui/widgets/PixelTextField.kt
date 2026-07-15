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
    /**
     * 非空 selection（selectionStart != selectionEnd）下选区的填充色。
     * 默认与 cursorColor 同色，业务可改成主题强调色。
     */
    val selectionColor: PixelColor = PixelColor.fromRgb(255, 255, 0),
    /**
     * IME composition 区段下方的 1px 下划线颜色。仅在 state.compositionStart
     * < compositionEnd 且 widget 聚焦时绘制。默认与 cursorColor 同色。
     */
    val compositionColor: PixelColor = PixelColor.fromRgb(255, 255, 0),
    /**
     * 非空 selection 两端最小拖拽 handle 的颜色。
     */
    val selectionHandleColor: PixelColor = PixelColor.fromRgb(255, 255, 0),
    /**
     * 是否绘制 selection handle。仅在 enabled 且非 readOnly 且 selection 非空时生效。
     */
    val selectionHandlesEnabled: Boolean = true,
    val cursorBlinkEnabled: Boolean = true,
    val cursorBlinkPeriodMs: Long = 1_000L,
    val padding: Int = 2,
) {
    /** 集中提供 `PixelTextField` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 提供 `PixelTextField` 的 `Default` 稳定默认值或常量。 */
        public val Default: PixelTextFieldStyle = PixelTextFieldStyle()
    }
}
