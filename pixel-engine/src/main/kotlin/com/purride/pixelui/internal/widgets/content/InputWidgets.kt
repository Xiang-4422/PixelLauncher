package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.BuildContext
import com.purride.pixelui.ConstrainedBox
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.FocusNode
import com.purride.pixelui.FocusNodeScope
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelBorderTokens
import com.purride.pixelui.PixelBoxConstraints
import com.purride.pixelui.PixelComponentColorTokens
import com.purride.pixelui.PixelColorScheme
import com.purride.pixelui.PixelControlColorMotion
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelInputType
import com.purride.pixelui.PixelLocalizations
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionTheme
import com.purride.pixelui.PixelMotionTransitionPreset
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsActions
import com.purride.pixelui.PixelSurface
import com.purride.pixelui.PixelSurfaceDecoration
import com.purride.pixelui.PixelTextFieldStyle
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.PixelTextButtonStyle
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.Semantics
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.Widget
import com.purride.pixelui.getInheritedWidgetOfExactType
import com.purride.pixelui.internal.toPixelAlignment
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixelui.animation.Curve
import kotlin.time.Duration

/**
 * Flutter 风格 `TextField` 的 direct widget。
 */
internal data class TextFieldWidget(
    val state: PixelTextFieldState,
    val controller: PixelTextFieldController,
    /** Persistent caller states merged with retained focus before token resolution. */
    val states: PixelControlStateSet = PixelControlStateSet.Normal,
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
    /** Spoken field label kept separate from the editable value. */
    val semanticLabel: String? = null,
    /** Optional usage hint announced independently from placeholder paint. */
    val semanticHint: String? = null,
    /** Current validation error announced with the field. */
    val semanticError: String? = null,
    /** Whether the spoken field label receives the same required marker as visible decoration. */
    val semanticRequired: Boolean = false,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        /** Complete semantic token graph resolved from the nearest provider. */
        val themeTokens = PixelTheme.of(context)
        /** Explicit localization labels, absent until an application opts into the provider. */
        val localizedLabels = PixelLocalizations.maybeOf(context)?.labels
        /** Text-field component roles and geometry. */
        val componentTokens = themeTokens.components.textField
        /** 调用方显式样式；Default 视为省略，让组件 token 继续生效。 */
        val explicitStyle = style.takeUnless { candidate -> candidate == PixelTextFieldStyle.Default }
        /** 光标、选区与闪烁行为样式；缺少显式样式时由 token 解析出选区颜色。 */
        val inputBehaviorStyle = explicitStyle ?: themeTokens.resolveInputBehaviorStyle()
        /** Disabled state normalized by the public facade and guarded for direct internal callers. */
        val disabled = PixelControlState.Disabled in states || !enabled
        /** Loading retains focus but makes the field read-only and removes mutation actions. */
        val loading = PixelControlState.Loading in states
        val effectiveFocusNode = focusNode ?: context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (effectiveFocusNode != null) {
            context.watch(effectiveFocusNode)
            if (!disabled && effectiveFocusNode.isFocused && !state.isFocused && !state.focusRequested) {
                controller.requestFocus(state)
            }
        }
        /** Focus may remain visible during Loading, but never during Disabled. */
        val focused = !disabled && state.isFocused
        /** Runtime state set used by border, foreground, and additive focus resolution. */
        var runtimeStates = mergeControlStates(
            persistent = states,
            disabled = disabled,
            pressed = false,
            hovered = false,
            focused = focused,
        )
        if (readOnly) runtimeStates += PixelControlState.Selected
        /** Focus is painted additively and does not replace the component's base visual role. */
        val baseStates = runtimeStates - PixelControlState.Focused
        /** Whether a semantic or interaction state should resolve through component role tokens. */
        val usesTokenState = !baseStates.isNormal
        /** Text and selection mutation availability. */
        val editable = !disabled && !readOnly && !loading
        /** Focus-request availability exposed to pointer and accessibility click actions. */
        val focusInteractive = !disabled && !loading
        val safeMinLines = minLines.coerceAtLeast(1)
        val safeMaxLines = maxLines.coerceAtLeast(safeMinLines)
        controller.syncCursorBlinkConfig(
            state = state,
            enabled = editable && inputBehaviorStyle.cursorBlinkEnabled,
            periodMs = inputBehaviorStyle.cursorBlinkPeriodMs,
        )
        val text = state.text.ifEmpty { placeholder }
        /** State-resolved component content color. */
        val tokenContentColor = componentTokens.resolveContentColor(baseStates, themeTokens.colors)
        /** 输入文本的基础度量与状态解析出的前景色。 Base input text metrics and state-resolved content color. */
        val inputTextStyle = explicitStyle?.let { explicit ->
            if (disabled) explicit.disabledTextStyle else explicit.textStyle
        } ?: themeTokens.typography.input.resolve(themeTokens.colors)
            .withOptionalColor(tokenContentColor)
        /** placeholder 静止态保留自身弱化角色，其他状态改用状态前景色。 Base placeholder metrics with its own muted role at rest and state colors otherwise. */
        val placeholderTextStyle = explicitStyle?.let { explicit ->
            if (disabled) explicit.disabledPlaceholderStyle else explicit.placeholderStyle
        } ?: themeTokens.typography.caption.resolve(themeTokens.colors).let { base ->
            if (usesTokenState) base.withOptionalColor(tokenContentColor) else base
        }
        /** Concrete text style selected by whether the controlled value is empty. */
        val textStyle = if (state.text.isEmpty()) placeholderTextStyle else inputTextStyle
        /** Explicit fill parameter/style before the component token. */
        val resolvedFillColor = when {
            fillColor != null -> fillColor
            explicitStyle != null -> explicitStyle.fillColor
            else -> componentTokens.resolveContainerColor(baseStates, themeTokens.colors)
        }
        /** 按显式边框参数、显式样式状态、组件 token 顺序解析。 Explicit border parameter, explicit style state, then component token. */
        val resolvedBorderColor = borderColor ?: when {
            explicitStyle != null && disabled -> explicitStyle.disabledBorderColor
            explicitStyle != null && readOnly -> explicitStyle.readOnlyBorderColor
            explicitStyle != null && focused -> explicitStyle.focusedBorderColor
            explicitStyle != null -> explicitStyle.borderColor
            else -> componentTokens.resolveBorderColor(baseStates, themeTokens.colors)
        }
        /** 显式统一内边距，或由 token 解析出的完整组件内边距。 Explicit uniform padding or the complete component inset resolved from tokens. */
        val resolvedPadding = explicitStyle?.padding?.let { inset -> EdgeInsets.all(inset) }
            ?: componentTokens.resolvePadding(themeTokens.spacing)
        /** Border width resolved through the current foundation border scale. */
        val resolvedBorderWidth = componentTokens.resolveBorderWidth(themeTokens.borders)
        /** Corner radius resolved through the current foundation radius scale. */
        val resolvedCornerRadius = componentTokens.resolveCornerRadius(themeTokens.radii)
        /** Hard pixel elevation offset resolved from the shared elevation scale. */
        val elevationOffset = componentTokens.resolveElevation(themeTokens.elevations)
        val inputSurface = TextInputSurfaceWidget(
            fillColor = resolvedFillColor,
            borderColor = resolvedBorderColor,
            borderWidth = resolvedBorderWidth,
            cornerRadius = resolvedCornerRadius,
            shadowColor = themeTokens.colors.shadow.takeIf { elevationOffset > 0 },
            shadowOffset = elevationOffset,
            padding = resolvedPadding,
            alignment = when (textAlign) {
                TextAlign.START -> Alignment.CENTER_START
                TextAlign.CENTER -> Alignment.CENTER
                TextAlign.END -> Alignment.CENTER_END
            },
            state = state,
            controller = controller,
            acceptsTextInput = focusInteractive,
            readOnly = !editable,
            autofocus = autofocus,
            minLines = safeMinLines,
            maxLines = safeMaxLines,
            inputType = inputType,
            textInputAction = textInputAction,
            focusNode = effectiveFocusNode,
            onChanged = onChanged,
            onSubmitted = onSubmitted,
            cursorColor = inputBehaviorStyle.cursorColor.takeIf { editable },
            cursorVisible = state.cursorVisible,
            cursorGap = inputBehaviorStyle.cursorGap.coerceAtLeast(0),
            selectionColor = inputBehaviorStyle.selectionColor.takeIf { editable },
            compositionColor = inputBehaviorStyle.compositionColor.takeIf { editable },
            selectionHandleColor = inputBehaviorStyle.selectionHandleColor.takeIf {
                editable && inputBehaviorStyle.selectionHandlesEnabled
            },
            key = key,
            child = TextWidget(
                data = text,
                style = textStyle,
                softWrap = safeMaxLines > 1,
                maxLines = safeMaxLines,
                overflow = if (safeMaxLines > 1) PixelTextOverflow.CLIP else PixelTextOverflow.ELLIPSIS,
                textAlign = textAlign,
                paddingRight = resolveTextFieldTextPaddingRight(
                    textAlign = textAlign,
                    surfacePaddingRight = resolvedPadding.right,
                ),
                key = key?.let { "$it-text" },
            ),
        )
        /** Focus indicator is additive to error/loading/read-only base colors. */
        val surface = withControlFocusIndicator(
            child = inputSurface,
            states = runtimeStates,
            componentTokens = componentTokens,
            colors = themeTokens.colors,
            borders = themeTokens.borders,
            key = key?.let { "$it-focus-indicator" },
            colorOverride = explicitStyle?.focusedBorderColor,
        )
        /** 非空白显式标签优先；省略时使用可本地化的 token。 Explicit non-blank labels win; omitted defaults use the localizable token. */
        /** Base label resolved before the optional locale-neutral required marker is appended. */
        val baseSpokenLabel = semanticLabel?.takeIf { label -> label.isNotBlank() }
            ?: localizedLabels?.textField
            ?: themeTokens.labels.textField
        /** Required state is descriptive only and never installs validation behavior. */
        val spokenLabel = baseSpokenLabel.withFormFieldRequiredMarker(semanticRequired)
        return Semantics(
            label = spokenLabel,
            role = PixelSemanticRole.TEXT_FIELD,
            enabled = focusInteractive,
            focused = focused,
            value = state.text,
            hint = semanticHint ?: placeholder.takeIf { it.isNotBlank() && it != spokenLabel },
            error = semanticError,
            selectionStart = state.selectionStart,
            selectionEnd = state.selectionEnd,
            excludeDescendants = true,
            actions = PixelSemanticsActions(
                onClick = if (focusInteractive) {
                    {
                        controller.requestFocus(state)
                        true
                    }
                } else {
                    null
                },
                onSetText = if (editable) {
                    { replacement ->
                        controller.updateText(state = state, text = replacement)
                        onChanged?.invoke(replacement)
                        true
                    }
                } else {
                    null
                },
                onSetSelection = if (editable) {
                    { start, end ->
                        if (
                            start < 0 ||
                            end < 0 ||
                            start > state.text.length ||
                            end > state.text.length
                        ) {
                            false
                        } else {
                            /** Accessibility endpoint order is not significant for a selected range. */
                            val orderedStart = minOf(start, end)
                            /** Ordered end then expands with its start to whole grapheme clusters. */
                            val orderedEnd = maxOf(start, end)
                            controller.setSelection(state, orderedStart, orderedEnd)
                            true
                        }
                    }
                } else {
                    null
                },
            ),
            child = surface,
            key = key?.let { "$it-semantics" },
        )
    }
}

/**
 * 解析 TextField 内部文字的右侧尾距。
 *
 * END 对齐时，最少保留“1px 空隙 + 1px 光标列”；输入表面的 padding 仍独立参与布局，
 * 因而 `surfacePaddingRight = 0` 时光标可以固定在字段最右列而不覆盖文字。
 */
internal fun resolveTextFieldTextPaddingRight(
    /** 当前输入文字的逻辑对齐方式。 */
    textAlign: TextAlign,
    /** 输入表面已经解析出的右侧内容 padding。 */
    surfacePaddingRight: Int,
): Int {
    if (textAlign != TextAlign.END) return 0
    /** 调用方提供的非负尾距。 */
    val safeSurfacePadding = surfacePaddingRight.coerceAtLeast(0)
    return safeSurfacePadding.coerceAtLeast(MIN_END_ALIGNED_TEXT_PADDING_PX)
}

/** END 对齐文字末端所需的最小空间：1px 空隙和 1px 光标列。 */
private const val MIN_END_ALIGNED_TEXT_PADDING_PX = 2

/**
 * TextField 视觉和文本输入目标导出的 direct render object widget。
 */
private data class TextInputSurfaceWidget(
    override val child: Widget?,
    val fillColor: PixelColor? = null,
    val borderColor: PixelColor? = null,
    /** Number of nested one-pixel border layers resolved from component tokens. */
    val borderWidth: Int = 1,
    /** Stair-step corner radius resolved from component tokens. */
    val cornerRadius: Int = 0,
    /** Optional hard-edged elevation color. */
    val shadowColor: PixelColor? = null,
    /** Positive diagonal hard-shadow offset included in layout. */
    val shadowOffset: Int = 0,
    /** Complete per-edge input content padding. */
    val padding: EdgeInsets,
    val alignment: Alignment,
    val state: PixelTextFieldState,
    val controller: PixelTextFieldController,
    /** Whether this frame exports a pointer/IME text-input target. */
    val acceptsTextInput: Boolean,
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
    /** 非空文本末尾字形与光标之间的额外像素间隙。 */
    val cursorGap: Int,
    val selectionColor: PixelColor?,
    val compositionColor: PixelColor?,
    val selectionHandleColor: PixelColor?,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderSurface(
            fillColor = fillColor,
            borderColor = borderColor,
            borderWidth = borderWidth,
            cornerRadius = cornerRadius,
            shadowColor = shadowColor,
            shadowOffset = shadowOffset,
            alignment = alignment.toPixelAlignment(),
            contentPaddingLeft = padding.left,
            contentPaddingTop = padding.top,
            contentPaddingRight = padding.right,
            contentPaddingBottom = padding.bottom,
            textInputState = state,
            textInputController = controller.takeIf { acceptsTextInput },
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
            textInputCursorGap = cursorGap,
            textInputSelectionColor = selectionColor,
            textInputCompositionColor = compositionColor,
            textInputSelectionHandleColor = selectionHandleColor,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderSurface).updateSurface(
            fillColor = fillColor,
            borderColor = borderColor,
            borderWidth = borderWidth,
            cornerRadius = cornerRadius,
            shadowColor = shadowColor,
            shadowOffset = shadowOffset,
            alignment = alignment.toPixelAlignment(),
            contentPaddingLeft = padding.left,
            contentPaddingTop = padding.top,
            contentPaddingRight = padding.right,
            contentPaddingBottom = padding.bottom,
            textInputState = state,
            textInputController = controller.takeIf { acceptsTextInput },
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
            textInputCursorGap = cursorGap,
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
    /** Persistent caller states merged with retained pointer and focus states. */
    val states: PixelControlStateSet = PixelControlStateSet.Normal,
    val style: PixelButtonStyle,
    val enabled: Boolean,
    val fillColor: PixelColor? = null,
    val borderColor: PixelColor? = null,
    /** Shared activation callback used by semantics and keyboard adapters. */
    val semanticAction: (() -> Boolean)? = null,
    /** Whether an inherited focused node should drive this button's own visual/semantic focus. */
    val focusVisualEnabled: Boolean = true,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    /** 为每个 retained 按钮创建独立交互状态，但共享 Host 的 Motion 时钟。 */
    override fun createState(): State<out StatefulWidget> = OutlinedButtonWidgetState()
}

/** OutlinedButton 的组合状态颜色、pointer 微状态和独立 focus indicator owner。 */
private class OutlinedButtonWidgetState : State<OutlinedButtonWidget>() {
    /** 指针当前是否保持按下；cancel/up 都由 InteractionDetector 归零。 */
    private var pressed: Boolean = false

    /** 鼠标或触控笔是否悬停在按钮命中区域。 */
    private var hovered: Boolean = false

    /** Retained animated container color, initialized after the first token resolution. */
    private var containerMotion: PixelControlColorMotion? = null

    /** Retained animated foreground color, initialized after the first token resolution. */
    private var contentMotion: PixelControlColorMotion? = null

    /** Retained animated border color, initialized after the first token resolution. */
    private var borderMotion: PixelControlColorMotion? = null

    /** 释放按钮反馈片段拥有的 ticker。 */
    override fun dispose() {
        containerMotion?.dispose()
        contentMotion?.dispose()
        borderMotion?.dispose()
    }

    /** 绘制固定布局尺寸的边框反馈，并导出即时逻辑 semantics。 */
    override fun build(context: BuildContext): Widget {
        /** Complete token graph resolved from the nearest provider. */
        val themeTokens = PixelTheme.of(context)
        /** 可选提供者标签只影响文本，不改变视觉 token 解析。 Optional provider labels affect text only and never change visual token resolution. */
        val localizedLabels = PixelLocalizations.maybeOf(context)?.labels
        /** Button-specific role and geometry tokens. */
        val componentTokens = themeTokens.components.button
        /** Blank caller text falls back to the localized generic button label. */
        val resolvedText = widget.text.takeIf { text -> text.isNotBlank() }
            ?: localizedLabels?.button
            ?: themeTokens.labels.button
        /** 调用方显式样式；Default 视为省略，让组件 token 继续生效。 */
        val explicitStyle = widget.style.takeUnless { style -> style == PixelButtonStyle.Default }
        /** Disabled state normalized by the public facade and guarded again for internal callers. */
        val disabled = PixelControlState.Disabled in widget.states ||
            !widget.enabled ||
            widget.onPressed == null
        /** Loading retains focus but rejects pointer, keyboard, and semantics activation. */
        val loading = PixelControlState.Loading in widget.states
        /** Actual mutation availability after persistent state normalization. */
        val interactive = !disabled && !loading
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) {
            context.watch(focusNode)
        }
        /** Focus remains visible for Loading and is removed only by Disabled. */
        val focused = !disabled && widget.focusVisualEnabled && focusNode?.isFocused == true
        /** Runtime state set combining caller state with retained pointer/focus state. */
        val runtimeStates = mergeControlStates(
            persistent = widget.states,
            disabled = disabled,
            pressed = interactive && pressed,
            hovered = interactive && hovered,
            focused = focused,
        )
        /** 焦点是叠加层，绝不替换当前基础角色。 Focus is additive; it must never displace the current base role. */
        val baseStates = runtimeStates - PixelControlState.Focused
        /** Target container color following explicit parameter/style/token precedence. */
        val targetContainerColor = when {
            widget.fillColor != null -> widget.fillColor
            explicitStyle != null -> explicitStyle.fillColor
            else -> componentTokens.resolveContainerColor(baseStates, themeTokens.colors)
        } ?: PixelColor.Transparent
        /** Target border color following explicit parameter/style/token precedence. */
        val targetBorderColor = when {
            widget.borderColor != null -> widget.borderColor
            explicitStyle != null -> explicitStyle.borderColor
            else -> componentTokens.resolveBorderColor(baseStates, themeTokens.colors)
        } ?: PixelColor.Transparent
        /** Base typography whose metrics come from an explicit style or the theme role. */
        val baseTextStyle = explicitStyle?.textStyle
            ?: themeTokens.typography.button.resolve(themeTokens.colors)
        /** Target content color following explicit style before the component role map. */
        val targetContentColor = when {
            explicitStyle != null -> explicitStyle.textStyle.color
            else -> componentTokens.resolveContentColor(baseStates, themeTokens.colors)
                ?: baseTextStyle.color
        }
        val feedbackSpec = PixelMotionTheme.of(context).feedback
        val motionScope = PixelMotionScope.maybeOf(context)
        val resolvedFeedback = motionScope?.let { scope -> feedbackSpec.resolve(scope.settings) }
        /** Three retained channels initialized from the exact first resolved frame. */
        val resolvedContainerMotion = containerMotion
            ?: PixelControlColorMotion(targetContainerColor).also { motion -> containerMotion = motion }
        val resolvedContentMotion = contentMotion
            ?: PixelControlColorMotion(targetContentColor).also { motion -> contentMotion = motion }
        val resolvedBorderMotion = borderMotion
            ?: PixelControlColorMotion(targetBorderColor).also { motion -> borderMotion = motion }
        listOf(resolvedContainerMotion, resolvedContentMotion, resolvedBorderMotion).forEach { motion ->
            configureControlColorMotion(
                motion = motion,
                scope = motionScope,
                resolvedDuration = resolvedFeedback?.duration ?: Duration.ZERO,
                resolvedDelay = resolvedFeedback?.delay ?: Duration.ZERO,
                resolvedCurve = resolvedFeedback?.curve ?: feedbackSpec.curve,
                immediate = resolvedFeedback?.let { resolved ->
                    resolved.isImmediate || resolved.transition == PixelMotionTransitionPreset.None
                } ?: true,
            )
        }
        if (!interactive) {
            pressed = false
            hovered = false
            resolvedContainerMotion.snapTo(targetContainerColor)
            resolvedContentMotion.snapTo(targetContentColor)
            resolvedBorderMotion.snapTo(targetBorderColor)
        } else {
            resolvedContainerMotion.animateTo(targetContainerColor)
            resolvedContentMotion.animateTo(targetContentColor)
            resolvedBorderMotion.animateTo(targetBorderColor)
        }
        listOf(resolvedContainerMotion, resolvedContentMotion, resolvedBorderMotion).forEach { motion ->
            motion.watch(context)
        }
        /** Hard pixel elevation offset resolved from the shared elevation scale. */
        val elevationOffset = componentTokens.resolveElevation(themeTokens.elevations)
        /** Content padding resolved through the current foundation spacing scale. */
        val resolvedPadding = componentTokens.resolvePadding(themeTokens.spacing)
        /** Border width resolved through the current foundation border scale. */
        val resolvedBorderWidth = componentTokens.resolveBorderWidth(themeTokens.borders)
        /** Corner radius resolved through the current foundation radius scale. */
        val resolvedCornerRadius = componentTokens.resolveCornerRadius(themeTokens.radii)
        /** Theme-sized surface before the independent focus overlay is applied. */
        val content = ConstrainedBox(
            constraints = PixelBoxConstraints(
                minWidth = componentTokens.resolveMinimumWidth(themeTokens.sizes),
                minHeight = componentTokens.resolveMinimumHeight(themeTokens.sizes),
            ),
            child = PixelSurface(
                decoration = PixelSurfaceDecoration(
                    fillColor = resolvedContainerMotion.value,
                    borderColor = resolvedBorderMotion.value,
                    borderWidth = resolvedBorderWidth,
                    cornerRadius = resolvedCornerRadius,
                    shadowColor = themeTokens.colors.shadow.takeIf { elevationOffset > 0 },
                    shadowOffset = elevationOffset,
                ),
                padding = resolvedPadding,
                alignment = explicitStyle?.alignment ?: PixelButtonStyle.Default.alignment,
                key = widget.key,
                child = Text(
                    resolvedText,
                    style = baseTextStyle.copy(color = resolvedContentMotion.value),
                    overflow = PixelTextOverflow.ELLIPSIS,
                    textAlign = TextAlign.CENTER,
                    key = widget.key?.let { "$it-text" },
                ),
            ),
            key = widget.key,
        )
        /** Additive focus indicator keeps error/selected/pressed base colors intact. */
        val focusedContent = withControlFocusIndicator(
            child = content,
            states = runtimeStates,
            componentTokens = componentTokens,
            colors = themeTokens.colors,
            borders = themeTokens.borders,
            key = widget.key?.let { "$it-focus-indicator" },
        )
        val button = if (interactive) {
            InteractionDetector(
                child = focusedContent,
                // Preserve the public callback identity used by PixelTester finder correlation.
                onTap = widget.onPressed ?: {},
                onPressedChanged = ::updatePressed,
                onHoveredChanged = ::updateHovered,
                key = widget.key,
            )
        } else {
            focusedContent
        }
        return Semantics(
            label = resolvedText,
            role = PixelSemanticRole.BUTTON,
            enabled = interactive,
            focused = focused,
            excludeDescendants = true,
            actions = PixelSemanticsActions(
                onClick = widget.semanticAction.takeIf { interactive },
            ),
            child = button,
            key = widget.key?.let { "$it-semantics" },
        )
    }

    /** 更新 pressed 状态并触发一次 retained 重建。 */
    private fun updatePressed(nextPressed: Boolean) {
        if (pressed == nextPressed) return
        setState { pressed = nextPressed }
    }

    /** 更新 hover 状态并触发一次 retained 重建。 */
    private fun updateHovered(nextHovered: Boolean) {
        if (hovered == nextHovered) return
        setState { hovered = nextHovered }
    }
}

/** 无边框、零默认 padding 的文字按钮。 */
internal data class TextButtonWidget(
    val text: String,
    val onPressed: (() -> Unit)?,
    /** Persistent caller states merged with retained pointer and focus states. */
    val states: PixelControlStateSet = PixelControlStateSet.Normal,
    val style: PixelTextButtonStyle,
    val enabled: Boolean,
    /** Shared activation callback used by semantics and keyboard adapters. */
    val semanticAction: (() -> Boolean)? = null,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    /** 为每个 retained 文字按钮创建连续交互状态。 */
    override fun createState(): State<out StatefulWidget> = TextButtonWidgetState()
}

/** TextButton 的组合前景状态、pointer 微状态和独立 focus indicator owner。 */
private class TextButtonWidgetState : State<TextButtonWidget>() {
    /** 指针当前是否保持按下。 */
    private var pressed: Boolean = false

    /** 鼠标或触控笔当前是否悬停。 */
    private var hovered: Boolean = false

    /** Retained animated foreground color initialized from the first resolved token frame. */
    private var contentMotion: PixelControlColorMotion? = null

    /** Retained optional container channel for customized TextButton component tokens. */
    private var containerMotion: PixelControlColorMotion? = null

    /** Retained optional outline channel for customized TextButton component tokens. */
    private var borderMotion: PixelControlColorMotion? = null

    /** 释放文字按钮反馈片段拥有的 ticker。 */
    override fun dispose() {
        contentMotion?.dispose()
        containerMotion?.dispose()
        borderMotion?.dispose()
    }

    /** 绘制颜色反馈并保持文字按钮的自然尺寸不变。 */
    override fun build(context: BuildContext): Widget {
        /** Complete token graph resolved from the nearest provider. */
        val themeTokens = PixelTheme.of(context)
        /** 可选提供者标签只影响文本，不改变视觉 token 解析。 Optional provider labels affect text only and never change visual token resolution. */
        val localizedLabels = PixelLocalizations.maybeOf(context)?.labels
        /** Text-button-specific role and geometry tokens. */
        val componentTokens = themeTokens.components.textButton
        /** Blank caller text falls back to the localized text-button label. */
        val resolvedText = widget.text.takeIf { text -> text.isNotBlank() }
            ?: localizedLabels?.textButton
            ?: themeTokens.labels.textButton
        /** 调用方显式样式；Default 视为省略，让组件 token 继续生效。 */
        val explicitStyle = widget.style.takeUnless { style -> style == PixelTextButtonStyle.Default }
        /** Disabled state normalized by the public facade and guarded for direct internal callers. */
        val disabled = PixelControlState.Disabled in widget.states ||
            !widget.enabled ||
            widget.onPressed == null
        /** Loading is focus-retaining but inert. */
        val loading = PixelControlState.Loading in widget.states
        /** Pointer and activation availability. */
        val interactive = !disabled && !loading
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) {
            context.watch(focusNode)
        }
        /** Focus remains visible for Loading and disappears only for Disabled. */
        val focused = !disabled && focusNode?.isFocused == true
        /** Runtime state set combining caller and retained interaction state. */
        val runtimeStates = mergeControlStates(
            persistent = widget.states,
            disabled = disabled,
            pressed = interactive && pressed,
            hovered = interactive && hovered,
            focused = focused,
        )
        /** Focus is additive and therefore removed before resolving the foreground base role. */
        val baseStates = runtimeStates - PixelControlState.Focused
        /** Typography metrics resolved from explicit style before the theme role. */
        val baseTextStyle = explicitStyle?.textStyle
            ?: themeTokens.typography.button.resolve(themeTokens.colors)
        /** Foreground target resolved using explicit style above component state tokens. */
        val targetContentColor = when {
            explicitStyle != null -> explicitStyle.textStyle.color
            else -> componentTokens.resolveContentColor(baseStates, themeTokens.colors)
                ?: baseTextStyle.color
        }
        /** 可选的 token 容器通道；无边框 TextButton 通常解析为透明。 Optional token container channel; a borderless TextButton usually resolves transparent. */
        val targetContainerColor = componentTokens
            .resolveContainerColor(baseStates, themeTokens.colors) ?: PixelColor.Transparent
        /** 可选的 token 边框通道；无边框 TextButton 通常解析为透明。 Optional token outline channel; a borderless TextButton usually resolves transparent. */
        val targetBorderColor = componentTokens
            .resolveBorderColor(baseStates, themeTokens.colors) ?: PixelColor.Transparent
        val feedbackSpec = PixelMotionTheme.of(context).feedback
        val motionScope = PixelMotionScope.maybeOf(context)
        val resolvedFeedback = motionScope?.let { scope -> feedbackSpec.resolve(scope.settings) }
        /** Retained foreground channel initialized from the exact first target. */
        val resolvedContentMotion = contentMotion
            ?: PixelControlColorMotion(targetContentColor).also { motion -> contentMotion = motion }
        /** Retained optional surface channels initialized from their exact first resolved frame. */
        val resolvedContainerMotion = containerMotion
            ?: PixelControlColorMotion(targetContainerColor).also { motion -> containerMotion = motion }
        val resolvedBorderMotion = borderMotion
            ?: PixelControlColorMotion(targetBorderColor).also { motion -> borderMotion = motion }
        listOf(resolvedContentMotion, resolvedContainerMotion, resolvedBorderMotion).forEach { motion ->
            configureControlColorMotion(
                motion = motion,
                scope = motionScope,
                resolvedDuration = resolvedFeedback?.duration ?: Duration.ZERO,
                resolvedDelay = resolvedFeedback?.delay ?: Duration.ZERO,
                resolvedCurve = resolvedFeedback?.curve ?: feedbackSpec.curve,
                immediate = resolvedFeedback?.let { resolved ->
                    resolved.isImmediate || resolved.transition == PixelMotionTransitionPreset.None
                } ?: true,
            )
        }
        if (!interactive) {
            pressed = false
            hovered = false
            resolvedContentMotion.snapTo(targetContentColor)
            resolvedContainerMotion.snapTo(targetContainerColor)
            resolvedBorderMotion.snapTo(targetBorderColor)
        } else {
            resolvedContentMotion.animateTo(targetContentColor)
            resolvedContainerMotion.animateTo(targetContainerColor)
            resolvedBorderMotion.animateTo(targetBorderColor)
        }
        listOf(resolvedContentMotion, resolvedContainerMotion, resolvedBorderMotion).forEach { motion ->
            motion.watch(context)
        }
        /** Hard pixel elevation offset resolved from the shared elevation scale. */
        val elevationOffset = componentTokens.resolveElevation(themeTokens.elevations)
        /** Default component padding resolved through the current foundation spacing scale. */
        val tokenPadding = componentTokens.resolvePadding(themeTokens.spacing)
        /** Border width resolved through the current foundation border scale. */
        val resolvedBorderWidth = componentTokens.resolveBorderWidth(themeTokens.borders)
        /** Corner radius resolved through the current foundation radius scale. */
        val resolvedCornerRadius = componentTokens.resolveCornerRadius(themeTokens.radii)
        /** Theme-sized content before the independent focus indicator is applied. */
        val content = ConstrainedBox(
            constraints = PixelBoxConstraints(
                minWidth = componentTokens.resolveMinimumWidth(themeTokens.sizes),
                minHeight = componentTokens.resolveMinimumHeight(themeTokens.sizes),
            ),
            child = PixelSurface(
                decoration = PixelSurfaceDecoration(
                    fillColor = resolvedContainerMotion.value,
                    borderColor = resolvedBorderMotion.value,
                    borderWidth = resolvedBorderWidth,
                    cornerRadius = resolvedCornerRadius,
                    shadowColor = themeTokens.colors.shadow.takeIf { elevationOffset > 0 },
                    shadowOffset = elevationOffset,
                ),
                padding = explicitStyle?.padding ?: tokenPadding,
                alignment = explicitStyle?.alignment ?: PixelTextButtonStyle.Default.alignment,
                key = widget.key,
                child = Text(
                    resolvedText,
                    style = baseTextStyle.copy(color = resolvedContentMotion.value),
                    overflow = PixelTextOverflow.ELLIPSIS,
                    softWrap = false,
                    maxLines = 1,
                    textAlign = TextAlign.CENTER,
                    key = widget.key?.let { "$it-text" },
                ),
            ),
            key = widget.key,
        )
        /** Additive focus indicator does not replace the foreground state role. */
        val focusedContent = withControlFocusIndicator(
            child = content,
            states = runtimeStates,
            componentTokens = componentTokens,
            colors = themeTokens.colors,
            borders = themeTokens.borders,
            key = widget.key?.let { "$it-focus-indicator" },
        )
        val button = if (interactive) {
            InteractionDetector(
                child = focusedContent,
                // Preserve the public callback identity used by PixelTester finder correlation.
                onTap = widget.onPressed ?: {},
                onPressedChanged = ::updatePressed,
                onHoveredChanged = ::updateHovered,
                key = widget.key,
            )
        } else {
            focusedContent
        }
        return Semantics(
            label = resolvedText,
            role = PixelSemanticRole.BUTTON,
            enabled = interactive,
            focused = focused,
            excludeDescendants = true,
            actions = PixelSemanticsActions(
                onClick = widget.semanticAction.takeIf { interactive },
            ),
            child = button,
            key = widget.key?.let { "$it-semantics" },
        )
    }

    /** 更新 pressed 状态并触发一次 retained 重建。 */
    private fun updatePressed(nextPressed: Boolean) {
        if (pressed == nextPressed) return
        setState { pressed = nextPressed }
    }

    /** 更新 hover 状态并触发一次 retained 重建。 */
    private fun updateHovered(nextHovered: Boolean) {
        if (hovered == nextHovered) return
        setState { hovered = nextHovered }
    }
}

/**
 * 把 retained 交互标志合并为一个不可变组件状态集合。
 *
 * Disabled 是瞬时交互所有权的终止状态：即使调用方持续传入 Focused、Pressed 或 Hovered，
 * 也会将其移除。Selected、Error 与 Loading 保持正交，使禁用状态下的选中、错误和加载视觉
 * 仍能得到确定性解析。该函数仅用于 Pixel SDK 兄弟 artifact 之间互操作。
 */
@PixelArtifactInternalApi
public fun mergeControlStates(
    persistent: PixelControlStateSet,
    disabled: Boolean,
    pressed: Boolean,
    hovered: Boolean,
    focused: Boolean,
): PixelControlStateSet {
    /** 从规范化运行时输入或调用方状态推导出的终止能力状态。 */
    val terminalDisabled = disabled || PixelControlState.Disabled in persistent
    if (terminalDisabled) {
        /** 移除不可能存在的瞬时所有权后保留的持久语义状态。 */
        return persistent -
            PixelControlState.Focused -
            PixelControlState.Pressed -
            PixelControlState.Hovered +
            PixelControlState.Disabled
    }
    /** 返回给组件 token 解析器的累积不可变状态集合。 */
    var result = persistent
    if (pressed) result += PixelControlState.Pressed
    if (hovered) result += PixelControlState.Hovered
    if (focused) result += PixelControlState.Focused
    return result
}

/** Configures one retained color channel from the already-resolved Motion environment. */
internal fun configureControlColorMotion(
    motion: PixelControlColorMotion,
    scope: PixelMotionScope?,
    resolvedDuration: Duration,
    resolvedDelay: Duration,
    resolvedCurve: Curve,
    immediate: Boolean,
) {
    motion.configure(
        nextVsync = scope?.vsync,
        nextDuration = resolvedDuration,
        nextDelay = resolvedDelay,
        nextCurve = resolvedCurve,
        nextImmediate = immediate,
    )
}

/** Wraps [child] in the component's additive focus-indicator render layer when focused. */
internal fun withControlFocusIndicator(
    child: Widget,
    states: PixelControlStateSet,
    componentTokens: PixelComponentColorTokens,
    colors: PixelColorScheme,
    borders: PixelBorderTokens,
    key: Any?,
    colorOverride: PixelColor? = null,
): Widget {
    /** Independent focus specification omitted for unfocused or non-focusable visuals. */
    val indicator = componentTokens.focusIndicatorFor(states) ?: return child
    return PixelControlFocusIndicatorWidget(
        child = child,
        color = colorOverride ?: indicator.resolveColor(colors),
        width = indicator.resolveWidth(borders),
        inset = indicator.inset,
        key = key,
    )
}

/** Shrink-wrapping focus decorator that preserves every child interaction and semantics channel. */
private data class PixelControlFocusIndicatorWidget(
    /** Component subtree whose exact size and target bounds are preserved. */
    override val child: Widget,
    /** Concrete focus color resolved from the active color scheme. */
    val color: PixelColor,
    /** Number of nested one-pixel outlines. */
    val width: Int,
    /** Distance from the component edge to the first outline. */
    val inset: Int,
    /** Stable retained render identity. */
    override val key: Any?,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    /** Creates the retained decorator that paints after its child. */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderPixelControlFocusIndicator(color = color, width = width, inset = inset)
    }

    /** Updates focus color and geometry without replacing the render object. */
    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderPixelControlFocusIndicator).update(
            color = color,
            width = width,
            inset = inset,
        )
    }
}

/** Render object for an additive, pixel-aligned focus outline that never changes child geometry. */
private class RenderPixelControlFocusIndicator(
    /** Current concrete outline color. */
    private var color: PixelColor,
    /** Current nested outline count. */
    private var width: Int,
    /** Current edge inset. */
    private var inset: Int,
) : SingleChildRenderObject() {
    /** Gives the child unchanged constraints and shrink-wraps its constrained result. */
    override fun layout(constraints: RenderConstraints) {
        renderChild?.layout(constraints)
        /** Exact child size constrained once more for an absent or malformed subtree. */
        val childSize = renderChild?.size ?: RenderSize.Zero
        size = RenderSize(
            width = constraints.constrainWidth(childSize.width),
            height = constraints.constrainHeight(childSize.height),
        )
    }

    /** Paints the child first, then draws every complete focus outline above its base state. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        renderChild?.paint(context, offsetX, offsetY)
        /** Maximum nested layers that leave a positive rectangle. */
        val availableLayers = ((minOf(size.width, size.height) - inset * 2 + 1) / 2).coerceAtLeast(0)
        /** Safe layer count after malformed runtime geometry is constrained. */
        val layerCount = minOf(width.coerceAtLeast(0), availableLayers)
        repeat(layerCount) { layer ->
            /** Absolute inset of this nested one-pixel outline. */
            val layerInset = inset + layer
            context.drawRect(
                x = offsetX + layerInset,
                y = offsetY + layerInset,
                w = size.width - layerInset * 2,
                h = size.height - layerInset * 2,
                color = color,
            )
        }
    }

    /** Replaces concrete focus properties and schedules repaint only when needed. */
    fun update(color: PixelColor, width: Int, inset: Int) {
        if (this.color == color && this.width == width && this.inset == inset) return
        this.color = color
        this.width = width
        this.inset = inset
        markNeedsPaint()
    }

    /** Forwards hit testing without adding a focus-only hit target. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        renderChild?.hitTest(localX, localY, result)
    }

    /** Forwards descendant click targets with unchanged geometry. */
    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        renderChild?.collectClickTargets(offsetX, offsetY, targets)
    }

    /** Forwards descendant pager targets with unchanged geometry. */
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) {
        renderChild?.collectPagerTargets(offsetX, offsetY, targets)
    }

    /** Forwards descendant list targets with unchanged geometry. */
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) {
        renderChild?.collectListTargets(offsetX, offsetY, targets)
    }

    /** Forwards descendant scrollbar targets with unchanged geometry. */
    override fun collectScrollbarTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelScrollbarTarget>,
    ) {
        renderChild?.collectScrollbarTargets(offsetX, offsetY, targets)
    }

    /** Forwards descendant refresh targets with unchanged geometry. */
    override fun collectRefreshTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelRefreshTarget>) {
        renderChild?.collectRefreshTargets(offsetX, offsetY, targets)
    }

    /** Forwards descendant text-input targets with unchanged geometry. */
    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        renderChild?.collectTextInputTargets(offsetX, offsetY, targets)
    }

    /** Forwards descendant Slider targets with unchanged geometry. */
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) {
        renderChild?.collectSliderTargets(offsetX, offsetY, targets)
    }

    /** Forwards descendant semantics with unchanged bounds and stable retained identifiers. */
    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        renderChild?.collectSemantics(offsetX, offsetY, targets)
    }

    /** Typed render child used by every layout, paint, and forwarding path. */
    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

/**
 * 从 token 图解析 TextField 的光标与选区行为样式。
 *
 * 颜色通道来自 [PixelColorScheme.selection]；闪烁周期与 handle 开关等非颜色行为沿用
 * [PixelTextFieldStyle] 的稳定默认值。
 */
private fun PixelThemeTokens.resolveInputBehaviorStyle(): PixelTextFieldStyle {
    return PixelTextFieldStyle.Default.copy(
        cursorColor = colors.selection,
        selectionColor = colors.selection,
        compositionColor = colors.selection,
        selectionHandleColor = colors.selection,
    )
}

/** 存在解析出的状态颜色时覆盖文本色，否则保留 typography 自带的语义色。 */
private fun PixelTextStyle.withOptionalColor(color: PixelColor?): PixelTextStyle {
    return color?.let { resolvedColor -> copy(color = resolvedColor) } ?: this
}
