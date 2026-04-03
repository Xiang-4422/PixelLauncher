package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelTextFieldStyle
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelButton
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelTextField
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState

/**
 * Flutter 风格 `TextField` 的 legacy bridge widget。
 */
internal data class TextFieldWidget(
    val state: PixelTextFieldState,
    val controller: PixelTextFieldController,
    val placeholder: String,
    val style: PixelTextFieldStyle,
    val theme: com.purride.pixelui.PixelThemeData?,
    val enabled: Boolean,
    val readOnly: Boolean,
    val autofocus: Boolean,
    val textInputAction: PixelTextInputAction,
    val onChanged: ((String) -> Unit)?,
    val onSubmitted: ((String) -> Unit)?,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 在 build 时解析输入框样式并注册 controller 依赖。
     */
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val resolvedTheme = context.resolveTheme(theme)
        val resolvedStyle = when {
            style != PixelTextFieldStyle.Default -> style
            !enabled -> resolvedTheme.disabledTextFieldStyle
            readOnly -> resolvedTheme.readOnlyTextFieldStyle
            else -> resolvedTheme.textFieldStyle
        }
        return LegacyLeafWidget(
            key = key,
        ) {
            PixelTextField(
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty,
                placeholder = placeholder,
                style = resolvedStyle,
                enabled = enabled,
                readOnly = readOnly,
                autofocus = autofocus,
                textInputAction = textInputAction,
                onChanged = onChanged,
                onSubmitted = onSubmitted,
                key = key,
            )
        }
    }
}

/**
 * Flutter 风格 `OutlinedButton` 的 legacy bridge widget。
 */
internal data class OutlinedButtonWidget(
    val text: String,
    val onPressed: (() -> Unit)?,
    val style: PixelButtonStyle,
    val theme: com.purride.pixelui.PixelThemeData?,
    val enabled: Boolean,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 在 build 时解析按钮样式并生成 legacy 按钮节点。
     */
    override fun build(context: BuildContext): Widget {
        val resolvedTheme = context.resolveTheme(theme)
        val resolvedStyle = when (style) {
            PixelButtonStyle.Default -> resolvedTheme.buttonStyle
            PixelButtonStyle.Accent -> resolvedTheme.accentButtonStyle
            else -> style
        }
        return LegacyLeafWidget(
            key = key,
        ) {
            PixelButton(
                text = text,
                onClick = onPressed,
                modifier = PixelModifier.Empty,
                style = resolvedStyle,
                disabledStyle = resolvedTheme.disabledButtonStyle,
                enabled = enabled,
                key = key,
            )
        }
    }
}
