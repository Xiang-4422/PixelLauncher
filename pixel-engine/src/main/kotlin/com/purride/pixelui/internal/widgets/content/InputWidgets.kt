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
import com.purride.pixelui.PixelTheme
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
    /** Marks the old public facade so a scope-less mount can retain its historical pixels. */
    val legacyFacade: Boolean = false,
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
        /** Old facades use historical visuals only when no explicit PixelTheme provider exists. */
        val usesScopeLessLegacyVisuals = legacyFacade && PixelTheme.maybeTokensOf(context) == null
        /** Complete semantic token graph resolved from the nearest provider. */
        val themeTokens = PixelTheme.tokensOf(context)
        /** Explicit localization labels, absent until an application opts into the provider. */
        val localizedLabels = PixelLocalizations.maybeOf(context)?.labels
        /** Exact legacy style retained because arbitrary component colors cannot become roles. */
        val legacyStyle = PixelTheme.of(context).textFieldStyle
        /** Text-field component roles and geometry. */
        val componentTokens = themeTokens.components.textField
        /** Explicit legacy style; Default allows component tokens to remain live. */
        val explicitStyle = style.takeUnless { candidate -> candidate == PixelTextFieldStyle.Default }
        /** Exact style selected by the pre-token facade contract. */
        val compatibilityStyle = explicitStyle ?: legacyStyle
        /** Legacy style retained for cursor, selection, composition, and blink compatibility. */
        val inputBehaviorStyle = explicitStyle ?: legacyStyle
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
        /** Base input text metrics and explicit color override. */
        val inputTextStyle = if (usesScopeLessLegacyVisuals) {
            if (disabled) compatibilityStyle.disabledTextStyle else compatibilityStyle.textStyle
        } else {
            explicitStyle?.let { explicit ->
                if (disabled) explicit.disabledTextStyle else explicit.textStyle
            } ?: legacyStyle.let { inherited ->
                /** Normal keeps the exact inherited color; non-Normal applies the semantic role color. */
                val baseStyle = if (disabled) inherited.disabledTextStyle else inherited.textStyle
                if (usesTokenState) {
                    tokenContentColor?.let { color -> baseStyle.copy(color = color) } ?: baseStyle
                } else {
                    baseStyle
                }
            }
        }
        /** Base placeholder metrics and state-resolved content color. */
        val placeholderTextStyle = if (usesScopeLessLegacyVisuals) {
            if (disabled) compatibilityStyle.disabledPlaceholderStyle else compatibilityStyle.placeholderStyle
        } else {
            explicitStyle?.let { explicit ->
                if (disabled) explicit.disabledPlaceholderStyle else explicit.placeholderStyle
            } ?: legacyStyle.let { inherited ->
                /** Placeholder metrics and Normal color remain exact for custom legacy themes. */
                val baseStyle = if (disabled) inherited.disabledPlaceholderStyle else inherited.placeholderStyle
                if (usesTokenState) {
                    tokenContentColor?.let { color -> baseStyle.copy(color = color) } ?: baseStyle
                } else {
                    baseStyle
                }
            }
        }
        /** Concrete text style selected by whether the controlled value is empty. */
        val textStyle = if (state.text.isEmpty()) placeholderTextStyle else inputTextStyle
        /** Explicit fill parameter/style before the component token. */
        val resolvedFillColor = when {
            fillColor != null -> fillColor
            usesScopeLessLegacyVisuals -> compatibilityStyle.fillColor
            explicitStyle != null -> explicitStyle.fillColor
            usesTokenState -> componentTokens.resolveContainerColor(baseStates, themeTokens.colors)
            else -> legacyStyle.fillColor
        }
        /** Explicit border parameter, compatible style state, then component token. */
        val resolvedBorderColor = borderColor ?: when {
            usesScopeLessLegacyVisuals && disabled -> compatibilityStyle.disabledBorderColor
            usesScopeLessLegacyVisuals && readOnly -> compatibilityStyle.readOnlyBorderColor
            usesScopeLessLegacyVisuals && focused -> compatibilityStyle.focusedBorderColor
            usesScopeLessLegacyVisuals -> compatibilityStyle.borderColor
            explicitStyle != null && disabled -> explicitStyle.disabledBorderColor
            explicitStyle != null && readOnly -> explicitStyle.readOnlyBorderColor
            explicitStyle != null && focused -> explicitStyle.focusedBorderColor
            explicitStyle != null -> explicitStyle.borderColor
            usesTokenState -> componentTokens.resolveBorderColor(baseStates, themeTokens.colors)
            else -> legacyStyle.borderColor
        }
        /** Explicit uniform padding or the complete component inset projected from legacy themes. */
        val resolvedPadding = if (usesScopeLessLegacyVisuals) {
            EdgeInsets.all(compatibilityStyle.padding)
        } else {
            explicitStyle?.padding?.let { inset -> EdgeInsets.all(inset) }
                ?: componentTokens.resolvePadding(themeTokens.spacing)
        }
        /** Border width resolved through the current foundation border scale. */
        val resolvedBorderWidth = if (usesScopeLessLegacyVisuals) {
            LEGACY_INPUT_BORDER_WIDTH_PX
        } else {
            componentTokens.resolveBorderWidth(themeTokens.borders)
        }
        /** Corner radius resolved through the current foundation radius scale. */
        val resolvedCornerRadius = if (usesScopeLessLegacyVisuals) {
            0
        } else {
            componentTokens.resolveCornerRadius(themeTokens.radii)
        }
        /** Hard pixel elevation offset resolved from the shared elevation scale. */
        val elevationOffset = if (usesScopeLessLegacyVisuals) {
            0
        } else {
            componentTokens.resolveElevation(themeTokens.elevations)
        }
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
                paddingRight = if (textAlign == TextAlign.END) {
                    if (usesScopeLessLegacyVisuals) LEGACY_INPUT_END_TEXT_PADDING_PX else resolvedPadding.right
                } else {
                    0
                },
                key = key?.let { "$it-text" },
            ),
        )
        /** Focus indicator is additive to error/loading/read-only base colors. */
        val surface = if (usesScopeLessLegacyVisuals) {
            inputSurface
        } else {
            withControlFocusIndicator(
                child = inputSurface,
                states = runtimeStates,
                componentTokens = componentTokens,
                colors = themeTokens.colors,
                borders = themeTokens.borders,
                key = key?.let { "$it-focus-indicator" },
                colorOverride = explicitStyle?.focusedBorderColor,
            )
        }
        /** Explicit non-blank labels win; omitted legacy defaults use the localizable token. */
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
    /** Marks the old public facade so a scope-less mount can retain its historical pixels. */
    val legacyFacade: Boolean = false,
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
        /** Old facades use historical visuals only when no explicit PixelTheme provider exists. */
        val usesScopeLessLegacyVisuals = widget.legacyFacade && PixelTheme.maybeTokensOf(context) == null
        /** Complete token graph resolved from the nearest provider. */
        val themeTokens = PixelTheme.tokensOf(context)
        /** Optional provider labels affect text only and never select the legacy visual branch. */
        val localizedLabels = PixelLocalizations.maybeOf(context)?.labels
        /** Exact inherited legacy theme used by the old disabled and focus color precedence. */
        val legacyTheme = PixelTheme.of(context)
        /** Exact inherited legacy style used for the Normal compatibility baseline. */
        val legacyStyle = legacyTheme.buttonStyle
        /** Button-specific role and geometry tokens. */
        val componentTokens = themeTokens.components.button
        /** Blank caller text falls back to the localized generic button label. */
        val resolvedText = widget.text.takeIf { text -> text.isNotBlank() }
            ?: localizedLabels?.button
            ?: if (usesScopeLessLegacyVisuals) widget.text else themeTokens.labels.button
        /** Explicit legacy style; Default is treated as absence so component tokens can propagate. */
        val explicitStyle = widget.style.takeUnless { style -> style == PixelButtonStyle.Default }
        /** Exact style selected by the pre-token facade contract. */
        val compatibilityStyle = explicitStyle ?: legacyStyle
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
        /** Focus is additive; it must never displace the current base role or legacy Normal style. */
        val baseStates = runtimeStates - PixelControlState.Focused
        /**
         * Whether a non-focus state should resolve through component roles. Scope-less legacy
         * facades retain only their resting/disabled pixels; new hover and press feedback remains
         * available without changing the historical first frame.
         */
        val usesTokenState = !baseStates.isNormal && !(usesScopeLessLegacyVisuals && disabled)
        /** Target container color following explicit parameter/style/token precedence. */
        val targetContainerColor = when {
            widget.fillColor != null -> widget.fillColor
            explicitStyle != null -> explicitStyle.fillColor
            usesTokenState -> componentTokens.resolveContainerColor(baseStates, themeTokens.colors)
            usesScopeLessLegacyVisuals -> compatibilityStyle.fillColor
            else -> legacyStyle.fillColor
        } ?: PixelColor.Transparent
        /** Target border color following explicit parameter/style/token precedence. */
        val targetBorderColor = when {
            widget.borderColor != null -> widget.borderColor
            usesScopeLessLegacyVisuals && disabled && widget.style == PixelButtonStyle.Default -> {
                legacyTheme.colors.disabled
            }
            explicitStyle != null -> explicitStyle.borderColor
            usesTokenState -> componentTokens.resolveBorderColor(baseStates, themeTokens.colors)
            usesScopeLessLegacyVisuals -> compatibilityStyle.borderColor
            else -> legacyStyle.borderColor
        } ?: PixelColor.Transparent
        /** Base typography whose metrics come from an explicit style or the theme role. */
        val baseTextStyle = if (usesScopeLessLegacyVisuals) {
            compatibilityStyle.textStyle
        } else {
            explicitStyle?.textStyle ?: legacyStyle.textStyle
        }
        /** Target content color following explicit style before the component role map. */
        val targetContentColor = when {
            usesScopeLessLegacyVisuals && disabled && widget.style == PixelButtonStyle.Default -> {
                legacyTheme.colors.disabled
            }
            explicitStyle != null -> explicitStyle.textStyle.color
            usesTokenState -> componentTokens.resolveContentColor(baseStates, themeTokens.colors)
                ?: baseTextStyle.color
            usesScopeLessLegacyVisuals -> baseTextStyle.color
            else -> baseTextStyle.color
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
        val elevationOffset = if (usesScopeLessLegacyVisuals) {
            0
        } else {
            componentTokens.resolveElevation(themeTokens.elevations)
        }
        /** Content padding resolved through the current foundation spacing scale. */
        val resolvedPadding = if (usesScopeLessLegacyVisuals) {
            EdgeInsets.all(LEGACY_OUTLINED_BUTTON_PADDING_PX)
        } else {
            componentTokens.resolvePadding(themeTokens.spacing)
        }
        /** Border width resolved through the current foundation border scale. */
        val resolvedBorderWidth = if (usesScopeLessLegacyVisuals) {
            LEGACY_OUTLINED_BUTTON_BORDER_WIDTH_PX
        } else {
            componentTokens.resolveBorderWidth(themeTokens.borders)
        }
        /** Corner radius resolved through the current foundation radius scale. */
        val resolvedCornerRadius = if (usesScopeLessLegacyVisuals) {
            0
        } else {
            componentTokens.resolveCornerRadius(themeTokens.radii)
        }
        /** Theme-sized surface before the independent focus overlay is applied. */
        val content = ConstrainedBox(
            constraints = PixelBoxConstraints(
                minWidth = if (usesScopeLessLegacyVisuals) {
                    0
                } else {
                    componentTokens.resolveMinimumWidth(themeTokens.sizes)
                },
                minHeight = if (usesScopeLessLegacyVisuals) {
                    0
                } else {
                    componentTokens.resolveMinimumHeight(themeTokens.sizes)
                },
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
                alignment = if (usesScopeLessLegacyVisuals) {
                    compatibilityStyle.alignment
                } else {
                    explicitStyle?.alignment ?: PixelButtonStyle.Default.alignment
                },
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
    /** Marks the old public facade so a scope-less mount can retain its historical pixels. */
    val legacyFacade: Boolean = false,
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
        /** Old facades use historical visuals only when no explicit PixelTheme provider exists. */
        val usesScopeLessLegacyVisuals = widget.legacyFacade && PixelTheme.maybeTokensOf(context) == null
        /** Complete token graph resolved from the nearest provider. */
        val themeTokens = PixelTheme.tokensOf(context)
        /** Optional provider labels affect text only and never select the legacy visual branch. */
        val localizedLabels = PixelLocalizations.maybeOf(context)?.labels
        /** Exact inherited legacy theme used by the old disabled color precedence. */
        val legacyTheme = PixelTheme.of(context)
        /** Exact inherited legacy style used for the Normal compatibility baseline. */
        val legacyStyle = legacyTheme.textButtonStyle
        /** Text-button-specific role and geometry tokens. */
        val componentTokens = themeTokens.components.textButton
        /** Blank caller text falls back to the localized text-button label. */
        val resolvedText = widget.text.takeIf { text -> text.isNotBlank() }
            ?: localizedLabels?.textButton
            ?: if (usesScopeLessLegacyVisuals) widget.text else themeTokens.labels.textButton
        /** Explicit legacy style; Default is absence so tokens remain live. */
        val explicitStyle = widget.style.takeUnless { style -> style == PixelTextButtonStyle.Default }
        /** Exact style selected by the pre-token facade contract. */
        val compatibilityStyle = explicitStyle ?: legacyStyle
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
        /** Whether a non-focus state should resolve through the component foreground map. */
        val usesTokenState = !baseStates.isNormal
        /** Typography metrics resolved from explicit style before the theme role. */
        val baseTextStyle = if (usesScopeLessLegacyVisuals) {
            compatibilityStyle.textStyle
        } else {
            explicitStyle?.textStyle ?: legacyStyle.textStyle
        }
        /** Foreground target resolved using explicit style above component state tokens. */
        val targetContentColor = when {
            usesScopeLessLegacyVisuals && disabled && widget.style == PixelTextButtonStyle.Default -> {
                legacyTheme.colors.disabled
            }
            usesScopeLessLegacyVisuals -> baseTextStyle.color
            explicitStyle != null -> explicitStyle.textStyle.color
            usesTokenState -> componentTokens.resolveContentColor(baseStates, themeTokens.colors)
                ?: baseTextStyle.color
            else -> baseTextStyle.color
        }
        /** Optional token container channel; legacy TextButton styles have no corresponding field. */
        val targetContainerColor = if (usesScopeLessLegacyVisuals) {
            PixelColor.Transparent
        } else {
            componentTokens.resolveContainerColor(baseStates, themeTokens.colors) ?: PixelColor.Transparent
        }
        /** Optional token outline channel; legacy TextButton styles have no corresponding field. */
        val targetBorderColor = if (usesScopeLessLegacyVisuals) {
            PixelColor.Transparent
        } else {
            componentTokens.resolveBorderColor(baseStates, themeTokens.colors) ?: PixelColor.Transparent
        }
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
        val elevationOffset = if (usesScopeLessLegacyVisuals) {
            0
        } else {
            componentTokens.resolveElevation(themeTokens.elevations)
        }
        /** Default component padding resolved through the current foundation spacing scale. */
        val tokenPadding = if (usesScopeLessLegacyVisuals) {
            compatibilityStyle.padding
        } else {
            componentTokens.resolvePadding(themeTokens.spacing)
        }
        /** Border width resolved through the current foundation border scale. */
        val resolvedBorderWidth = if (usesScopeLessLegacyVisuals) {
            0
        } else {
            componentTokens.resolveBorderWidth(themeTokens.borders)
        }
        /** Corner radius resolved through the current foundation radius scale. */
        val resolvedCornerRadius = if (usesScopeLessLegacyVisuals) {
            0
        } else {
            componentTokens.resolveCornerRadius(themeTokens.radii)
        }
        /** Theme-sized content before the independent focus indicator is applied. */
        val content = ConstrainedBox(
            constraints = PixelBoxConstraints(
                minWidth = if (usesScopeLessLegacyVisuals) {
                    0
                } else {
                    componentTokens.resolveMinimumWidth(themeTokens.sizes)
                },
                minHeight = if (usesScopeLessLegacyVisuals) {
                    0
                } else {
                    componentTokens.resolveMinimumHeight(themeTokens.sizes)
                },
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
                padding = if (usesScopeLessLegacyVisuals) {
                    compatibilityStyle.padding
                } else {
                    explicitStyle?.padding ?: tokenPadding
                },
                alignment = if (usesScopeLessLegacyVisuals) {
                    compatibilityStyle.alignment
                } else {
                    explicitStyle?.alignment ?: PixelTextButtonStyle.Default.alignment
                },
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

/** Historical uniform TextField outline width used before component border tokens. */
private const val LEGACY_INPUT_BORDER_WIDTH_PX: Int = 1

/** Historical trailing text inset used by end-aligned TextField content. */
private const val LEGACY_INPUT_END_TEXT_PADDING_PX: Int = 2

/** Historical uniform OutlinedButton content inset. */
private const val LEGACY_OUTLINED_BUTTON_PADDING_PX: Int = 2

/** Historical single-pixel OutlinedButton outline width. */
private const val LEGACY_OUTLINED_BUTTON_BORDER_WIDTH_PX: Int = 1
