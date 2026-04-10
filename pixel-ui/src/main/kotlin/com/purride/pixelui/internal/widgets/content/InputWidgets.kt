package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelTextFieldStyle
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.Alignment
import com.purride.pixelui.InternalBuildContext
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
     * 在 build 时解析输入框样式并组合成 direct surface/text 子树。
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
        val text = state.text.ifEmpty { placeholder }
        val textStyle = if (state.text.isEmpty()) {
            resolvedStyle.placeholderStyle
        } else {
            resolvedStyle.textStyle
        }
        return TextInputSurfaceWidget(
            fillTone = resolvedStyle.fillTone,
            borderTone = when {
                !enabled -> resolvedStyle.disabledBorderTone
                readOnly -> resolvedStyle.readOnlyBorderTone
                state.isFocused -> resolvedStyle.focusedBorderTone
                else -> resolvedStyle.borderTone
            },
            padding = resolvedStyle.padding,
            alignment = Alignment.CENTER_START,
            state = state,
            controller = controller,
            readOnly = readOnly,
            autofocus = autofocus,
            textInputAction = textInputAction,
            onChanged = onChanged,
            onSubmitted = onSubmitted,
            key = key,
            child = TextWidget(
                data = text,
                style = textStyle,
                theme = null,
                softWrap = false,
                maxLines = 1,
                overflow = PixelTextOverflow.CLIP,
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
    val fillTone: com.purride.pixelcore.PixelTone,
    val borderTone: com.purride.pixelcore.PixelTone?,
    val padding: Int,
    val alignment: Alignment,
    val state: PixelTextFieldState,
    val controller: PixelTextFieldController,
    val readOnly: Boolean,
    val autofocus: Boolean,
    val textInputAction: PixelTextInputAction,
    val onChanged: ((String) -> Unit)?,
    val onSubmitted: ((String) -> Unit)?,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(
    child = child,
    key = key,
) {
    /**
     * 创建可导出 text input target 的 surface。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderSurface(
            fillTone = fillTone,
            borderTone = borderTone,
            alignment = alignment.toPixelAlignment(),
            contentPaddingLeft = padding,
            contentPaddingTop = padding,
            contentPaddingRight = padding,
            contentPaddingBottom = padding,
            textInputState = state,
            textInputController = controller,
            textInputReadOnly = readOnly,
            textInputAutofocus = autofocus,
            textInputAction = textInputAction,
            textInputOnChanged = onChanged,
            textInputOnSubmitted = onSubmitted,
        )
    }

    /**
     * 同步 text input surface 配置。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderSurface).updateSurface(
            fillTone = fillTone,
            borderTone = borderTone,
            alignment = alignment.toPixelAlignment(),
            contentPaddingLeft = padding,
            contentPaddingTop = padding,
            contentPaddingRight = padding,
            contentPaddingBottom = padding,
            textInputState = state,
            textInputController = controller,
            textInputReadOnly = readOnly,
            textInputAutofocus = autofocus,
            textInputAction = textInputAction,
            textInputOnChanged = onChanged,
            textInputOnSubmitted = onSubmitted,
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
    val theme: com.purride.pixelui.PixelThemeData?,
    val enabled: Boolean,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 在 build 时解析按钮样式，并组合成 direct gesture/surface/text 子树。
     */
    override fun build(context: BuildContext): Widget {
        val resolvedTheme = context.resolveTheme(theme)
        val effectiveEnabled = enabled && onPressed != null
        val resolvedStyle = if (!effectiveEnabled) {
            resolvedTheme.disabledButtonStyle
        } else {
            when (style) {
                PixelButtonStyle.Default -> resolvedTheme.buttonStyle
                PixelButtonStyle.Accent -> resolvedTheme.accentButtonStyle
                else -> style
            }
        }
        val content = ButtonSurfaceWidget(
            fillTone = resolvedStyle.fillTone,
            borderTone = resolvedStyle.borderTone,
            padding = 1,
            alignment = resolvedStyle.alignment,
            key = key,
            child = TextWidget(
                data = text,
                style = resolvedStyle.textStyle,
                theme = null,
                softWrap = false,
                maxLines = 1,
                overflow = PixelTextOverflow.CLIP,
                textAlign = TextAlign.CENTER,
                key = key?.let { "$it-text" },
            ),
        )
        return if (effectiveEnabled) {
            GestureDetectorWidget(
                child = content,
                onTap = onPressed ?: {},
                key = key,
            )
        } else {
            content
        }
    }
}

/**
 * OutlinedButton 使用的 direct surface，默认填满父级分配的按钮区域。
 */
private data class ButtonSurfaceWidget(
    override val child: Widget?,
    val fillTone: com.purride.pixelcore.PixelTone,
    val borderTone: com.purride.pixelcore.PixelTone?,
    val padding: Int,
    val alignment: Alignment,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(
    child = child,
    key = key,
) {
    /**
     * 创建填满父级按钮区域的 surface。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderSurface(
            fillTone = fillTone,
            borderTone = borderTone,
            alignment = alignment.toPixelAlignment(),
            fillMaxWidth = true,
            fillMaxHeight = true,
            contentPaddingLeft = padding,
            contentPaddingTop = padding,
            contentPaddingRight = padding,
            contentPaddingBottom = padding,
        )
    }

    /**
     * 同步按钮 surface 配置。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderSurface).updateSurface(
            fillTone = fillTone,
            borderTone = borderTone,
            alignment = alignment.toPixelAlignment(),
            fillMaxWidth = true,
            fillMaxHeight = true,
            contentPaddingLeft = padding,
            contentPaddingTop = padding,
            contentPaddingRight = padding,
            contentPaddingBottom = padding,
        )
    }
}
