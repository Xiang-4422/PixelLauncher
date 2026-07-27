package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Directionality
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.Expanded
import com.purride.pixelui.FormFieldDecoration
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.PaddingDirectional
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextDirection
import com.purride.pixelui.Widget

/** Marker appended to visible and spoken labels when decoration declares a required field. */
private const val REQUIRED_MARKER: String = "*"

/** Separator used to fold supporting decoration into the field's single semantic hint. */
private const val SEMANTIC_PART_SEPARATOR: String = ". "

/**
 * Normalized decoration consumed by both the visual wrapper and the input semantic boundary.
 *
 * @property visibleLabel Label text painted above the input, including the required marker.
 * @property supportingText Error-or-helper text painted below the input.
 * @property counter Caller-formatted counter retained beside either supporting state.
 * @property semanticLabel Explicit or visible field name before the required marker is applied.
 * @property semanticHint Supporting visual text folded into the unique TextField node.
 * @property semanticError Active validation message folded into the unique TextField node.
 * @property required Whether the input semantic label must receive the required marker.
 */
internal data class ResolvedFormFieldDecoration(
    val visibleLabel: String?,
    val supportingText: String?,
    val counter: String?,
    val semanticLabel: String?,
    val semanticHint: String?,
    val semanticError: String?,
    val required: Boolean,
)

/**
 * Resolves blank handling, error precedence, and one-node semantic composition for a TextField.
 */
internal fun FormFieldDecoration.resolveForTextField(
    semanticLabel: String?,
    semanticHint: String?,
    semanticError: String?,
): ResolvedFormFieldDecoration {
    /** Caller label retained verbatim except for trailing space before the required marker. */
    val fieldLabel = label.nonBlankOrNull()
    /** Label actually painted in the decoration layer. */
    val visibleLabel = when {
        fieldLabel != null && required -> fieldLabel.withFormFieldRequiredMarker(required = true)
        fieldLabel != null -> fieldLabel
        required -> REQUIRED_MARKER
        else -> null
    }
    /** Decoration error takes priority, while the legacy semantic error remains a visible fallback. */
    val activeError = error.nonBlankOrNull() ?: semanticError.nonBlankOrNull()
    /** Helper is hidden whenever an active error exists. */
    val supportingText = activeError ?: helper.nonBlankOrNull()
    /** Counter stays caller-formatted and visible beside helper or error. */
    val visibleCounter = counter.nonBlankOrNull()
    /** Explicit accessibility name wins, followed by the visible field label. */
    val resolvedSemanticLabel = semanticLabel.nonBlankOrNull() ?: fieldLabel
    /** Fully marked label used only to avoid repeating identical visible text in the hint. */
    val markedSemanticLabel = resolvedSemanticLabel?.withFormFieldRequiredMarker(required)
    /** Visible label context retained when the caller deliberately supplied a different spoken name. */
    val additionalVisibleLabel = visibleLabel?.takeIf { candidate ->
        candidate != REQUIRED_MARKER && candidate != markedSemanticLabel
    }
    /** Ordered, duplicate-free supporting parts announced through the field's hint. */
    val semanticParts = buildList {
        semanticHint.nonBlankOrNull()?.let(::add)
        additionalVisibleLabel?.let(::add)
        if (activeError == null) supportingText?.let(::add)
        visibleCounter?.let(::add)
    }.distinct()
    return ResolvedFormFieldDecoration(
        visibleLabel = visibleLabel,
        supportingText = supportingText,
        counter = visibleCounter,
        semanticLabel = resolvedSemanticLabel,
        semanticHint = semanticParts.takeIf(List<String>::isNotEmpty)
            ?.joinToString(separator = SEMANTIC_PART_SEPARATOR),
        semanticError = activeError,
        required = required,
    )
}

/** Returns this text only when it contains at least one non-whitespace character. */
private fun String?.nonBlankOrNull(): String? = this?.takeIf(String::isNotBlank)

/** Appends one locale-neutral marker without duplicating a caller-supplied trailing marker. */
internal fun String.withFormFieldRequiredMarker(required: Boolean): String {
    if (!required) return this
    /** Trailing whitespace is removed so the marker always has deterministic separation. */
    val normalized = trimEnd()
    if (normalized.isEmpty()) return REQUIRED_MARKER
    return if (normalized.endsWith(REQUIRED_MARKER)) normalized else "$normalized $REQUIRED_MARKER"
}

/**
 * Paints label and supporting decoration around a stable input child.
 *
 * The three direct Column slots never change order, so inserting or removing decoration cannot
 * remount the TextField child or discard its selection, focus node, or IME target ownership.
 */
internal data class FormFieldDecorationWidget(
    /** Normalized visual and semantic decoration. */
    val decoration: ResolvedFormFieldDecoration,
    /** Persistent component states already normalized by the public TextField facade. */
    val states: PixelControlStateSet,
    /** Whether the input uses the TextField selected/read-only visual role. */
    val readOnly: Boolean,
    /** Stable controlled TextField subtree placed in the middle slot. */
    val child: Widget,
    /** Retained identity shared with the public decorated field. */
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    /** Resolves live theme roles and builds the fixed three-slot decoration layout. */
    override fun build(context: BuildContext): Widget {
        /** Complete theme graph watched by this decoration frame. */
        val theme = PixelTheme.of(context)
        /** Existing TextField component family reused for every decoration state color. */
        val componentTokens = theme.components.textField
        /** Read-only participates in the same selected base state as the input surface. */
        val runtimeStates = if (readOnly) states + PixelControlState.Selected else states
        /** Focus remains an additive input outline and never replaces decoration foreground roles. */
        val baseStates = runtimeStates - PixelControlState.Focused
        /** State-resolved TextField foreground applied without introducing another token family. */
        val decorationColor = componentTokens.resolveContentColor(baseStates, theme.colors)
        /** Label metrics come from the shared label typography role. */
        val labelStyle = theme.typography.label.resolve(theme.colors).withColor(decorationColor)
        /** Helper, error, and counter metrics come from the shared caption typography role. */
        val supportingStyle = theme.typography.caption.resolve(theme.colors).withColor(decorationColor)
        /** Compact token spacing between label and input surface. */
        val labelGap = theme.spacing.extraSmall
        /** Compact token spacing between input surface and supporting row. */
        val supportingGap = theme.spacing.extraSmall
        /** Visual-only label prevents a second TalkBack node for painted decoration text. */
        val labelContent = decoration.visibleLabel?.let { label ->
            decorationText(
                text = label,
                style = labelStyle,
                part = DecorationPart.Label,
            )
        }
        /** Direction-aware helper/error and counter row, or null when both slots are empty. */
        val supportingContent = buildSupportingContent(
            context = context,
            style = supportingStyle,
            horizontalGap = theme.spacing.small,
        )
        return Column(
            children = listOf(
                FormFieldDecorationSlotWidget(
                    content = labelContent,
                    gap = labelGap,
                    placement = DecorationSlotPlacement.BeforeInput,
                    key = partKey(DecorationPart.LabelSlot),
                ),
                child,
                FormFieldDecorationSlotWidget(
                    content = supportingContent,
                    gap = supportingGap,
                    placement = DecorationSlotPlacement.AfterInput,
                    key = partKey(DecorationPart.SupportingSlot),
                ),
            ),
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            key = partKey(DecorationPart.Layout),
        )
    }

    /** Builds the helper/error and counter row with counter at directional end. */
    private fun buildSupportingContent(
        context: BuildContext,
        style: PixelTextStyle,
        horizontalGap: Int,
    ): Widget? {
        /** Helper or active error that flexes before the fixed caller counter. */
        val supporting = decoration.supportingText?.let { text ->
            Expanded(
                child = PaddingDirectional(
                    child = decorationText(
                        text = text,
                        style = style,
                        part = DecorationPart.Supporting,
                    ),
                    padding = EdgeInsetsDirectional.only(end = horizontalGap),
                    key = partKey(DecorationPart.SupportingPadding),
                ),
                key = partKey(DecorationPart.SupportingFlex),
            )
        }
        /** Caller-provided counter that remains visible in both helper and error states. */
        val counter = decoration.counter?.let { text ->
            decorationText(
                text = text,
                style = style,
                part = DecorationPart.Counter,
            )
        }
        if (supporting == null && counter == null) return null
        /** RTL reverses the visual child order so the counter remains at directional end. */
        val children = if (Directionality.of(context) == TextDirection.RTL) {
            listOfNotNull(counter, supporting)
        } else {
            listOfNotNull(supporting, counter)
        }
        return Row(
            children = children,
            mainAxisSize = MainAxisSize.MAX,
            mainAxisAlignment = if (supporting == null) MainAxisAlignment.END else MainAxisAlignment.START,
            key = partKey(DecorationPart.SupportingRow),
        )
    }

    /** Creates one ellipsized paint-only Text widget for a decoration slot. */
    private fun decorationText(
        text: String,
        style: PixelTextStyle,
        part: DecorationPart,
    ): Widget {
        /** Text is visually present but deliberately absent from the accessibility subtree. */
        val paintedText = Text(
            data = text,
            style = style,
            maxLines = 1,
            overflow = PixelTextOverflow.ELLIPSIS,
            key = partKey(part),
        )
        return VisualOnlyWidget(
            child = paintedText,
            key = partKey(part.visualOnlyPart),
        )
    }

    /** Derives a stable sibling-local key from the decorated field identity and [part]. */
    private fun partKey(part: DecorationPart): FormFieldDecorationPartKey {
        return FormFieldDecorationPartKey(owner = key, part = part)
    }
}

/** Applies [color] while retaining the typography role's complete metrics. */
private fun PixelTextStyle.withColor(color: com.purride.pixelcore.PixelColor?): PixelTextStyle {
    return color?.let { resolved -> copy(color = resolved) } ?: this
}

/** Whether a fixed decoration slot appears before or after the stable input child. */
private enum class DecorationSlotPlacement {
    /** Label slot whose gap follows visible content. */
    BeforeInput,

    /** Supporting slot whose gap precedes visible content. */
    AfterInput,
}

/**
 * Fixed root slot that collapses without changing the TextField child's sibling index.
 */
private data class FormFieldDecorationSlotWidget(
    /** Optional visual-only content for this frame. */
    val content: Widget?,
    /** Theme-resolved gap adjacent to the input surface. */
    val gap: Int,
    /** Side of the input surface owned by this slot. */
    val placement: DecorationSlotPlacement,
    /** Stable slot identity. */
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    /** Builds a zero-height placeholder or content padded toward the input surface. */
    override fun build(context: BuildContext): Widget {
        /** Absent decoration consumes no layout extent while preserving this retained slot. */
        val visibleContent = content ?: return SizedBox(height = 0, key = key)
        /** Gap is placed only on the edge adjacent to the input surface. */
        val padding = when (placement) {
            DecorationSlotPlacement.BeforeInput -> EdgeInsets.only(bottom = gap)
            DecorationSlotPlacement.AfterInput -> EdgeInsets.only(top = gap)
        }
        return Padding(
            child = visibleContent,
            padding = padding,
            key = key,
        )
    }
}

/** Stable subpart names used to keep decoration elements retained across content updates. */
private enum class DecorationPart {
    /** Root layout. */
    Layout,

    /** Fixed label slot. */
    LabelSlot,

    /** Painted label text. */
    Label,

    /** Label semantics-suppression wrapper. */
    LabelVisualOnly,

    /** Fixed supporting slot. */
    SupportingSlot,

    /** Supporting row. */
    SupportingRow,

    /** Flexible helper/error wrapper. */
    SupportingFlex,

    /** Directional helper/error padding. */
    SupportingPadding,

    /** Painted helper/error text. */
    Supporting,

    /** Supporting-text semantics-suppression wrapper. */
    SupportingVisualOnly,

    /** Painted counter text. */
    Counter,

    /** Counter semantics-suppression wrapper. */
    CounterVisualOnly,
    ;

    /** Matching visual-only wrapper part for each painted text part. */
    val visualOnlyPart: DecorationPart
        get() = when (this) {
            Label -> LabelVisualOnly
            Supporting -> SupportingVisualOnly
            Counter -> CounterVisualOnly
            else -> error("$this does not represent painted decoration text")
        }
}

/** Value-equal retained key scoped to one decorated field and one [part]. */
private data class FormFieldDecorationPartKey(
    /** Public field identity, or null when sibling scope alone provides identity. */
    val owner: Any?,
    /** Stable decoration subpart. */
    val part: DecorationPart,
)
