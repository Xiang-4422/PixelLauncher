package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelTone

/**
 * 容器样式。
 *
 * 当前先把 `Container` 最常用的三项视觉参数收进样式对象：
 * 填充色、边框色、内容对齐。
 */
public data class PixelContainerStyle(
    val fillTone: PixelTone = PixelTone.OFF,
    val borderTone: PixelTone? = PixelTone.ON,
    val alignment: Alignment = Alignment.CENTER,
) {
    public companion object {
        public val Default: PixelContainerStyle = PixelContainerStyle()
    }
}

/**
 * 主题 token——pixel-engine 样式系统的唯一真相来源。
 *
 * 设计原则：
 * - **tokens 优先**：所有 widget 的默认样式都从 tokens 派生（textTone /
 *   accentTone / borderTone / disabledBorderTone 等），改一个 token 就能
 *   全局变色，不需要逐个 widget 配置。
 * - **可显式覆盖**：[PixelThemeData] 同时持有具体 style 对象作为可选覆盖：
 *   只要某个 style 字段保持构造默认值，就走 tokens 推导；如果显式传了
 *   不同的 style 对象，那个对象直接生效。
 * - **典型用法**：
 *   ```kotlin
 *   // 改全局色阶：只动 tokens
 *   val theme = PixelThemeData(
 *       tokens = PixelThemeTokens.Default.copy(textTone = PixelTone.ACCENT),
 *   )
 *   // 单点定制按钮：覆盖 buttonStyle
 *   val theme2 = PixelThemeData(
 *       buttonStyle = PixelButtonStyle(fillTone = PixelTone.ACCENT, ...),
 *   )
 *   ```
 *
 * 字段语义按"基础色阶 + 状态色"两组组织：
 * - 基础：textTone / accentTone / mutedTone / surfaceTone / borderTone
 * - 状态：accentBorderTone / selectedBorderTone / pressedBorderTone /
 *   focusedBorderTone / disabledBorderTone / readOnlyBorderTone
 * - 输入间距：inputPadding
 */
public data class PixelThemeTokens(
    val textTone: PixelTone = PixelTone.ON,
    val accentTone: PixelTone = PixelTone.ACCENT,
    val mutedTone: PixelTone = PixelTone.OFF,
    val surfaceTone: PixelTone = PixelTone.OFF,
    val borderTone: PixelTone? = PixelTone.ON,
    val accentBorderTone: PixelTone? = PixelTone.ACCENT,
    val selectedBorderTone: PixelTone? = PixelTone.ACCENT,
    val pressedBorderTone: PixelTone? = PixelTone.ACCENT,
    val focusedBorderTone: PixelTone? = PixelTone.ACCENT,
    val disabledBorderTone: PixelTone? = PixelTone.ON,
    val readOnlyBorderTone: PixelTone? = PixelTone.ACCENT,
    val inputPadding: Int = 2,
    val textColor: PixelColor? = null,
    val accentColor: PixelColor? = null,
    val mutedColor: PixelColor? = null,
    val surfaceColor: PixelColor? = null,
    val borderColor: PixelColor? = null,
    val accentBorderColor: PixelColor? = null,
    val selectedBorderColor: PixelColor? = null,
    val pressedBorderColor: PixelColor? = null,
    val focusedBorderColor: PixelColor? = null,
    val disabledBorderColor: PixelColor? = null,
    val readOnlyBorderColor: PixelColor? = null,
) {
    public companion object {
        public val Default: PixelThemeTokens = PixelThemeTokens()

        public val Dark: PixelThemeTokens = PixelThemeTokens(
            textColor = PixelColor.fromRgb(220, 220, 220),
            accentColor = PixelColor.fromRgb(255, 165, 0),
            mutedColor = PixelColor.fromRgb(80, 80, 80),
            surfaceColor = PixelColor.fromRgb(24, 24, 24),
            borderColor = PixelColor.fromRgb(160, 160, 160),
            accentBorderColor = PixelColor.fromRgb(255, 165, 0),
            selectedBorderColor = PixelColor.fromRgb(255, 200, 50),
            pressedBorderColor = PixelColor.fromRgb(200, 120, 0),
            focusedBorderColor = PixelColor.fromRgb(255, 165, 0),
            disabledBorderColor = PixelColor.fromRgb(80, 80, 80),
            readOnlyBorderColor = PixelColor.fromRgb(140, 140, 140),
        )

        public val Light: PixelThemeTokens = PixelThemeTokens(
            textColor = PixelColor.fromRgb(30, 30, 30),
            accentColor = PixelColor.fromRgb(0, 100, 200),
            mutedColor = PixelColor.fromRgb(180, 180, 180),
            surfaceColor = PixelColor.fromRgb(245, 245, 245),
            borderColor = PixelColor.fromRgb(80, 80, 80),
            accentBorderColor = PixelColor.fromRgb(0, 100, 200),
            selectedBorderColor = PixelColor.fromRgb(0, 140, 255),
            pressedBorderColor = PixelColor.fromRgb(0, 70, 150),
            focusedBorderColor = PixelColor.fromRgb(0, 100, 200),
            disabledBorderColor = PixelColor.fromRgb(180, 180, 180),
            readOnlyBorderColor = PixelColor.fromRgb(120, 120, 120),
        )

        public val Ocean: PixelThemeTokens = PixelThemeTokens(
            textColor = PixelColor.fromRgb(200, 240, 255),
            accentColor = PixelColor.fromRgb(0, 210, 180),
            mutedColor = PixelColor.fromRgb(40, 80, 100),
            surfaceColor = PixelColor.fromRgb(10, 30, 50),
            borderColor = PixelColor.fromRgb(60, 160, 180),
            accentBorderColor = PixelColor.fromRgb(0, 210, 180),
            selectedBorderColor = PixelColor.fromRgb(100, 230, 200),
            pressedBorderColor = PixelColor.fromRgb(0, 160, 140),
            focusedBorderColor = PixelColor.fromRgb(0, 210, 180),
            disabledBorderColor = PixelColor.fromRgb(40, 80, 100),
            readOnlyBorderColor = PixelColor.fromRgb(80, 140, 160),
        )

        public val Amber: PixelThemeTokens = PixelThemeTokens(
            textColor = PixelColor.fromRgb(255, 240, 200),
            accentColor = PixelColor.fromRgb(255, 200, 0),
            mutedColor = PixelColor.fromRgb(80, 60, 20),
            surfaceColor = PixelColor.fromRgb(30, 20, 5),
            borderColor = PixelColor.fromRgb(180, 140, 40),
            accentBorderColor = PixelColor.fromRgb(255, 200, 0),
            selectedBorderColor = PixelColor.fromRgb(255, 220, 80),
            pressedBorderColor = PixelColor.fromRgb(200, 150, 0),
            focusedBorderColor = PixelColor.fromRgb(255, 200, 0),
            disabledBorderColor = PixelColor.fromRgb(80, 60, 20),
            readOnlyBorderColor = PixelColor.fromRgb(140, 110, 40),
        )
    }
}

/**
 * 主题入口对象。
 *
 * pixel-engine 的主题系统遵循"**tokens 是唯一真相来源**"原则。本类
 * 持有的 16 个具体 style 字段（textStyle / buttonStyle / containerStyle
 * 等）都是**可选的局部覆盖**：
 *
 * - 用户未显式传值时，每个 style 字段保持构造默认值（例如
 *   `PixelTextStyle.Default`、`PixelButtonStyle.Default`）。`resolveXxx`
 *   会检测出这是默认值，**改从 [tokens] 派生**对应样式。
 * - 用户显式传入不同的 style 对象时，那个对象**直接生效**，跳过 tokens
 *   推导。
 *
 * **推荐用法**：优先改 tokens 实现全局换肤，只在需要"某一类组件
 * 与其他组件视觉显著不同"时才传 style 覆盖。
 *
 * ```kotlin
 * // 全局换肤：所有 tone 跟着 accentTone 变
 * val accentTheme = PixelThemeData(
 *     tokens = PixelThemeTokens.Default.copy(
 *         textTone = PixelTone.ACCENT,
 *         borderTone = PixelTone.ACCENT,
 *     ),
 * )
 *
 * // 单 widget 覆盖：除按钮外其他样式都跟 tokens
 * val accentButtonOnly = PixelThemeData(
 *     buttonStyle = PixelButtonStyle(
 *         fillTone = PixelTone.ACCENT,
 *         borderTone = null,
 *         textStyle = PixelTextStyle(tone = PixelTone.OFF),
 *     ),
 * )
 * ```
 *
 * 局部修改 tokens 时可以用便利扩展 [withTokens]，比手写
 * `theme.copy(tokens = theme.tokens.copy(...))` 更直观。
 */
public data class PixelThemeData(
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
    val selectedButtonStyle: PixelButtonStyle = PixelButtonStyle.Default,
    val pressedButtonStyle: PixelButtonStyle = PixelButtonStyle.Default,
    val selectedContainerStyle: PixelContainerStyle = PixelContainerStyle.Default,
    val pressedContainerStyle: PixelContainerStyle = PixelContainerStyle.Default,
    val tokens: PixelThemeTokens = PixelThemeTokens.Default,
) {
    internal fun resolveTextStyle(): PixelTextStyle {
        return textStyle.resolveTokenDefault(
            PixelTextStyle(tone = tokens.textTone),
            PixelTextStyle.Default,
        )
    }

    internal fun resolveAccentTextStyle(): PixelTextStyle {
        return accentTextStyle.resolveTokenDefault(
            PixelTextStyle(tone = tokens.accentTone),
            PixelTextStyle.Accent,
        )
    }

    internal fun resolveButtonStyle(): PixelButtonStyle {
        return buttonStyle.resolveTokenDefault(
            PixelButtonStyle(
                fillTone = tokens.surfaceTone,
                borderTone = tokens.borderTone,
                textStyle = PixelTextStyle(tone = tokens.textTone),
            ),
            PixelButtonStyle.Default,
        )
    }

    internal fun resolveAccentButtonStyle(): PixelButtonStyle {
        return accentButtonStyle.resolveTokenDefault(
            PixelButtonStyle(
                fillTone = tokens.surfaceTone,
                borderTone = tokens.accentBorderTone,
                textStyle = PixelTextStyle(tone = tokens.accentTone),
            ),
            PixelButtonStyle.Accent,
        )
    }

    internal fun resolveSelectedButtonStyle(): PixelButtonStyle {
        return selectedButtonStyle.resolveTokenDefault(
            PixelButtonStyle(
                fillTone = tokens.surfaceTone,
                borderTone = tokens.selectedBorderTone,
                textStyle = PixelTextStyle(tone = tokens.accentTone),
            ),
            PixelButtonStyle.Default,
        )
    }

    internal fun resolvePressedButtonStyle(): PixelButtonStyle {
        return pressedButtonStyle.resolveTokenDefault(
            PixelButtonStyle(
                fillTone = tokens.surfaceTone,
                borderTone = tokens.pressedBorderTone,
                textStyle = PixelTextStyle(tone = tokens.accentTone),
            ),
            PixelButtonStyle.Default,
        )
    }

    internal fun resolveDisabledButtonStyle(): PixelButtonStyle {
        return disabledButtonStyle.resolveTokenDefault(
            PixelButtonStyle(
                fillTone = tokens.surfaceTone,
                borderTone = tokens.disabledBorderTone,
                textStyle = PixelTextStyle(tone = tokens.mutedTone),
            ),
            PixelButtonStyle.Disabled,
        )
    }

    internal fun resolveTextFieldStyle(): PixelTextFieldStyle {
        return textFieldStyle.resolveTokenDefault(
            tokenTextFieldStyle(
                borderTone = tokens.borderTone,
                textTone = tokens.textTone,
                placeholderTone = tokens.accentTone,
            ),
            PixelTextFieldStyle.Default,
        )
    }

    internal fun resolveReadOnlyTextFieldStyle(): PixelTextFieldStyle {
        return readOnlyTextFieldStyle.resolveTokenDefault(
            tokenTextFieldStyle(
                borderTone = tokens.borderTone,
                textTone = tokens.textTone,
                placeholderTone = tokens.accentTone,
                readOnlyBorderTone = tokens.readOnlyBorderTone,
            ),
            PixelTextFieldStyle.Default.copy(readOnlyBorderTone = PixelTone.ACCENT),
        )
    }

    internal fun resolveDisabledTextFieldStyle(): PixelTextFieldStyle {
        return disabledTextFieldStyle.resolveTokenDefault(
            tokenTextFieldStyle(
                borderTone = tokens.disabledBorderTone,
                textTone = tokens.mutedTone,
                placeholderTone = tokens.mutedTone,
                focusedBorderTone = tokens.disabledBorderTone,
                disabledBorderTone = tokens.disabledBorderTone,
            ),
            PixelTextFieldStyle.Default.copy(
                disabledBorderTone = PixelTone.ON,
                disabledTextStyle = PixelTextStyle(tone = PixelTone.OFF),
                disabledPlaceholderStyle = PixelTextStyle(tone = PixelTone.OFF),
            ),
        )
    }

    internal fun resolveContainerStyle(): PixelContainerStyle {
        return containerStyle.resolveTokenDefault(
            PixelContainerStyle(
                fillTone = tokens.surfaceTone,
                borderTone = tokens.borderTone,
                alignment = Alignment.CENTER,
            ),
            PixelContainerStyle.Default,
        )
    }

    internal fun resolveAccentContainerStyle(): PixelContainerStyle {
        return accentContainerStyle.resolveTokenDefault(
            PixelContainerStyle(
                fillTone = tokens.surfaceTone,
                borderTone = tokens.accentBorderTone,
                alignment = Alignment.CENTER,
            ),
            PixelContainerStyle(
                fillTone = PixelTone.OFF,
                borderTone = PixelTone.ACCENT,
                alignment = Alignment.CENTER,
            ),
        )
    }

    internal fun resolveSelectedContainerStyle(): PixelContainerStyle {
        return selectedContainerStyle.resolveTokenDefault(
            PixelContainerStyle(
                fillTone = tokens.surfaceTone,
                borderTone = tokens.selectedBorderTone,
                alignment = Alignment.CENTER,
            ),
            PixelContainerStyle.Default,
        )
    }

    internal fun resolvePressedContainerStyle(): PixelContainerStyle {
        return pressedContainerStyle.resolveTokenDefault(
            PixelContainerStyle(
                fillTone = tokens.surfaceTone,
                borderTone = tokens.pressedBorderTone,
                alignment = Alignment.CENTER,
            ),
            PixelContainerStyle.Default,
        )
    }

    public companion object {
        public val Default: PixelThemeData = PixelThemeData()
    }
}

/**
 * 局部修改主题 tokens 的便利扩展。
 *
 * 等价于 `copy(tokens = tokens.transform())`，但读起来更顺：
 *
 * ```kotlin
 * val accentTheme = PixelThemeData.Default.withTokens {
 *     copy(textTone = PixelTone.ACCENT, borderTone = PixelTone.ACCENT)
 * }
 * ```
 *
 * 注意：本函数只动 [PixelThemeData.tokens]，不动 style 覆盖字段。
 * 若 widget 已经被显式 style 覆盖，token 改动对它不生效——这是设计意图。
 */
public inline fun PixelThemeData.withTokens(
    transform: PixelThemeTokens.() -> PixelThemeTokens,
): PixelThemeData {
    return copy(tokens = tokens.transform())
}

private fun PixelTextStyle.resolveTokenDefault(
    tokenDefault: PixelTextStyle,
    hardcodedDefault: PixelTextStyle,
): PixelTextStyle {
    return if (this == hardcodedDefault) tokenDefault else this
}

private fun PixelButtonStyle.resolveTokenDefault(
    tokenDefault: PixelButtonStyle,
    hardcodedDefault: PixelButtonStyle,
): PixelButtonStyle {
    return if (this == hardcodedDefault) tokenDefault else this
}

private fun PixelContainerStyle.resolveTokenDefault(
    tokenDefault: PixelContainerStyle,
    hardcodedDefault: PixelContainerStyle,
): PixelContainerStyle {
    return if (this == hardcodedDefault) tokenDefault else this
}

private fun PixelTextFieldStyle.resolveTokenDefault(
    tokenDefault: PixelTextFieldStyle,
    hardcodedDefault: PixelTextFieldStyle,
): PixelTextFieldStyle {
    return if (this == hardcodedDefault) tokenDefault else this
}

private fun PixelThemeData.tokenTextFieldStyle(
    borderTone: PixelTone?,
    textTone: PixelTone,
    placeholderTone: PixelTone,
    focusedBorderTone: PixelTone? = tokens.focusedBorderTone,
    disabledBorderTone: PixelTone? = tokens.disabledBorderTone,
    readOnlyBorderTone: PixelTone? = tokens.readOnlyBorderTone,
): PixelTextFieldStyle {
    return PixelTextFieldStyle(
        fillTone = tokens.surfaceTone,
        borderTone = borderTone,
        focusedBorderTone = focusedBorderTone,
        disabledBorderTone = disabledBorderTone,
        readOnlyBorderTone = readOnlyBorderTone,
        textStyle = PixelTextStyle(tone = textTone),
        placeholderStyle = PixelTextStyle(tone = placeholderTone),
        disabledTextStyle = PixelTextStyle(tone = tokens.mutedTone),
        disabledPlaceholderStyle = PixelTextStyle(tone = tokens.mutedTone),
        cursorTone = tokens.accentTone,
        padding = tokens.inputPadding,
    )
}
