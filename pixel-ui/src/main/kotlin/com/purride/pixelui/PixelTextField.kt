package com.purride.pixelui

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState

/**
 * 文本输入样式。
 *
 * 第一版延续当前组件的最小风格模型：
 * 填充、边框、文本、占位文本和光标色都作为样式对象收敛。
 */
data class PixelTextFieldStyle(
    val fillTone: PixelTone = PixelTone.OFF,
    val borderTone: PixelTone? = PixelTone.ON,
    val focusedBorderTone: PixelTone? = PixelTone.ACCENT,
    val disabledBorderTone: PixelTone? = PixelTone.ON,
    val readOnlyBorderTone: PixelTone? = PixelTone.ACCENT,
    val textStyle: PixelTextStyle = PixelTextStyle.Default,
    val placeholderStyle: PixelTextStyle = PixelTextStyle(tone = PixelTone.ACCENT),
    val disabledTextStyle: PixelTextStyle = PixelTextStyle(tone = PixelTone.OFF),
    val disabledPlaceholderStyle: PixelTextStyle = PixelTextStyle(tone = PixelTone.OFF),
    val cursorTone: PixelTone = PixelTone.ACCENT,
    val padding: Int = 2,
) {
    companion object {
        val Default = PixelTextFieldStyle()
    }
}

/**
 * 文本输入节点。
 *
 * 第一版先聚焦单行输入和点击聚焦，不做多行排版。
 */
internal data class PixelTextFieldNode(
    override val key: Any? = null,
    override val modifier: PixelModifier = PixelModifier.Empty,
    val state: PixelTextFieldState,
    val controller: PixelTextFieldController,
    val placeholder: String = "",
    val style: PixelTextFieldStyle = PixelTextFieldStyle.Default,
    val styleLocked: Boolean = false,
    val enabled: Boolean = true,
    val readOnly: Boolean = false,
    val autofocus: Boolean = false,
    val textInputAction: PixelTextInputAction = PixelTextInputAction.DONE,
    val onChanged: ((String) -> Unit)? = null,
    val onSubmitted: ((String) -> Unit)? = null,
) : PixelNode

internal fun PixelTextField(
    state: PixelTextFieldState,
    controller: PixelTextFieldController,
    modifier: PixelModifier = PixelModifier.Empty,
    placeholder: String = "",
    style: PixelTextFieldStyle = PixelTextFieldStyle.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    autofocus: Boolean = false,
    textInputAction: PixelTextInputAction = PixelTextInputAction.DONE,
    onChanged: ((String) -> Unit)? = null,
    onSubmitted: ((String) -> Unit)? = null,
    key: Any? = null,
): PixelNode {
    return PixelTextFieldNode(
        key = key,
        modifier = modifier,
        state = state,
        controller = controller,
        placeholder = placeholder,
        style = style,
        enabled = enabled,
        readOnly = readOnly,
        autofocus = autofocus,
        textInputAction = textInputAction,
        onChanged = onChanged,
        onSubmitted = onSubmitted,
    )
}
