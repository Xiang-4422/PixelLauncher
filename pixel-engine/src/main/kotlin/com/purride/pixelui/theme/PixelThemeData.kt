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
 * 主题 token。
 *
 * token 是组件样式没有显式指定时的下一层默认来源，用来把基础色阶和
 * 常见状态色集中起来，避免每个组件都内联自己的硬编码默认值。
 */
data class PixelThemeTokens(
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
) {
    companion object {
        val Default = PixelThemeTokens()
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

    companion object {
        val Default = PixelThemeData()
    }
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
