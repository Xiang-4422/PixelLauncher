package com.purride.pixelui.internal.legacy

import com.purride.pixelui.PixelTextFieldStyle
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState

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
