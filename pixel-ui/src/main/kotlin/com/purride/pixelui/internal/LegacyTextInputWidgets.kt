package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Directionality
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelTextFieldStyle
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.TextAlign
import com.purride.pixelui.internal.legacy.PixelButton
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelText
import com.purride.pixelui.internal.legacy.PixelTextField
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixelui.toPixelTextAlign

internal data class TextWidget(
    val data: String,
    val style: PixelTextStyle,
    val theme: com.purride.pixelui.PixelThemeData?,
    val softWrap: Boolean,
    val maxLines: Int,
    val overflow: PixelTextOverflow,
    val textAlign: TextAlign,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): com.purride.pixelui.Widget {
        val resolvedTheme = context.resolveTheme(theme)
        val resolvedStyle = when (style) {
            PixelTextStyle.Default -> resolvedTheme.textStyle
            PixelTextStyle.Accent -> resolvedTheme.accentTextStyle
            else -> style
        }
        return LegacyLeafWidget(
            key = key,
        ) {
            PixelText(
                text = data,
                modifier = PixelModifier.Empty,
                style = resolvedStyle,
                softWrap = softWrap,
                maxLines = maxLines,
                overflow = overflow,
                textAlign = textAlign.toPixelTextAlign(),
                textDirection = Directionality.of(context),
                key = key,
            )
        }
    }
}

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
    override fun build(context: BuildContext): com.purride.pixelui.Widget {
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
    override fun build(context: BuildContext): com.purride.pixelui.Widget {
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
