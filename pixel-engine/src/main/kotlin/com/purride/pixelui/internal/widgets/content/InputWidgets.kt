package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelInputType
import com.purride.pixelui.PixelTextFieldStyle
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.TextAlign
import com.purride.pixelui.Widget
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
    val textInputAction: PixelTextInputAction,
    val onChanged: ((String) -> Unit)?,
    val onSubmitted: ((String) -> Unit)?,
    val fillColor: PixelColor? = null,
    val borderColor: PixelColor? = null,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val effectiveStyle = when {
            !enabled -> PixelTextFieldStyle(
                fillColor = style.fillColor,
                borderColor = style.disabledBorderColor,
                textStyle = style.disabledTextStyle,
                placeholderStyle = style.disabledPlaceholderStyle,
                padding = style.padding,
            )
            readOnly -> style.copy(borderColor = style.readOnlyBorderColor)
            state.isFocused -> style.copy(borderColor = style.focusedBorderColor)
            else -> style
        }
        val safeMinLines = minLines.coerceAtLeast(1)
        val safeMaxLines = maxLines.coerceAtLeast(safeMinLines)
        controller.syncCursorBlinkConfig(
            state = state,
            enabled = enabled && !readOnly && style.cursorBlinkEnabled,
            periodMs = style.cursorBlinkPeriodMs,
        )
        val text = state.text.ifEmpty { placeholder }
        val textStyle = when {
            !enabled && state.text.isEmpty() -> effectiveStyle.placeholderStyle
            !enabled -> effectiveStyle.textStyle
            state.text.isEmpty() -> style.placeholderStyle
            else -> style.textStyle
        }
        return TextInputSurfaceWidget(
            fillColor = fillColor ?: effectiveStyle.fillColor,
            borderColor = borderColor ?: effectiveStyle.borderColor,
            padding = style.padding,
            alignment = Alignment.CENTER_START,
            state = state,
            controller = controller,
            readOnly = readOnly,
            autofocus = autofocus,
            minLines = safeMinLines,
            maxLines = safeMaxLines,
            inputType = inputType,
            textInputAction = textInputAction,
            onChanged = onChanged,
            onSubmitted = onSubmitted,
            cursorColor = if (enabled && !readOnly) style.cursorColor else null,
            cursorVisible = state.cursorVisible,
            selectionColor = if (enabled && !readOnly) style.selectionColor else null,
            compositionColor = if (enabled && !readOnly) style.compositionColor else null,
            selectionHandleColor = if (enabled && !readOnly && style.selectionHandlesEnabled) style.selectionHandleColor else null,
            key = key,
            child = TextWidget(
                data = text,
                style = textStyle,
                softWrap = safeMaxLines > 1,
                maxLines = safeMaxLines,
                overflow = if (safeMaxLines > 1) PixelTextOverflow.CLIP else PixelTextOverflow.ELLIPSIS,
                textAlign = TextAlign.START,
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
        val effectiveFill = fillColor ?: style.fillColor
        val effectiveBorder = borderColor ?: style.borderColor
        val content = ButtonSurfaceWidget(
            fillColor = effectiveFill,
            borderColor = effectiveBorder,
            padding = 1,
            alignment = style.alignment,
            key = key,
            child = TextWidget(
                data = text,
                style = style.textStyle,
                softWrap = false,
                maxLines = 1,
                overflow = PixelTextOverflow.CLIP,
                textAlign = TextAlign.CENTER,
                key = key?.let { "$it-text" },
            ),
        )
        return if (effectiveEnabled) {
            GestureDetectorWidget(child = content, onTap = onPressed ?: {}, key = key)
        } else {
            content
        }
    }
}

/**
 * OutlinedButton 使用的 direct surface。
 */
private data class ButtonSurfaceWidget(
    override val child: Widget?,
    val fillColor: PixelColor? = null,
    val borderColor: PixelColor? = null,
    val padding: Int,
    val alignment: Alignment,
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
        )
    }
}
