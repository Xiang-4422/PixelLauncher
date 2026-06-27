package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Container
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.FocusNode
import com.purride.pixelui.FocusNodeScope
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelInputType
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelTextFieldStyle
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.PixelTextButtonStyle
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.Semantics
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.Widget
import com.purride.pixelui.getInheritedWidgetOfExactType
import com.purride.pixelui.internal.toPixelAlignment
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState

/**
 * Flutter 风格 `TextField` 的 direct widget。
 */
internal data class TextFieldWidget(
    val state: PixelTextFieldState,
    val controller: PixelTextFieldController,
    val placeholder: String,
    val style: PixelTextFieldStyle,
    val enabled: Boolean,
    val readOnly: Boolean,
    val autofocus: Boolean,
    val minLines: Int,
    val maxLines: Int,
    val inputType: PixelInputType,
    val textAlign: TextAlign,
    val textInputAction: PixelTextInputAction,
    val onChanged: ((String) -> Unit)?,
    val onSubmitted: ((String) -> Unit)?,
    val fillColor: PixelColor? = null,
    val borderColor: PixelColor? = null,
    val focusNode: FocusNode? = null,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val themedStyle = if (style == PixelTextFieldStyle.Default) PixelTheme.of(context).textFieldStyle else style
        val effectiveFocusNode = focusNode ?: context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (effectiveFocusNode != null) {
            context.watch(effectiveFocusNode)
            if (effectiveFocusNode.isFocused && !state.isFocused && !state.focusRequested) {
                controller.requestFocus(state)
            }
        }
        val effectiveStyle = when {
            !enabled -> PixelTextFieldStyle(
                fillColor = themedStyle.fillColor,
                borderColor = themedStyle.disabledBorderColor,
                textStyle = themedStyle.disabledTextStyle,
                placeholderStyle = themedStyle.disabledPlaceholderStyle,
                padding = themedStyle.padding,
            )
            readOnly -> themedStyle.copy(borderColor = themedStyle.readOnlyBorderColor)
            state.isFocused -> themedStyle.copy(borderColor = themedStyle.focusedBorderColor)
            else -> themedStyle
        }
        val safeMinLines = minLines.coerceAtLeast(1)
        val safeMaxLines = maxLines.coerceAtLeast(safeMinLines)
        controller.syncCursorBlinkConfig(
            state = state,
            enabled = enabled && !readOnly && themedStyle.cursorBlinkEnabled,
            periodMs = themedStyle.cursorBlinkPeriodMs,
        )
        val text = state.text.ifEmpty { placeholder }
        val textStyle = when {
            !enabled && state.text.isEmpty() -> effectiveStyle.placeholderStyle
            !enabled -> effectiveStyle.textStyle
            state.text.isEmpty() -> themedStyle.placeholderStyle
            else -> themedStyle.textStyle
        }
        return TextInputSurfaceWidget(
            fillColor = fillColor ?: effectiveStyle.fillColor,
            borderColor = borderColor ?: effectiveStyle.borderColor,
            padding = themedStyle.padding,
            alignment = when (textAlign) {
                TextAlign.START -> Alignment.CENTER_START
                TextAlign.CENTER -> Alignment.CENTER
                TextAlign.END -> Alignment.CENTER_END
            },
            state = state,
            controller = controller,
            readOnly = readOnly || !enabled,
            autofocus = autofocus,
            minLines = safeMinLines,
            maxLines = safeMaxLines,
            inputType = inputType,
            textInputAction = textInputAction,
            focusNode = effectiveFocusNode,
            onChanged = onChanged,
            onSubmitted = onSubmitted,
            cursorColor = if (enabled && !readOnly) themedStyle.cursorColor else null,
            cursorVisible = state.cursorVisible,
            selectionColor = if (enabled && !readOnly) themedStyle.selectionColor else null,
            compositionColor = if (enabled && !readOnly) themedStyle.compositionColor else null,
            selectionHandleColor = if (enabled && !readOnly && themedStyle.selectionHandlesEnabled) themedStyle.selectionHandleColor else null,
            key = key,
            child = TextWidget(
                data = text,
                style = textStyle,
                softWrap = safeMaxLines > 1,
                maxLines = safeMaxLines,
                overflow = if (safeMaxLines > 1) PixelTextOverflow.CLIP else PixelTextOverflow.ELLIPSIS,
                textAlign = textAlign,
                key = key?.let { "$it-text" },
            ),
        )
    }
}

/**
 * TextField 视觉和文本输入目标导出的 direct render object widget。
 */
private data class TextInputSurfaceWidget(
    override val child: Widget?,
    val fillColor: PixelColor? = null,
    val borderColor: PixelColor? = null,
    val padding: Int,
    val alignment: Alignment,
    val state: PixelTextFieldState,
    val controller: PixelTextFieldController,
    val readOnly: Boolean,
    val autofocus: Boolean,
    val minLines: Int,
    val maxLines: Int,
    val inputType: PixelInputType,
    val textInputAction: PixelTextInputAction,
    val focusNode: FocusNode?,
    val onChanged: ((String) -> Unit)?,
    val onSubmitted: ((String) -> Unit)?,
    val cursorColor: PixelColor?,
    val cursorVisible: Boolean,
    val selectionColor: PixelColor?,
    val compositionColor: PixelColor?,
    val selectionHandleColor: PixelColor?,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderSurface(
            fillColor = fillColor,
            borderColor = borderColor,
            alignment = alignment.toPixelAlignment(),
            contentPaddingLeft = padding,
            contentPaddingTop = padding,
            contentPaddingRight = padding,
            contentPaddingBottom = padding,
            textInputState = state,
            textInputController = controller,
            textInputReadOnly = readOnly,
            textInputAutofocus = autofocus,
            textInputMinLines = minLines,
            textInputMaxLines = maxLines,
            textInputType = inputType,
            textInputAction = textInputAction,
            textInputFocusNode = focusNode,
            textInputOnChanged = onChanged,
            textInputOnSubmitted = onSubmitted,
            textInputCursorColor = cursorColor,
            textInputCursorVisible = cursorVisible,
            textInputSelectionColor = selectionColor,
            textInputCompositionColor = compositionColor,
            textInputSelectionHandleColor = selectionHandleColor,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderSurface).updateSurface(
            fillColor = fillColor,
            borderColor = borderColor,
            alignment = alignment.toPixelAlignment(),
            contentPaddingLeft = padding,
            contentPaddingTop = padding,
            contentPaddingRight = padding,
            contentPaddingBottom = padding,
            textInputState = state,
            textInputController = controller,
            textInputReadOnly = readOnly,
            textInputAutofocus = autofocus,
            textInputMinLines = minLines,
            textInputMaxLines = maxLines,
            textInputType = inputType,
            textInputAction = textInputAction,
            textInputFocusNode = focusNode,
            textInputOnChanged = onChanged,
            textInputOnSubmitted = onSubmitted,
            textInputCursorColor = cursorColor,
            textInputCursorVisible = cursorVisible,
            textInputSelectionColor = selectionColor,
            textInputCompositionColor = compositionColor,
            textInputSelectionHandleColor = selectionHandleColor,
        )
    }
}

/**
 * Flutter 风格 `OutlinedButton` 的 direct widget。
 */
internal data class OutlinedButtonWidget(
    val text: String,
    val onPressed: (() -> Unit)?,
    val style: PixelButtonStyle,
    val enabled: Boolean,
    val fillColor: PixelColor? = null,
    val borderColor: PixelColor? = null,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        val effectiveEnabled = enabled && onPressed != null
        val theme = PixelTheme.of(context)
        val themedStyle = if (style == PixelButtonStyle.Default) theme.buttonStyle else style
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) {
            context.watch(focusNode)
        }
        val focused = effectiveEnabled && focusNode?.isFocused == true
        val effectiveFill = fillColor ?: themedStyle.fillColor
        val effectiveTextStyle = if (!effectiveEnabled && style == PixelButtonStyle.Default) {
            themedStyle.textStyle.copy(color = theme.colors.disabled)
        } else {
            themedStyle.textStyle
        }
        val effectiveBorder = borderColor ?: when {
            !effectiveEnabled && style == PixelButtonStyle.Default -> theme.colors.disabled
            focused -> theme.colors.focus
            else -> themedStyle.borderColor
        }
        val content = Container(
            fillColor = effectiveFill,
            borderColor = effectiveBorder,
            padding = EdgeInsets.all(OUTLINED_BUTTON_PADDING_PX),
            alignment = themedStyle.alignment,
            key = key,
            child = Text(
                text,
                style = effectiveTextStyle,
                overflow = PixelTextOverflow.ELLIPSIS,
                textAlign = TextAlign.CENTER,
                key = key?.let { "$it-text" },
            ),
        )
        val button = if (effectiveEnabled) {
            GestureDetector(child = content, onTap = onPressed ?: {}, key = key)
        } else {
            content
        }
        return Semantics(
            label = text,
            role = PixelSemanticRole.BUTTON,
            enabled = effectiveEnabled,
            focused = focused,
            child = button,
            key = key?.let { "$it-semantics" },
        )
    }
}

/** 无边框、零默认 padding 的文字按钮。 */
internal data class TextButtonWidget(
    val text: String,
    val onPressed: (() -> Unit)?,
    val style: PixelTextButtonStyle,
    val enabled: Boolean,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        val effectiveEnabled = enabled && onPressed != null
        val theme = PixelTheme.of(context)
        val themedStyle = if (style == PixelTextButtonStyle.Default) theme.textButtonStyle else style
        val effectiveTextStyle = if (!effectiveEnabled && style == PixelTextButtonStyle.Default) {
            themedStyle.textStyle.copy(color = theme.colors.disabled)
        } else {
            themedStyle.textStyle
        }
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) {
            context.watch(focusNode)
        }
        val focused = effectiveEnabled && focusNode?.isFocused == true
        val content = Container(
            padding = themedStyle.padding,
            alignment = themedStyle.alignment,
            key = key,
            child = Text(
                text,
                style = effectiveTextStyle,
                overflow = PixelTextOverflow.ELLIPSIS,
                softWrap = false,
                maxLines = 1,
                textAlign = TextAlign.CENTER,
                key = key?.let { "$it-text" },
            ),
        )
        val button = if (effectiveEnabled) {
            GestureDetector(child = content, onTap = onPressed ?: {}, key = key)
        } else {
            content
        }
        return Semantics(
            label = text,
            role = PixelSemanticRole.BUTTON,
            enabled = effectiveEnabled,
            focused = focused,
            child = button,
            key = key?.let { "$it-semantics" },
        )
    }
}

private const val OUTLINED_BUTTON_PADDING_PX: Int = 2
