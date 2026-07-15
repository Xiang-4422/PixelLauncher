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
    /** 集中提供 `PixelTheme` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 提供 `PixelTheme` 的 `Default` 稳定默认值或常量。 */
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
    /** 集中提供 `PixelTheme` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 提供 `PixelTheme` 的 `Default` 稳定默认值或常量。 */
        public val Default: PixelThemeColors = PixelThemeColors()
    }
}

/**
 * 定义 `PixelTheme` 在 `PixelTheme` 中承担的数据与行为边界。
 *
 * Provides both legacy [PixelThemeData] and complete [PixelThemeTokens] to a widget subtree.
 *
 * The public legacy constructor remains a real JVM secondary constructor so already compiled
 * consumers keep the original `(PixelThemeData, Widget, Object)` descriptor.
 */
public class PixelTheme private constructor(
    /** 保存 `PixelTheme` 对外传递的 `data` 数据。 */
    public val data: PixelThemeData,
    /** 公开 `PixelTheme` 的 `tokens` 配置或运行值。
 *
 * Complete semantic token graph inherited by new standard components.
 */
    public val tokens: PixelThemeTokens,
    override val child: Widget,
    override val key: Any?,
) : InheritedWidget(child = child, key = key) {
    /** 创建 `PixelTheme` 实例并建立初始不变量。
 *
 * Creates a provider from the unchanged legacy theme model and constructor descriptor.
 */
    public constructor(
        data: PixelThemeData,
        child: Widget,
        key: Any? = null,
    ) : this(
        data = data,
        tokens = PixelThemeTokens.fromLegacy(data),
        child = child,
        key = key,
    )

    /** 创建 `PixelTheme` 实例并建立初始不变量。
 *
 * Creates a provider from the complete token graph and projects legacy styles once.
 */
    public constructor(
        tokens: PixelThemeTokens,
        child: Widget,
        key: Any? = null,
    ) : this(
        data = tokens.toLegacyThemeData(),
        tokens = tokens,
        child = child,
        key = key,
    )

    /** Notifies legacy and token consumers when either immutable theme representation changes. */
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        val oldTheme = oldWidget as? PixelTheme ?: return true
        return oldTheme.data != data || oldTheme.tokens != tokens
    }

    /** 集中提供 `PixelTheme` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 执行 `PixelTheme` 的 `maybeOf` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest legacy theme data, or null when no PixelTheme is inherited.
 */
        public fun maybeOf(context: BuildContext): PixelThemeData? {
            return context.dependOnInheritedWidgetOfExactType<PixelTheme>()?.data
        }

        /** 执行 `PixelTheme` 的 `of` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest legacy theme data or [PixelThemeData.Default].
 */
        public fun of(context: BuildContext): PixelThemeData {
            return maybeOf(context) ?: PixelThemeData.Default
        }

        /** 执行 `PixelTheme` 的 `maybeTokensOf` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest complete token graph, or null when no PixelTheme is inherited.
 */
        public fun maybeTokensOf(context: BuildContext): PixelThemeTokens? {
            return context.dependOnInheritedWidgetOfExactType<PixelTheme>()?.tokens
        }

        /** 执行 `PixelTheme` 的 `tokensOf` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest complete token graph or [PixelThemeTokens.Default].
 */
        public fun tokensOf(context: BuildContext): PixelThemeTokens {
            return maybeTokensOf(context) ?: PixelThemeTokens.Default
        }
    }
}
