package com.purride.pixelui

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.internal.PixelCoreArtifactAccess
import com.purride.pixelui.internal.AutomaticFocusAction
import com.purride.pixelui.internal.InteractionDetector
import com.purride.pixelui.internal.mergeControlStates

/**
 * 定义 `PixelNavigationDestination` 在 `NavigationControls` 中承担的数据与行为边界。
 *
 * Immutable application destination rendered by [NavigationBar] or [NavigationRail].
 *
 * [id] is the only selection, callback, controller, and retained-state identity. Visual position
 * is deliberately excluded so inserting or reordering destinations cannot redirect an action to
 * another stack.
 *
 * @property id Stable, non-blank business identifier; controller-bound controls use it as stack id.
 * @property label Non-blank visible and spoken destination name.
 * @property icon Normal-state icon whose alpha channel is tinted from component tokens.
 * @property selectedIcon Optional selected-state icon; [icon] remains the fallback.
 * @property enabled Whether this destination can be selected independently from the group state.
 */
public data class PixelNavigationDestination(
    public val id: String,
    public val label: String,
    public val icon: PixelIconData,
    public val selectedIcon: PixelIconData? = null,
    public val enabled: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "PixelNavigationDestination.id must not be blank." }
        require(label.isNotBlank()) { "PixelNavigationDestination.label must not be blank." }
    }
}

/**
 * 执行 `NavigationControls` 的 `NavigationBar` 公开行为；具体参数、返回和副作用见下文。
 *
 * Renders a controlled bottom application-navigation bar.
 *
 * The complete bar is one focus-traversal stop. Left/Right select the adjacent enabled business
 * destination, while Enter/Space reselect the current destination. [selectedId] is never converted
 * into persistent index identity; the caller owns it and must rebuild after [onSelected].
 *
 * @param destinations Non-empty dynamic destination list with unique ids and labels.
 * @param selectedId Caller-owned id that must match exactly one destination.
 * @param onSelected Pointer, keyboard, or accessibility selection request by stable id.
 * @param states Persistent component states inherited by every destination.
 * @param enabled Whole-bar capability gate; Disabled also removes its single Tab stop.
 * @param semanticLabel Non-blank collection name announced before destination items. The
 * historical `"Navigation bar"` default is also the old-overload omission sentinel, so an
 * installed localization provider replaces that exact value; any other explicit value wins.
 * @param key Stable group identity retained independently from destination order.
 */
@JvmName("NavigationBar")
public fun NavigationBar(
    destinations: List<PixelNavigationDestination>,
    selectedId: String,
    onSelected: (String) -> Unit,
    states: PixelControlStateSet = PixelControlStateSet.Normal,
    enabled: Boolean = true,
    semanticLabel: String = DEFAULT_NAVIGATION_BAR_LABEL,
    key: Any? = null,
): Widget = buildNavigationControl(
    destinations = destinations,
    selectedId = selectedId,
    onRequestSelection = { destinationId ->
        onSelected(destinationId)
        true
    },
    states = states,
    enabled = enabled,
    semanticLabel = semanticLabel,
    orientation = PixelNavigationOrientation.Bar,
    key = key,
)

/**
 * 执行 `NavigationControls` 的 `NavigationBar` 公开行为；具体参数、返回和副作用见下文。
 *
 * Renders a bottom bar bound directly to independent stacks in [controller].
 *
 * Selecting another destination activates its already-mounted stack without clearing inactive
 * entries. Reselecting the active destination optionally pops only that stack to its root. Back
 * and secondary-root fallback remain entirely owned by [PixelMultiStackNavigatorController].
 *
 * @param destinations Destinations whose ids resolve to controller stack ids after host attachment.
 * @param controller Multi-stack selection and back-policy owner observed for active-id changes.
 * @param popToRootOnReselect Whether active-destination activation clears entries above its root.
 * @param animated Whether a reselect pop-to-root uses the child Navigator transition policy.
 * @param states Persistent component states inherited by every destination.
 * @param enabled Whole-bar capability gate.
 * @param semanticLabel Non-blank collection name announced before destination items. The
 * historical `"Navigation bar"` default is also the old-overload omission sentinel, so an
 * installed localization provider replaces that exact value; any other explicit value wins.
 * @param key Stable binding and group identity.
 */
@JvmName("NavigationBarWithController")
public fun NavigationBar(
    destinations: List<PixelNavigationDestination>,
    controller: PixelMultiStackNavigatorController,
    popToRootOnReselect: Boolean = false,
    animated: Boolean = true,
    states: PixelControlStateSet = PixelControlStateSet.Normal,
    enabled: Boolean = true,
    semanticLabel: String = DEFAULT_NAVIGATION_BAR_LABEL,
    key: Any? = null,
): Widget = PixelControllerNavigationBinding(
    destinations = destinations,
    controller = controller,
    popToRootOnReselect = popToRootOnReselect,
    animated = animated,
    states = states,
    enabled = enabled,
    semanticLabel = semanticLabel,
    orientation = PixelNavigationOrientation.Bar,
    key = key,
)

/**
 * 执行 `NavigationControls` 的 `NavigationRail` 公开行为；具体参数、返回和副作用见下文。
 *
 * Renders a controlled side application-navigation rail.
 *
 * The complete rail is one focus-traversal stop. Up/Down select adjacent enabled destinations;
 * Enter/Space reselect the current stable id. Pointer and accessibility actions share the same
 * capability-guarded callback.
 *
 * @param destinations Non-empty dynamic destination list with unique ids and labels.
 * @param selectedId Caller-owned id that must match exactly one destination.
 * @param onSelected Pointer, keyboard, or accessibility selection request by stable id.
 * @param states Persistent component states inherited by every destination.
 * @param enabled Whole-rail capability gate; Disabled removes its single Tab stop.
 * @param semanticLabel Non-blank collection name announced before destination items. The
 * historical `"Navigation rail"` default is also the old-overload omission sentinel, so an
 * installed localization provider replaces that exact value; any other explicit value wins.
 * @param key Stable group identity retained independently from destination order.
 */
@JvmName("NavigationRail")
public fun NavigationRail(
    destinations: List<PixelNavigationDestination>,
    selectedId: String,
    onSelected: (String) -> Unit,
    states: PixelControlStateSet = PixelControlStateSet.Normal,
    enabled: Boolean = true,
    semanticLabel: String = DEFAULT_NAVIGATION_RAIL_LABEL,
    key: Any? = null,
): Widget = buildNavigationControl(
    destinations = destinations,
    selectedId = selectedId,
    onRequestSelection = { destinationId ->
        onSelected(destinationId)
        true
    },
    states = states,
    enabled = enabled,
    semanticLabel = semanticLabel,
    orientation = PixelNavigationOrientation.Rail,
    key = key,
)

/**
 * 执行 `NavigationControls` 的 `NavigationRail` 公开行为；具体参数、返回和副作用见下文。
 *
 * Renders a side rail bound directly to independent stacks in [controller].
 *
 * Stack switching is lossless for every inactive child. Optional active reselect behavior delegates
 * to the controller's targeted clear operation, while controller back handling continues to pop the
 * active nested stack first and return secondary roots to the initial stack second.
 *
 * @param destinations Destinations whose ids resolve to controller stack ids after host attachment.
 * @param controller Multi-stack selection and back-policy owner observed for active-id changes.
 * @param popToRootOnReselect Whether active-destination activation clears entries above its root.
 * @param animated Whether a reselect pop-to-root uses the child Navigator transition policy.
 * @param states Persistent component states inherited by every destination.
 * @param enabled Whole-rail capability gate.
 * @param semanticLabel Non-blank collection name announced before destination items. The
 * historical `"Navigation rail"` default is also the old-overload omission sentinel, so an
 * installed localization provider replaces that exact value; any other explicit value wins.
 * @param key Stable binding and group identity.
 */
@JvmName("NavigationRailWithController")
public fun NavigationRail(
    destinations: List<PixelNavigationDestination>,
    controller: PixelMultiStackNavigatorController,
    popToRootOnReselect: Boolean = false,
    animated: Boolean = true,
    states: PixelControlStateSet = PixelControlStateSet.Normal,
    enabled: Boolean = true,
    semanticLabel: String = DEFAULT_NAVIGATION_RAIL_LABEL,
    key: Any? = null,
): Widget = PixelControllerNavigationBinding(
    destinations = destinations,
    controller = controller,
    popToRootOnReselect = popToRootOnReselect,
    animated = animated,
    states = states,
    enabled = enabled,
    semanticLabel = semanticLabel,
    orientation = PixelNavigationOrientation.Rail,
    key = key,
)

/** Visual and keyboard axis shared by the two public application-navigation components. */
private enum class PixelNavigationOrientation {
    /** Horizontal bottom bar using Left/Right movement. */
    Bar,

    /** Vertical side rail using Up/Down movement. */
    Rail,
}

/** Retained controller adapter that rebuilds selection whenever the active stack changes. */
private data class PixelControllerNavigationBinding(
    /** Current dynamic destination definitions. */
    val destinations: List<PixelNavigationDestination>,
    /** Controller whose active stack drives controlled selection. */
    val controller: PixelMultiStackNavigatorController,
    /** Active-stack reselection policy delegated without touching inactive stacks. */
    val popToRootOnReselect: Boolean,
    /** Child Navigator clearing animation policy. */
    val animated: Boolean,
    /** Persistent group component states. */
    val states: PixelControlStateSet,
    /** Whole-group capability gate. */
    val enabled: Boolean,
    /** Spoken collection label. */
    val semanticLabel: String,
    /** Bar or rail layout and keyboard axis. */
    val orientation: PixelNavigationOrientation,
    /** Stable public binding identity. */
    override val key: Any?,
) : StatelessWidget(key = key) {
    /** Observes active-stack changes and builds one ordinary controlled navigation group. */
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        validateControllerDestinations(destinations = destinations, controller = controller)
        /** Stable child key keeps the group focus owner below this adapter across notifications. */
        val controlKey = PixelNavigationControlKey(ownerKey = key, orientation = orientation)
        return buildNavigationControl(
            destinations = destinations,
            selectedId = controller.activeStackId,
            onRequestSelection = { destinationId ->
                controller.selectStack(
                    stackId = destinationId,
                    popToRootOnReselect = popToRootOnReselect,
                    animated = animated,
                ) != PixelStackSelectionResult.UnknownStack
            },
            states = states,
            enabled = enabled,
            semanticLabel = semanticLabel,
            orientation = orientation,
            key = controlKey,
        )
    }
}

/** Stable group key below a controller-binding wrapper. */
private data class PixelNavigationControlKey(
    /** Caller-owned controller binding identity. */
    val ownerKey: Any?,
    /** Component family preventing bar/rail state reuse. */
    val orientation: PixelNavigationOrientation,
)

/**
 * Preserves eager legacy validation before mounting the context-bound localization resolver.
 */
private fun buildNavigationControl(
    destinations: List<PixelNavigationDestination>,
    selectedId: String,
    onRequestSelection: (String) -> Boolean,
    states: PixelControlStateSet,
    enabled: Boolean,
    semanticLabel: String,
    orientation: PixelNavigationOrientation,
    key: Any?,
): Widget {
    require(semanticLabel.isNotBlank()) { "Navigation semanticLabel must not be blank." }
    /** Eager compatibility validation retained for callers that expect construction-time failure. */
    validateNavigationDestinations(destinations = destinations, selectedId = selectedId)
    return PixelLocalizedNavigationControl(
        destinations = destinations,
        selectedId = selectedId,
        onRequestSelection = onRequestSelection,
        states = states,
        enabled = enabled,
        semanticLabel = semanticLabel,
        orientation = orientation,
        key = key,
    )
}

/** Resolves container and state labels inside the nearest inherited localization context. */
private data class PixelLocalizedNavigationControl(
    /** Current dynamic destinations with mandatory caller-owned labels. */
    val destinations: List<PixelNavigationDestination>,
    /** Caller- or controller-owned selected business id. */
    val selectedId: String,
    /** Shared stable-id selection request used by every input channel. */
    val onRequestSelection: (String) -> Boolean,
    /** Persistent group capability states. */
    val states: PixelControlStateSet,
    /** Whole-group capability gate. */
    val enabled: Boolean,
    /** Explicit label or historical default-string sentinel from the public overload. */
    val semanticLabel: String,
    /** Bar or rail family selecting both layout and localized container label. */
    val orientation: PixelNavigationOrientation,
    /** Stable focus, semantics, and retained group identity. */
    override val key: Any?,
) : StatelessWidget(key = key) {
    /** Resolves all user-visible optional text before rebuilding the unchanged control tree. */
    override fun build(context: BuildContext): Widget {
        /** Explicitly installed bundle; null preserves the opt-in localization boundary. */
        val localizationBundle = PixelLocalizations.maybeOf(context)
        /** Theme remains the fallback source for Loading and Error state labels. */
        val theme = PixelTheme.tokensOf(context)
        /** Historical English collection name and old-overload omission sentinel. */
        val englishContainerLabel = when (orientation) {
            PixelNavigationOrientation.Bar -> DEFAULT_NAVIGATION_BAR_LABEL
            PixelNavigationOrientation.Rail -> DEFAULT_NAVIGATION_RAIL_LABEL
        }
        /** Provider collection name selected independently for Bar and Rail. */
        val providerContainerLabel = when (orientation) {
            PixelNavigationOrientation.Bar -> localizationBundle?.navigationBar
            PixelNavigationOrientation.Rail -> localizationBundle?.navigationRail
        }
        /** A non-sentinel caller label is the only distinguishable explicit old-overload value. */
        val explicitContainerLabel = semanticLabel.takeUnless { label ->
            label == englishContainerLabel
        }
        /** Fixed explicit → provider → English precedence for the collection boundary. */
        val resolvedContainerLabel = PixelLocalizationResolver.resolveText(
            explicitText = explicitContainerLabel,
            providerText = providerContainerLabel,
            themeText = null,
            englishFallback = englishContainerLabel,
        )
        /** Provider state name wins before the current explicit theme label. */
        val resolvedLoadingLabel = PixelLocalizationResolver.resolveText(
            explicitText = null,
            providerText = localizationBundle?.labels?.loading,
            themeText = theme.labels.loading,
            englishFallback = PixelLabelTokens.Default.loading,
        )
        /** Provider error name wins before the current explicit theme label. */
        val resolvedErrorLabel = PixelLocalizationResolver.resolveText(
            explicitText = null,
            providerText = localizationBundle?.labels?.error,
            themeText = theme.labels.error,
            englishFallback = PixelLabelTokens.Default.error,
        )
        return buildResolvedNavigationControl(
            destinations = destinations,
            selectedId = selectedId,
            onRequestSelection = onRequestSelection,
            states = states,
            enabled = enabled,
            semanticLabel = resolvedContainerLabel,
            loadingLabel = resolvedLoadingLabel,
            errorLabel = resolvedErrorLabel,
            orientation = orientation,
            key = key,
        )
    }
}

/** Builds the validated single-focus navigation tree after context-bound text resolution. */
private fun buildResolvedNavigationControl(
    destinations: List<PixelNavigationDestination>,
    selectedId: String,
    onRequestSelection: (String) -> Boolean,
    states: PixelControlStateSet,
    enabled: Boolean,
    semanticLabel: String,
    loadingLabel: String,
    errorLabel: String,
    orientation: PixelNavigationOrientation,
    key: Any?,
): Widget {
    require(semanticLabel.isNotBlank()) { "Navigation semanticLabel must not be blank." }
    /** Selected visual position derived anew from stable identity after every dynamic reorder. */
    val selectedIndex = validateNavigationDestinations(
        destinations = destinations,
        selectedId = selectedId,
    )
    /** Whole-group persistent states normalized with the caller capability gate. */
    var groupStates = states
    if (!enabled) groupStates += PixelControlState.Disabled
    /** Enabled business positions used only for directional lookup, never retained identity. */
    val enabledIndices = destinations.indices.filter { index -> destinations[index].enabled }
    /** Disabled and all-disabled groups expose no traversal stop. */
    val focusable = PixelControlState.Disabled !in groupStates && enabledIndices.isNotEmpty()
    /** Loading retains an existing focus owner while suppressing every mutation route. */
    val interactive = focusable && PixelControlState.Loading !in groupStates
    /** Enabled item highlighted when the selected destination is temporarily disabled. */
    val focusAnchorIndex = selectedIndex
        .takeIf { index -> destinations[index].enabled }
        ?: enabledIndices.firstOrNull()
        ?: -1
    /** Axis-specific movement and activation handler installed on the group's sole focus node. */
    val keyHandler = navigationKeyHandler(
        destinations = destinations,
        selectedIndex = selectedIndex,
        orientation = orientation,
        onRequestSelection = onRequestSelection,
    ).takeIf { interactive }
    return AutomaticFocusAction(
        enabled = focusable,
        debugLabel = semanticLabel,
        onKeyEvent = keyHandler,
        key = key,
    ) { context, _ ->
        /** Active token graph supplying geometry, typography, and semantic labels. */
        val theme = PixelTheme.tokensOf(context)
        /** Stable-id keyed items retaining pointer state through insertion and reordering. */
        val itemWidgets = destinations.mapIndexed { index, destination ->
            /** Caller-controlled logical selection for this exact business destination. */
            val selected = destination.id == selectedId
            /** Persistent item state with selection and per-destination capability normalized. */
            var itemStates = groupStates - PixelControlState.Selected
            if (selected) itemStates += PixelControlState.Selected
            if (!destination.enabled) itemStates += PixelControlState.Disabled
            /** Shared pointer and semantics request, absent for Disabled and Loading items. */
            val select: (() -> Boolean)? = if (interactive && destination.enabled) {
                { onRequestSelection(destination.id) }
            } else {
                null
            }
            /** Position metadata follows current layout while key/action identity remains id-based. */
            val itemInfo = if (orientation == PixelNavigationOrientation.Bar) {
                PixelSemanticsCollectionItemInfo(
                    rowIndex = 0,
                    columnIndex = index,
                    selected = selected,
                )
            } else {
                PixelSemanticsCollectionItemInfo(
                    rowIndex = index,
                    columnIndex = 0,
                    selected = selected,
                )
            }
            /** Composite key that never contains the destination's mutable visual index. */
            val itemKey = PixelNavigationDestinationKey(ownerKey = key, destinationId = destination.id)
            PixelNavigationDestinationItem(
                destination = destination,
                selected = selected,
                states = itemStates,
                select = select,
                focusWhenParentFocused = index == focusAnchorIndex,
                collectionItemInfo = itemInfo,
                orientation = orientation,
                loadingLabel = loadingLabel,
                errorLabel = errorLabel,
                key = itemKey,
            )
        }
        /** Axis-specific visible layout preserving source-list order only as presentation. */
        val navigationLayout = if (orientation == PixelNavigationOrientation.Bar) {
            Row(
                children = destinations.zip(itemWidgets).map { (destination, item) ->
                    /** Keyed flex slot moves with its business item instead of retaining an index. */
                    Expanded(
                        child = item,
                        key = PixelNavigationBarSlotKey(
                            ownerKey = key,
                            destinationId = destination.id,
                        ),
                    )
                },
                spacing = 0,
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                key = key?.let { owner -> "$owner-navigation-bar-items" },
            )
        } else {
            Column(
                children = itemWidgets,
                spacing = 0,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                key = key?.let { owner -> "$owner-navigation-rail-items" },
            )
        }
        /** Structured single-selection dimensions matching the current bar or rail axis. */
        val collectionInfo = if (orientation == PixelNavigationOrientation.Bar) {
            PixelSemanticsCollectionInfo(
                rowCount = 1,
                columnCount = destinations.size,
                selectionMode = PixelSemanticsSelectionMode.SINGLE,
            )
        } else {
            PixelSemanticsCollectionInfo(
                rowCount = destinations.size,
                columnCount = 1,
                selectionMode = PixelSemanticsSelectionMode.SINGLE,
            )
        }
        /** Public collection boundary preserving the caller key for retained-tree lookup. */
        Semantics(
            label = semanticLabel,
            role = PixelSemanticRole.GENERIC,
            enabled = interactive,
            value = loadingLabel.takeIf { PixelControlState.Loading in groupStates },
            error = errorLabel.takeIf { PixelControlState.Error in groupStates },
            collectionInfo = collectionInfo,
            mergeDescendants = false,
            child = navigationLayout,
            key = key,
        )
    }
}

/** Stable retained identity composed from a group and one business destination id. */
private data class PixelNavigationDestinationKey(
    /** Optional caller-owned group identity. */
    val ownerKey: Any?,
    /** Stable destination identity independent from current visual position. */
    val destinationId: String,
)

/** Stable flex-slot identity allowing Bar destinations to move without remounting their subtree. */
private data class PixelNavigationBarSlotKey(
    /** Optional caller-owned group identity. */
    val ownerKey: Any?,
    /** Stable business destination id independent from the current column position. */
    val destinationId: String,
)

/** Retained destination configuration whose pointer feedback and tint cache are state-owned. */
private data class PixelNavigationDestinationItem(
    /** Immutable destination definition for this business id. */
    val destination: PixelNavigationDestination,
    /** Caller-controlled selection state. */
    val selected: Boolean,
    /** Persistent normalized component states. */
    val states: PixelControlStateSet,
    /** Capability-guarded shared selection request. */
    val select: (() -> Boolean)?,
    /** Whether group focus should be represented by this semantic item. */
    val focusWhenParentFocused: Boolean,
    /** Current row/column position in the navigation collection. */
    val collectionItemInfo: PixelSemanticsCollectionItemInfo,
    /** Bar or rail item-content layout. */
    val orientation: PixelNavigationOrientation,
    /** Provider- or theme-resolved Loading status text. */
    val loadingLabel: String,
    /** Provider- or theme-resolved Error status text. */
    val errorLabel: String,
    /** Stable business-id based retained identity. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates pointer feedback and icon-tint cache state for this destination. */
    override fun createState(): State<out StatefulWidget> = PixelNavigationDestinationItemState()
}

/** Runtime pointer feedback and tinted-icon cache for one navigation destination. */
private class PixelNavigationDestinationItemState : State<PixelNavigationDestinationItem>() {
    /** Whether this destination currently owns a captured pointer press. */
    private var pressed: Boolean = false

    /** Whether a mouse or stylus currently hovers over this destination. */
    private var hovered: Boolean = false

    /** Source bitmap paired with [cachedTintedBitmap]. */
    private var cachedSourceBitmap: PixelBitmap? = null

    /** Theme content color paired with [cachedTintedBitmap]. */
    private var cachedTintColor: PixelColor? = null

    /** Reused alpha-mask tint while source identity and content color remain unchanged. */
    private var cachedTintedBitmap: PixelBitmap? = null

    /** Resolves current theme, state, layout, pointer behavior, and structured semantics. */
    override fun build(context: BuildContext): Widget {
        /** Complete inherited design-token graph. */
        val theme = PixelTheme.tokensOf(context)
        /** Component family selected solely from the public bar/rail entry point. */
        val tokens = when (widget.orientation) {
            PixelNavigationOrientation.Bar -> theme.components.navigationBar
            PixelNavigationOrientation.Rail -> theme.components.navigationRail
        }
        /** Single group-owned node inherited by all non-focusable destination items. */
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) context.watch(focusNode)
        /** Whether this exact item represents the group's actual focus. */
        val focused = focusNode?.isFocused == true && widget.focusWhenParentFocused
        /** Disabled/Loading transitions release stale pointer micro-state immediately. */
        if (
            PixelControlState.Disabled in widget.states ||
            PixelControlState.Loading in widget.states ||
            widget.select == null
        ) {
            pressed = false
            hovered = false
        }
        /** Runtime states retaining controlled selection, validation, and real focus together. */
        val resolvedStates = mergeControlStates(
            persistent = widget.states,
            disabled = PixelControlState.Disabled in widget.states,
            pressed = pressed,
            hovered = hovered,
            focused = focused,
        )
        /** State-resolved foreground shared by the icon tint and visible label. */
        val contentColor = tokens.resolveContentColor(resolvedStates, theme.colors)
            ?: theme.colors.onSurface
        /** Selected icon choice that keeps the normal icon as an explicit fallback. */
        val sourceIcon = if (widget.selected) {
            widget.destination.selectedIcon ?: widget.destination.icon
        } else {
            widget.destination.icon
        }
        /** Cached themed alpha-mask used as a non-semantic visual descendant. */
        val tintedIcon = PixelIconData(resolveTintedBitmap(sourceIcon.bitmap, contentColor))
        /** Visible icon and label ordered for the selected navigation family. */
        val itemContent = if (widget.orientation == PixelNavigationOrientation.Bar) {
            Column(
                children = listOf(
                    Icon(tintedIcon),
                    Text(
                        data = widget.destination.label,
                        style = theme.typography.label.resolve(theme.colors).copy(color = contentColor),
                        overflow = PixelTextOverflow.ELLIPSIS,
                        softWrap = false,
                        maxLines = 1,
                        textAlign = TextAlign.CENTER,
                    ),
                ),
                spacing = theme.spacing.extraSmall,
                mainAxisAlignment = MainAxisAlignment.CENTER,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
            )
        } else {
            Row(
                children = listOf(
                    Icon(tintedIcon),
                    Text(
                        data = widget.destination.label,
                        style = theme.typography.label.resolve(theme.colors).copy(color = contentColor),
                        overflow = PixelTextOverflow.ELLIPSIS,
                        softWrap = false,
                        maxLines = 1,
                    ),
                ),
                spacing = theme.spacing.extraSmall,
                mainAxisAlignment = MainAxisAlignment.START,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
            )
        }
        /** Token-resolved visual surface before pointer and focus adapters. */
        val surface = ConstrainedBox(
            constraints = PixelBoxConstraints(
                minWidth = tokens.resolveMinimumWidth(theme.sizes),
                minHeight = tokens.resolveMinimumHeight(theme.sizes),
            ),
            child = PixelSurface(
                padding = tokens.resolvePadding(theme.spacing),
                decoration = PixelSurfaceDecoration(
                    fillColor = tokens.resolveContainerColor(resolvedStates, theme.colors),
                    borderColor = tokens.resolveBorderColor(resolvedStates, theme.colors),
                    borderWidth = tokens.resolveBorderWidth(theme.borders),
                    cornerRadius = tokens.resolveCornerRadius(theme.radii),
                    shadowColor = theme.colors.shadow,
                    shadowOffset = tokens.resolveElevation(theme.elevations),
                ),
                child = itemContent,
                key = widget.key?.let { itemKey -> "$itemKey-surface" },
            ),
            key = widget.key?.let { itemKey -> "$itemKey-constraints" },
        )
        /** Pointer target spans the complete token-sized destination only while interactive. */
        val interactiveSurface = widget.select?.let { select ->
            InteractionDetector(
                child = surface,
                onTap = { select.invoke() },
                onPressedChanged = ::updatePressed,
                onHoveredChanged = ::updateHovered,
                key = widget.key,
            )
        } ?: surface
        return FocusableControl(
            label = widget.destination.label,
            role = PixelSemanticRole.TAB,
            child = interactiveSurface,
            enabled = PixelControlState.Disabled !in resolvedStates,
            semanticsEnabled = widget.select != null,
            automaticallyFocusable = false,
            focusWhenParentFocused = widget.focusWhenParentFocused,
            selected = widget.selected,
            value = widget.loadingLabel.takeIf { PixelControlState.Loading in resolvedStates },
            error = widget.errorLabel.takeIf { PixelControlState.Error in resolvedStates },
            collectionItemInfo = widget.collectionItemInfo,
            focusIndicator = tokens.focusIndicator ?: PixelFocusIndicatorTokens.Default,
            actions = PixelSemanticsActions(onClick = widget.select),
            key = widget.key,
        )
    }

    /** Returns a cached alpha-mask tint for the current icon source and token foreground. */
    private fun resolveTintedBitmap(source: PixelBitmap, tint: PixelColor): PixelBitmap {
        /** Cached value valid only for the identical immutable source object and color value. */
        val cached = cachedTintedBitmap
        if (cachedSourceBitmap === source && cachedTintColor == tint && cached != null) return cached
        /** SDK 内部只读源像素；不得修改或向消费者暴露。 */
        val sourcePixels = PixelCoreArtifactAccess.pixelsUnsafe(source)
        /** Fresh destination pixels retaining source alpha and replacing RGB with theme tint. */
        val pixels = IntArray(sourcePixels.size)
        sourcePixels.forEachIndexed { index, sourceArgb ->
            /** Source mask opacity from the standard ARGB high byte. */
            val sourceAlpha = (sourceArgb ushr 24) and 0xFF
            /** Product alpha combining translucent source masks and theme colors. */
            val resolvedAlpha = sourceAlpha * tint.alpha / 0xFF
            pixels[index] = if (resolvedAlpha == 0) {
                PixelColor.Transparent.argb
            } else {
                PixelColor.fromArgb(resolvedAlpha, tint.red, tint.green, tint.blue).argb
            }
        }
        /** Immutable tinted bitmap stored for future identical builds. */
        val tinted = PixelBitmap(width = source.width, height = source.height, pixels = pixels)
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

    /** Updates hover membership exactly once per pointer-boundary transition. */
    private fun updateHovered(nextHovered: Boolean) {
        if (hovered == nextHovered) return
        setState { hovered = nextHovered }
    }
}

/** Rejects empty, duplicate, or uncontrolled dynamic destination lists. */
private fun validateNavigationDestinations(
    destinations: List<PixelNavigationDestination>,
    selectedId: String,
): Int {
    require(destinations.isNotEmpty()) { "Navigation destinations must not be empty." }
    require(selectedId.isNotBlank()) { "Navigation selectedId must not be blank." }
    /** Business ids used by actions, retained keys, and optional controller stacks. */
    val ids = destinations.map(PixelNavigationDestination::id)
    require(ids.distinct().size == ids.size) { "Navigation destination ids must be unique." }
    /** Spoken labels kept unique so a semantic item remains unambiguous to tests and services. */
    val labels = destinations.map(PixelNavigationDestination::label)
    require(labels.distinct().size == labels.size) { "Navigation destination labels must be unique." }
    /** Exact current position used only for layout metadata and directional traversal. */
    val selectedIndex = destinations.indexOfFirst { destination -> destination.id == selectedId }
    require(selectedIndex >= 0) {
        "Navigation selectedId must match exactly one destination id."
    }
    return selectedIndex
}

/** Validates controller ids when a concrete multi-stack host is already mounted. */
private fun validateControllerDestinations(
    destinations: List<PixelNavigationDestination>,
    controller: PixelMultiStackNavigatorController,
) {
    if (!controller.isAttached) return
    /** Destination ids expected to resolve through the currently mounted controller host. */
    val destinationIds = destinations.map(PixelNavigationDestination::id).toSet()
    /** Unknown visible destinations would otherwise report successful-looking inert actions. */
    require(controller.stackIds.containsAll(destinationIds)) {
        "Controller-bound navigation destinations must reference mounted stack ids."
    }
}

/** Creates axis-specific cyclic movement plus current-destination Enter/Space activation. */
private fun navigationKeyHandler(
    destinations: List<PixelNavigationDestination>,
    selectedIndex: Int,
    orientation: PixelNavigationOrientation,
    onRequestSelection: (String) -> Boolean,
): (PixelKeyEvent) -> Boolean = { event ->
    when (event.key) {
        PixelKey.ARROW_LEFT -> if (orientation == PixelNavigationOrientation.Bar) {
            requestAdjacentNavigationDestination(
                destinations = destinations,
                selectedIndex = selectedIndex,
                delta = -1,
                onRequestSelection = onRequestSelection,
            )
        } else {
            false
        }
        PixelKey.ARROW_RIGHT -> if (orientation == PixelNavigationOrientation.Bar) {
            requestAdjacentNavigationDestination(
                destinations = destinations,
                selectedIndex = selectedIndex,
                delta = 1,
                onRequestSelection = onRequestSelection,
            )
        } else {
            false
        }
        PixelKey.ARROW_UP -> if (orientation == PixelNavigationOrientation.Rail) {
            requestAdjacentNavigationDestination(
                destinations = destinations,
                selectedIndex = selectedIndex,
                delta = -1,
                onRequestSelection = onRequestSelection,
            )
        } else {
            false
        }
        PixelKey.ARROW_DOWN -> if (orientation == PixelNavigationOrientation.Rail) {
            requestAdjacentNavigationDestination(
                destinations = destinations,
                selectedIndex = selectedIndex,
                delta = 1,
                onRequestSelection = onRequestSelection,
            )
        } else {
            false
        }
        PixelKey.ENTER,
        PixelKey.SPACE,
        -> destinations.getOrNull(selectedIndex)
            ?.takeIf(PixelNavigationDestination::enabled)
            ?.let { destination -> onRequestSelection(destination.id) }
            ?: false
        else -> false
    }
}

/** Requests the next enabled stable id in cyclic visual order. */
private fun requestAdjacentNavigationDestination(
    destinations: List<PixelNavigationDestination>,
    selectedIndex: Int,
    delta: Int,
    onRequestSelection: (String) -> Boolean,
): Boolean {
    if (selectedIndex !in destinations.indices) return false
    /** Candidate position advanced until an enabled business destination is found. */
    var candidateIndex = selectedIndex
    repeat(destinations.size) {
        candidateIndex = Math.floorMod(candidateIndex + delta, destinations.size)
        /** Candidate whose id, not position, is delivered to the controlled owner. */
        val candidate = destinations[candidateIndex]
        if (candidate.enabled) return onRequestSelection(candidate.id)
    }
    return false
}

/** Default configurable collection label for the horizontal component. */
private const val DEFAULT_NAVIGATION_BAR_LABEL: String = "Navigation bar"

/** Default configurable collection label for the vertical component. */
private const val DEFAULT_NAVIGATION_RAIL_LABEL: String = "Navigation rail"
