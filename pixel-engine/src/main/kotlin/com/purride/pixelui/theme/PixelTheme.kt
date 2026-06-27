package com.purride.pixelui

import com.purride.pixelcore.PixelColor

/**
 * Pixel UI 的可继承默认样式。
 *
 * 只覆盖 widget 层的像素视觉默认值；宿主显示、输入法、insets 和生命周期仍由
 * PixelHostView / PixelHostSetupConfig 管理。
 */
public data class PixelThemeData(
    val colors: PixelThemeColors = PixelThemeColors.Default,
    val textStyle: PixelTextStyle = PixelTextStyle.Default.copy(color = colors.text),
    val buttonStyle: PixelButtonStyle = PixelButtonStyle.Default.copy(
        borderColor = colors.border,
        textStyle = textStyle,
    ),
    val textButtonStyle: PixelTextButtonStyle = PixelTextButtonStyle.Default.copy(
        textStyle = textStyle,
    ),
    val textFieldStyle: PixelTextFieldStyle = PixelTextFieldStyle.Default.copy(
        borderColor = colors.border,
        focusedBorderColor = colors.selection,
        disabledBorderColor = colors.disabled,
        readOnlyBorderColor = colors.selection,
        textStyle = textStyle,
        placeholderStyle = textStyle.copy(color = colors.mutedText),
        disabledTextStyle = textStyle.copy(color = colors.disabled),
        disabledPlaceholderStyle = textStyle.copy(color = colors.disabled),
        cursorColor = colors.selection,
        selectionColor = colors.selection,
        compositionColor = colors.selection,
        selectionHandleColor = colors.selection,
    ),
) {
    public companion object {
        public val Default: PixelThemeData = PixelThemeData()
    }
}

/**
 * 像素控件共享调色板。
 */
public data class PixelThemeColors(
    val background: PixelColor = PixelColor.Black,
    val surface: PixelColor = PixelColor.Black,
    val text: PixelColor = PixelColor.White,
    val mutedText: PixelColor = PixelColor.fromRgb(160, 160, 160),
    val border: PixelColor = PixelColor.White,
    val accent: PixelColor = PixelColor.fromRgb(80, 180, 110),
    val danger: PixelColor = PixelColor.fromRgb(220, 90, 80),
    val warning: PixelColor = PixelColor.fromRgb(255, 200, 0),
    val disabled: PixelColor = PixelColor.fromRgb(80, 80, 80),
    val inactive: PixelColor = PixelColor.fromRgb(120, 120, 120),
    val track: PixelColor = PixelColor.fromRgb(60, 60, 60),
    val focus: PixelColor = PixelColor.fromRgb(255, 200, 0),
    val selection: PixelColor = PixelColor.fromRgb(255, 255, 0),
) {
    public companion object {
        public val Default: PixelThemeColors = PixelThemeColors()
    }
}

/**
 * 在 widget 子树中提供 [PixelThemeData]。
 */
public class PixelTheme(
    public val data: PixelThemeData,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return (oldWidget as? PixelTheme)?.data != data
    }

    public companion object {
        public fun maybeOf(context: BuildContext): PixelThemeData? {
            return context.dependOnInheritedWidgetOfExactType<PixelTheme>()?.data
        }

        public fun of(context: BuildContext): PixelThemeData {
            return maybeOf(context) ?: PixelThemeData.Default
        }
    }
}
