package com.purride.pixelui

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.internal.PixelCoreArtifactAccess
import com.purride.pixelui.internal.AutomaticFocusAction
import com.purride.pixelui.internal.InteractionDetector
import com.purride.pixelui.internal.activationKeyHandler

/**
 * 定义 `PixelRadioOption` 在 `SelectionControls` 中承担的数据与行为边界。
 *
 * Immutable business option rendered by [RadioGroup].
 *
 * [id] participates in retained identity, callback delivery, and controlled selection. It must
 * therefore remain value-stable while the same logical option moves within a rebuilt list.
 *
 * @param id Stable business identifier, independent from the option's current position.
 * @param label Non-blank visible and semantic label.
 * @param enabled Whether this option may be selected independently from the group capability.
 */
public data class PixelRadioOption<T : Any>(
    /** 公开 `SelectionControls` 的 `id` 配置或运行值。
 *
 * Stable business identifier used for selection and retained identity.
 */
    public val id: T,
    /** 公开 `SelectionControls` 的 `label` 配置或运行值。
 *
 * Visible and spoken option label.
 */
    public val label: String,
    /** 表示 `SelectionControls` 当前是否满足 `enabled` 对应条件。
 *
 * Per-option capability gate applied after the group state.
 */
    public val enabled: Boolean = true,
) {
    init {
        require(label.isNotBlank()) { "PixelRadioOption.label must not be blank." }
    }
}

/**
 * 执行 `SelectionControls` 的 `Radio` 公开行为；具体参数、返回和副作用见下文。
 *
 * Renders one controlled radio indicator.
 *
 * Pointer, Enter, Space, and accessibility click all invoke the same [onSelected] action. The
 * component never mutates [selected]; the caller must rebuild with the requested value. Loading
 * retains focus while suppressing activation, and Disabled removes the control from traversal.
 *
 * @param selected Caller-owned selected state exported as both checked and selected semantics.
 * @param onSelected Selection request callback, or null for a read-only disabled radio.
 * @param semanticLabel Required non-blank accessible name for the indicator.
 * @param states Persistent visual states; Selected and Disabled are normalized by this function.
 * @param enabled Caller capability gate independent from [states].
 * @param key Stable retained, focus, pointer, and semantics identity.
 */
public fun Radio(
    selected: Boolean,
    onSelected: (() -> Unit)?,
    semanticLabel: String,
    states: PixelControlStateSet = PixelControlStateSet.Normal,
    enabled: Boolean = true,
    key: Any? = null,
): Widget {
    requireNonBlankControlLabel(componentName = "Radio", semanticLabel = semanticLabel)
    /** Persistent states normalized with controlled selection and actual callback availability. */
    val effectiveStates = normalizedSelectionControlStates(
        states = states,
        selected = selected,
        enabled = enabled && onSelected != null,
    )
    /** Disabled removes traversal while Loading deliberately retains an existing focus node. */
    val focusable = PixelControlState.Disabled !in effectiveStates
    /** All activation adapters share this action and suppress it for Loading and Disabled. */
    val select: (() -> Boolean)? = onSelected
        ?.takeIf { focusable && PixelControlState.Loading !in effectiveStates }
        ?.let { callback ->
            {
                callback()
                true
            }
        }
    return AutomaticFocusAction(
        enabled = focusable,
        debugLabel = semanticLabel,
        onKeyEvent = select?.let(::activationKeyHandler),
        key = key,
    ) { _, _ ->
        PixelRadioStateWidget(
            selected = selected,
            states = effectiveStates,
            select = select,
            semanticLabel = semanticLabel,
            showLabel = false,
            focusWhenParentFocused = true,
            collectionItemInfo = null,
            key = key,
        )
    }
}

/**
 * 执行 `SelectionControls` 的 `RadioGroup` 公开行为；具体参数、返回和副作用见下文。
 *
 * Renders a controlled, vertically arranged, single-selection radio group.
 *
 * A non-empty [options] list must contain unique business ids and exactly one [selectedId]. An
 * empty group is valid only with a null selection. The complete group forms one Tab stop; Left and
 * Up select the previous enabled option, Right and Down select the next enabled option, and
 * Enter/Space reselect the current keyboard anchor. All changes are requests delivered through
 * [onSelected], so the caller remains the sole owner of selection.
 *
 * @param options Immutable options whose ids remain stable across insertion and reordering.
 * @param selectedId Caller-owned selected business id, or null only when [options] is empty.
 * @param onSelected Selection request callback, or null for a read-only disabled group.
 * @param semanticLabel Required non-blank accessible name for the collection.
 * @param states Persistent group states inherited by every option.
 * @param enabled Caller capability gate for the complete group.
 * @param key Stable retained, focus, keyboard, and collection-semantics identity.
 */
public fun <T : Any> RadioGroup(
    options: List<PixelRadioOption<T>>,
    selectedId: T?,
    onSelected: ((T) -> Unit)?,
    semanticLabel: String,
    states: PixelControlStateSet = PixelControlStateSet.Normal,
    enabled: Boolean = true,
    key: Any? = null,
): Widget {
    requireNonBlankControlLabel(componentName = "RadioGroup", semanticLabel = semanticLabel)
    /** Validated selected position proving that a non-empty group has exactly one selection. */
    val selectedIndex = validateRadioGroupSelection(options = options, selectedId = selectedId)
    /** Persistent group states normalized with callback and caller capability. */
    val effectiveGroupStates = normalizedSelectionControlStates(
        states = states,
        selected = false,
        enabled = enabled && onSelected != null,
    )
    /** Enabled positions retained in visual order for cyclic keyboard traversal. */
    val enabledIndices = options.indices.filter { index -> options[index].enabled }
    /** Disabled removes the group from traversal; an empty/all-disabled group has no Tab stop. */
    val focusable = PixelControlState.Disabled !in effectiveGroupStates && enabledIndices.isNotEmpty()
    /** Loading retains the group focus owner while suppressing every selection route. */
    val interactive = focusable && PixelControlState.Loading !in effectiveGroupStates
    /** Keyboard anchor uses the selection when enabled, otherwise the first eligible option. */
    val keyboardAnchorIndex = selectedIndex
        .takeIf { index -> index >= 0 && options[index].enabled }
        ?: enabledIndices.firstOrNull()
        ?: -1
    /** Shared group key handler rebuilt from the latest controlled selection and option order. */
    val keyHandler = onSelected?.takeIf { interactive }?.let { callback ->
        radioGroupKeyHandler(
            options = options,
            anchorIndex = keyboardAnchorIndex,
            onSelected = callback,
        )
    }
    return AutomaticFocusAction(
        enabled = focusable,
        debugLabel = semanticLabel,
        onKeyEvent = keyHandler,
        key = key,
    ) { context, _ ->
        /** Latest inherited tokens used for group spacing and option rendering. */
        val theme = PixelTheme.tokensOf(context)
        /** Semantic collection descriptor shared by the rendered group node. */
        val collectionInfo = PixelSemanticsCollectionInfo(
            rowCount = options.size,
            columnCount = 1,
            selectionMode = PixelSemanticsSelectionMode.SINGLE,
        )
        /** Keyed radio rows preserving business identity when options are reordered. */
        val optionWidgets = options.mapIndexed { index, option ->
            /** Whether this exact business option owns the caller-controlled selection. */
            val optionSelected = index == selectedIndex
            /** Per-option capability merged without discarding selected/error/loading state. */
            val optionStates = normalizedSelectionControlStates(
                states = effectiveGroupStates,
                selected = optionSelected,
                enabled = option.enabled && PixelControlState.Disabled !in effectiveGroupStates,
            )
            /** One shared selection action used by pointer and accessibility for this option. */
            val selectOption: (() -> Boolean)? = onSelected
                ?.takeIf { interactive && option.enabled }
                ?.let { callback ->
                    {
                        callback(option.id)
                        true
                    }
                }
            /** Stable retained key composed from the group and this option's business id. */
            val optionKey = PixelRadioOptionKey(groupKey = key, optionId = option.id)
            PixelRadioStateWidget(
                selected = optionSelected,
                states = optionStates,
                select = selectOption,
                semanticLabel = option.label,
                showLabel = true,
                focusWhenParentFocused = index == keyboardAnchorIndex,
                collectionItemInfo = PixelSemanticsCollectionItemInfo(
                    rowIndex = index,
                    columnIndex = 0,
                    selected = optionSelected,
                ),
                key = optionKey,
            )
        }
        /** Visible one-column layout beneath a non-merging single-selection collection node. */
        val groupColumn = Column(
            children = optionWidgets,
            spacing = theme.spacing.extraSmall,
            crossAxisAlignment = CrossAxisAlignment.START,
            key = key?.let { value -> "$value-radio-options" },
        )
        Semantics(
            label = semanticLabel,
            role = PixelSemanticRole.GENERIC,
            enabled = interactive,
            collectionInfo = collectionInfo,
            mergeDescendants = false,
            child = groupColumn,
            key = key?.let { value -> "$value-radio-group-semantics" },
        )
    }
}

/**
 * 执行 `SelectionControls` 的 `IconButton` 公开行为；具体参数、返回和副作用见下文。
 *
 * Renders a controlled icon-only button with one merged semantic boundary.
 *
 * The icon bitmap is treated as an alpha mask and tinted from the active icon-button content
 * token, so Disabled, Error, Loading, and custom high-contrast schemes remain legible. Pointer,
 * Enter, Space, and accessibility click all invoke the same callback. Loading keeps focus while
 * blocking activation; Disabled removes traversal. The accessible name is mandatory because the
 * visual icon has no text alternative of its own.
 *
 * @param icon Bitmap icon whose non-transparent pixels form the tint mask.
 * @param onPressed Shared activation callback, or null for a read-only disabled button.
 * @param semanticLabel Required non-blank accessible name.
 * @param selected Caller-owned persistent selected presentation and structured semantic state.
 * @param states Persistent visual states normalized with Selected and Disabled.
 * @param enabled Caller capability gate independent from [states].
 * @param key Stable retained, focus, pointer, and semantics identity.
 */
public fun IconButton(
    icon: PixelIconData,
    onPressed: (() -> Unit)?,
    semanticLabel: String,
    selected: Boolean = false,
    states: PixelControlStateSet = PixelControlStateSet.Normal,
    enabled: Boolean = true,
    key: Any? = null,
): Widget {
    requireNonBlankControlLabel(componentName = "IconButton", semanticLabel = semanticLabel)
    /** Persistent states normalized with selected presentation and callback availability. */
    val effectiveStates = normalizedSelectionControlStates(
        states = states,
        selected = selected,
        enabled = enabled && onPressed != null,
    )
    /** Disabled removes traversal while Loading intentionally preserves an existing focus node. */
    val focusable = PixelControlState.Disabled !in effectiveStates
    /** Pointer, keyboard, and semantics share one capability-guarded activation action. */
    val activate: (() -> Boolean)? = onPressed
        ?.takeIf { focusable && PixelControlState.Loading !in effectiveStates }
        ?.let { callback ->
            {
                callback()
                true
            }
        }
    return AutomaticFocusAction(
        enabled = focusable,
        debugLabel = semanticLabel,
        onKeyEvent = activate?.let(::activationKeyHandler),
        key = key,
    ) { _, _ ->
        PixelIconButtonStateWidget(
            icon = icon,
            states = effectiveStates,
            selected = selected,
            activate = activate,
            semanticLabel = semanticLabel,
            key = key,
        )
    }
}

/** Stable retained key derived from a group identity and one business option id. */
private data class PixelRadioOptionKey(
    /** Optional caller-owned group identity. */
    val groupKey: Any?,
    /** Stable business identity independent from the current row index. */
    val optionId: Any,
)

/** Retained Radio configuration whose hover and press states are runtime-owned. */
private data class PixelRadioStateWidget(
    /** Controlled logical selection rendered as checked geometry. */
    val selected: Boolean,
    /** Persistent normalized states inherited from the standalone control or group. */
    val states: PixelControlStateSet,
    /** Shared pointer and semantic selection request, or null while inert. */
    val select: (() -> Boolean)?,
    /** Required visible/spoken label. */
    val semanticLabel: String,
    /** Whether the label is painted beside the radio indicator. */
    val showLabel: Boolean,
    /** Whether an inherited group focus node highlights this exact option. */
    val focusWhenParentFocused: Boolean,
    /** Optional row position within a RadioGroup collection. */
    val collectionItemInfo: PixelSemanticsCollectionItemInfo?,
    /** Stable retained, pointer, and semantic identity. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates one retained owner for pointer hover and press micro-states. */
    override fun createState(): State<out StatefulWidget> = PixelRadioState()
}

/** Runtime interaction owner for one standalone or grouped Radio. */
private class PixelRadioState : State<PixelRadioStateWidget>() {
    /** Whether this Radio currently owns a captured pointer press. */
    private var pressed: Boolean = false

    /** Whether a mouse or stylus currently hovers over this Radio target. */
    private var hovered: Boolean = false

    /** Resolves token paint, pointer behavior, focus, and structured radio semantics. */
    override fun build(context: BuildContext): Widget {
        /** Complete inherited theme token graph. */
        val theme = PixelTheme.tokensOf(context)
        /** Provider labels override only status text while the option name remains caller-owned. */
        val localizedLabels = PixelLocalizations.maybeOf(context)?.labels ?: theme.labels
        /** Independent Radio component tokens. */
        val tokens = theme.components.radio
        /** Focus node owned by the standalone control or complete RadioGroup. */
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) context.watch(focusNode)
        /** Runtime states after focus and pointer micro-states are merged. */
        var resolvedStates = widget.states
        if (focusNode?.isFocused == true && widget.focusWhenParentFocused) {
            resolvedStates += PixelControlState.Focused
        }
        if (pressed) resolvedStates += PixelControlState.Pressed
        if (hovered) resolvedStates += PixelControlState.Hovered
        if (
            PixelControlState.Disabled in resolvedStates ||
            PixelControlState.Loading in resolvedStates
        ) {
            pressed = false
            hovered = false
            resolvedStates -= PixelControlState.Pressed
            resolvedStates -= PixelControlState.Hovered
        }
        /** Focus is painted additively and excluded from base state color priority. */
        val baseStates = resolvedStates - PixelControlState.Focused
        /** Concrete themed radio fill. */
        val fillColor = tokens.resolveContainerColor(baseStates, theme.colors)
        /** Concrete themed radio outline. */
        val borderColor = tokens.resolveBorderColor(baseStates, theme.colors)
        /** Concrete themed marker and label color. */
        val contentColor = tokens.resolveContentColor(baseStates, theme.colors) ?: theme.colors.onSurface
        /** Token-resolved radio indicator width. */
        val indicatorWidth = tokens.resolveMinimumWidth(theme.sizes)
        /** Token-resolved radio indicator height. */
        val indicatorHeight = tokens.resolveMinimumHeight(theme.sizes)
        /** Selected marker extent retained within the smallest indicator axis. */
        val markerExtent = (minOf(indicatorWidth, indicatorHeight) / 3).coerceAtLeast(1)
        /** Inner selected marker, or equal-sized transparent layout placeholder. */
        val marker = if (widget.selected) {
            PixelSurface(
                width = markerExtent,
                height = markerExtent,
                decoration = PixelSurfaceDecoration(
                    fillColor = contentColor,
                    borderWidth = 0,
                    cornerRadius = markerExtent,
                ),
            )
        } else {
            SizedBox(width = markerExtent, height = markerExtent)
        }
        /** Circular token-sized radio indicator. */
        val indicator = PixelSurface(
            width = indicatorWidth,
            height = indicatorHeight,
            decoration = PixelSurfaceDecoration(
                fillColor = fillColor,
                borderColor = borderColor,
                borderWidth = tokens.resolveBorderWidth(theme.borders),
                cornerRadius = tokens.resolveCornerRadius(theme.radii),
                shadowColor = theme.colors.shadow,
                shadowOffset = tokens.resolveElevation(theme.elevations),
            ),
            child = Center(child = marker),
        )
        /** Visible option row for RadioGroup, or the standalone indicator alone. */
        val content = if (widget.showLabel) {
            Row(
                children = listOf(
                    indicator,
                    Text(
                        data = widget.semanticLabel,
                        style = theme.typography.body.resolve(theme.colors).copy(color = contentColor),
                    ),
                ),
                spacing = theme.spacing.extraSmall,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
            )
        } else {
            indicator
        }
        /** Pointer target spans the complete visible row only while selection is available. */
        val interactiveContent = widget.select?.let { select ->
            InteractionDetector(
                child = content,
                onTap = { select.invoke() },
                onPressedChanged = ::updatePressed,
                onHoveredChanged = ::updateHovered,
                key = widget.key,
            )
        } ?: content
        return FocusableControl(
            label = widget.semanticLabel,
            role = PixelSemanticRole.RADIO_BUTTON,
            enabled = PixelControlState.Disabled !in resolvedStates,
            semanticsEnabled = widget.select != null,
            automaticallyFocusable = false,
            focusWhenParentFocused = widget.focusWhenParentFocused,
            selected = widget.selected,
            checked = widget.selected,
            value = localizedLabels.loading.takeIf { PixelControlState.Loading in resolvedStates },
            error = localizedLabels.error.takeIf { PixelControlState.Error in resolvedStates },
            collectionItemInfo = widget.collectionItemInfo,
            focusIndicator = tokens.focusIndicator ?: PixelFocusIndicatorTokens.Default,
            actions = PixelSemanticsActions(onClick = widget.select),
            child = interactiveContent,
            key = widget.key,
        )
    }

    /** Updates captured pointer ownership exactly once per transition. */
    private fun updatePressed(nextPressed: Boolean) {
        if (pressed == nextPressed) return
        setState { pressed = nextPressed }
    }

    /** Updates hover membership exactly once per mouse or stylus boundary transition. */
    private fun updateHovered(nextHovered: Boolean) {
        if (hovered == nextHovered) return
        setState { hovered = nextHovered }
    }
}

/** Retained IconButton configuration whose hover and press states are runtime-owned. */
private data class PixelIconButtonStateWidget(
    /** Source alpha-mask icon. */
    val icon: PixelIconData,
    /** Persistent normalized states including selected and capability states. */
    val states: PixelControlStateSet,
    /** Caller-owned selected presentation exported through semantics. */
    val selected: Boolean,
    /** Shared pointer, keyboard, and semantics activation action. */
    val activate: (() -> Boolean)?,
    /** Required spoken control name. */
    val semanticLabel: String,
    /** Stable retained, pointer, focus, and semantic identity. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates one retained owner for pointer micro-states and tinted bitmap caching. */
    override fun createState(): State<out StatefulWidget> = PixelIconButtonState()
}

/** Runtime interaction and tint-cache owner for one IconButton. */
private class PixelIconButtonState : State<PixelIconButtonStateWidget>() {
    /** Whether this IconButton currently owns a captured pointer press. */
    private var pressed: Boolean = false

    /** Whether a mouse or stylus currently hovers over this IconButton. */
    private var hovered: Boolean = false

    /** Source bitmap used by [cachedTintedBitmap]. */
    private var cachedSourceBitmap: PixelBitmap? = null

    /** Content color used by [cachedTintedBitmap]. */
    private var cachedTintColor: PixelColor? = null

    /** Last alpha-mask tint result reused while source and theme color are unchanged. */
    private var cachedTintedBitmap: PixelBitmap? = null

    /** Resolves independent icon-button tokens, pointer feedback, focus, and merged semantics. */
    override fun build(context: BuildContext): Widget {
        /** Complete inherited theme token graph. */
        val theme = PixelTheme.tokensOf(context)
        /** Provider labels override only status text while the required icon name stays explicit. */
        val localizedLabels = PixelLocalizations.maybeOf(context)?.labels ?: theme.labels
        /** Independent IconButton component tokens. */
        val tokens = theme.components.iconButton
        /** Focus node provided by the public automatic focus boundary. */
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) context.watch(focusNode)
        /** Runtime states after actual focus and pointer micro-states are merged. */
        var resolvedStates = widget.states
        if (focusNode?.isFocused == true) resolvedStates += PixelControlState.Focused
        if (pressed) resolvedStates += PixelControlState.Pressed
        if (hovered) resolvedStates += PixelControlState.Hovered
        if (
            PixelControlState.Disabled in resolvedStates ||
            PixelControlState.Loading in resolvedStates
        ) {
            pressed = false
            hovered = false
            resolvedStates -= PixelControlState.Pressed
            resolvedStates -= PixelControlState.Hovered
        }
        /** Focus is painted additively rather than replacing selected or validation colors. */
        val baseStates = resolvedStates - PixelControlState.Focused
        /** Concrete themed button fill. */
        val fillColor = tokens.resolveContainerColor(baseStates, theme.colors)
        /** Concrete themed button outline. */
        val borderColor = tokens.resolveBorderColor(baseStates, theme.colors)
        /** Concrete themed icon tint. */
        val contentColor = tokens.resolveContentColor(baseStates, theme.colors) ?: theme.colors.onSurface
        /** Cached tint result preserving the source bitmap's per-pixel alpha mask. */
        val tintedBitmap = resolveTintedBitmap(source = widget.icon.bitmap, tint = contentColor)
        /** Token-sized icon-only surface with a centered, non-semantic visual descendant. */
        val surface = PixelSurface(
            width = tokens.resolveMinimumWidth(theme.sizes),
            height = tokens.resolveMinimumHeight(theme.sizes),
            padding = tokens.resolvePadding(theme.spacing),
            decoration = PixelSurfaceDecoration(
                fillColor = fillColor,
                borderColor = borderColor,
                borderWidth = tokens.resolveBorderWidth(theme.borders),
                cornerRadius = tokens.resolveCornerRadius(theme.radii),
                shadowColor = theme.colors.shadow,
                shadowOffset = tokens.resolveElevation(theme.elevations),
            ),
            child = Center(child = Icon(PixelIconData(tintedBitmap))),
        )
        /** Pointer target spans the complete token minimum surface while activation is available. */
        val interactiveSurface = widget.activate?.let { activate ->
            InteractionDetector(
                child = surface,
                onTap = { activate.invoke() },
                onPressedChanged = ::updatePressed,
                onHoveredChanged = ::updateHovered,
                key = widget.key,
            )
        } ?: surface
        return FocusableControl(
            label = widget.semanticLabel,
            role = PixelSemanticRole.BUTTON,
            enabled = PixelControlState.Disabled !in resolvedStates,
            semanticsEnabled = widget.activate != null,
            automaticallyFocusable = false,
            selected = widget.selected,
            value = localizedLabels.loading.takeIf { PixelControlState.Loading in resolvedStates },
            error = localizedLabels.error.takeIf { PixelControlState.Error in resolvedStates },
            focusIndicator = tokens.focusIndicator ?: PixelFocusIndicatorTokens.Default,
            actions = PixelSemanticsActions(onClick = widget.activate),
            child = interactiveSurface,
            key = widget.key,
        )
    }

    /** Returns a cached alpha-mask tint result for the current source and content color. */
    private fun resolveTintedBitmap(source: PixelBitmap, tint: PixelColor): PixelBitmap {
        /** Existing result that is valid only for the identical immutable source and tint. */
        val cached = cachedTintedBitmap
        if (cachedSourceBitmap === source && cachedTintColor == tint && cached != null) return cached
        /** Fresh tinted bitmap created from the immutable source alpha channel. */
        val tinted = tintAlphaMask(source = source, tint = tint)
        cachedSourceBitmap = source
        cachedTintColor = tint
        cachedTintedBitmap = tinted
        return tinted
    }

    /** Updates captured pointer ownership exactly once per transition. */
    private fun updatePressed(nextPressed: Boolean) {
        if (pressed == nextPressed) return
        setState { pressed = nextPressed }
    }

    /** Updates hover membership exactly once per mouse or stylus boundary transition. */
    private fun updateHovered(nextHovered: Boolean) {
        if (hovered == nextHovered) return
        setState { hovered = nextHovered }
    }
}

/** Adds controlled selection and actual capability to one persistent component state set. */
private fun normalizedSelectionControlStates(
    states: PixelControlStateSet,
    selected: Boolean,
    enabled: Boolean,
): PixelControlStateSet {
    /** Accumulator retaining every caller state except the explicitly controlled selection bit. */
    var effectiveStates = states - PixelControlState.Selected
    if (selected) effectiveStates += PixelControlState.Selected
    if (!enabled) effectiveStates += PixelControlState.Disabled
    return effectiveStates
}

/** Rejects missing, duplicate, or ambiguous controlled RadioGroup selection. */
private fun <T : Any> validateRadioGroupSelection(
    options: List<PixelRadioOption<T>>,
    selectedId: T?,
): Int {
    /** Distinct option ids proving that business identity is unambiguous. */
    val distinctIds = options.map(PixelRadioOption<T>::id).toSet()
    require(distinctIds.size == options.size) { "RadioGroup option ids must be unique." }
    if (options.isEmpty()) {
        require(selectedId == null) { "An empty RadioGroup must use selectedId = null." }
        return -1
    }
    require(selectedId != null) { "A non-empty RadioGroup requires exactly one selectedId." }
    /** Exact selected position after duplicate ids have already been rejected. */
    val selectedIndex = options.indexOfFirst { option -> option.id == selectedId }
    require(selectedIndex >= 0) { "RadioGroup selectedId must match exactly one option id." }
    return selectedIndex
}

/** Creates four-direction cyclic selection and Enter/Space activation for one RadioGroup. */
private fun <T : Any> radioGroupKeyHandler(
    options: List<PixelRadioOption<T>>,
    anchorIndex: Int,
    onSelected: (T) -> Unit,
): (PixelKeyEvent) -> Boolean = { event ->
    when (event.key) {
        PixelKey.ARROW_LEFT,
        PixelKey.ARROW_UP,
        -> requestAdjacentRadioSelection(
            options = options,
            anchorIndex = anchorIndex,
            delta = -1,
            onSelected = onSelected,
        )
        PixelKey.ARROW_RIGHT,
        PixelKey.ARROW_DOWN,
        -> requestAdjacentRadioSelection(
            options = options,
            anchorIndex = anchorIndex,
            delta = 1,
            onSelected = onSelected,
        )
        PixelKey.ENTER,
        PixelKey.SPACE,
        -> options.getOrNull(anchorIndex)?.takeIf(PixelRadioOption<T>::enabled)?.let { option ->
            onSelected(option.id)
            true
        } ?: false
        else -> false
    }
}

/** Requests the next enabled business option in cyclic visual order. */
private fun <T : Any> requestAdjacentRadioSelection(
    options: List<PixelRadioOption<T>>,
    anchorIndex: Int,
    delta: Int,
    onSelected: (T) -> Unit,
): Boolean {
    if (options.isEmpty() || anchorIndex !in options.indices) return false
    /** Candidate index advanced cyclically until an enabled option is found. */
    var candidateIndex = anchorIndex
    repeat(options.size) {
        candidateIndex = Math.floorMod(candidateIndex + delta, options.size)
        /** Candidate business option for this traversal step. */
        val candidate = options[candidateIndex]
        if (candidate.enabled) {
            onSelected(candidate.id)
            return true
        }
    }
    return false
}

/** Tints every non-transparent source pixel while preserving and combining alpha channels. */
private fun tintAlphaMask(source: PixelBitmap, tint: PixelColor): PixelBitmap {
    /** SDK 内部只读源像素；不得修改或向消费者暴露。 */
    val sourcePixels = PixelCoreArtifactAccess.pixelsUnsafe(source)
    /** Destination pixels receiving the tint RGB and combined source/tint alpha. */
    val pixels = IntArray(sourcePixels.size)
    sourcePixels.forEachIndexed { index, sourceArgb ->
        /** Source mask opacity in the standard ARGB high byte. */
        val sourceAlpha = (sourceArgb ushr 24) and 0xFF
        /** Product alpha preserves translucent source edges and translucent theme colors. */
        val resolvedAlpha = sourceAlpha * tint.alpha / 0xFF
        pixels[index] = if (resolvedAlpha == 0) {
            PixelColor.Transparent.argb
        } else {
            PixelColor.fromArgb(resolvedAlpha, tint.red, tint.green, tint.blue).argb
        }
    }
    return PixelBitmap(width = source.width, height = source.height, pixels = pixels)
}

/** Enforces descriptive accessible names before a control enters the retained tree. */
private fun requireNonBlankControlLabel(componentName: String, semanticLabel: String) {
    require(semanticLabel.isNotBlank()) { "$componentName.semanticLabel must not be blank." }
}
