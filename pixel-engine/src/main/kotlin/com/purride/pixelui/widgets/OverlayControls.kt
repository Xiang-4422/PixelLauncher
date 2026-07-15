package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.animation.Curve
import com.purride.pixelui.animation.IntOffset
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelAnimationStatus
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.internal.ElementSubtreeVisibility
import com.purride.pixelui.internal.AutomaticFocusAction
import com.purride.pixelui.internal.AnchoredOverlayFollowerWidget
import com.purride.pixelui.internal.AnchoredOverlayPortalWidget
import com.purride.pixelui.internal.InteractionDetector
import com.purride.pixelui.internal.ModalInteractionScopeWidget
import com.purride.pixelui.internal.PixelAnchoredOverlayLink
import com.purride.pixelui.internal.VisualOnlyWidget
import com.purride.pixelui.internal.activationKeyHandler
import com.purride.pixelui.internal.mergeControlStates
import com.purride.pixelui.internal.withControlFocusIndicator
import kotlin.time.Duration

/**
 * 执行 `OverlayControls` 的 `Popover` 路由操作并保持结果恰好一次。
 *
 * Creates the source- and binary-compatible automatic Popover overload.
 *
 * This keeps the pre-M4-3 JVM descriptor and maps it to [PixelPopoverPlacement.Auto],
 * [PixelPopoverAlignment.Start], and a one-pixel safe viewport margin.
 */
public fun Popover(
    anchor: Widget,
    content: Widget,
    expanded: Boolean,
    contentOffset: IntOffset = IntOffset(0, 10),
    dismissible: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    key: Any? = null,
    modal: Boolean = true,
): Widget = Popover(
    anchor = anchor,
    content = content,
    expanded = expanded,
    contentOffset = contentOffset,
    dismissible = dismissible,
    onDismiss = onDismiss,
    key = key,
    modal = modal,
    placement = PixelPopoverPlacement.Auto,
    alignment = PixelPopoverAlignment.Start,
    viewportMargin = 1,
)

/**
 * 执行 `OverlayControls` 的 `Popover` 路由操作并保持结果恰好一次。
 *
 * Creates one controlled, collision-aware popup presentation.
 *
 * [expanded] is caller-owned. The presentation uses the anchor's actual global paint bounds,
 * measures inside the [MediaQuery] safe/IME viewport, escapes ancestor clips through a root portal,
 * and recomputes paint, hit, and semantic geometry after scrolling or window changes. Popover uses
 * retained opacity motion; logical close removes modal interaction before exit paint completes.
 *
 * @param anchor In-flow widget whose real global bounds drive placement.
 * @param content Popup subtree measured inside the safe Host viewport.
 * @param expanded Whether content is logically open and interactive.
 * @param contentOffset Legacy offset from the anchor's global top-start origin.
 * @param dismissible Whether an open popup installs a full-viewport outside-tap barrier.
 * @param onDismiss Controlled dismissal callback used by barrier, Escape, and Back.
 * @param key Stable retained identity for anchor, follower, focus, and motion state.
 * @param modal Whether the presentation isolates background focus, input, and semantics.
 * @param placement Preferred vertical side and collision-flip policy.
 * @param alignment Horizontal alignment relative to the measured anchor.
 * @param viewportMargin Additional logical pixels retained from safe viewport edges.
 */
public fun Popover(
    anchor: Widget,
    content: Widget,
    expanded: Boolean,
    contentOffset: IntOffset = IntOffset(0, 10),
    dismissible: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    key: Any? = null,
    modal: Boolean = true,
    placement: PixelPopoverPlacement,
    alignment: PixelPopoverAlignment = PixelPopoverAlignment.Start,
    viewportMargin: Int = 1,
): Widget = PopoverWidget(
    anchor = anchor,
    content = content,
    expanded = expanded,
    contentOffset = contentOffset,
    dismissible = dismissible,
    onDismiss = onDismiss,
    modal = modal,
    placement = placement,
    alignment = alignment,
    viewportMargin = viewportMargin,
    key = key,
)

/** Retained implementation behind the source-compatible [Popover] function. */
private class PopoverWidget(
    /** Anchor that remains the first stable Stack child across enter and exit. */
    val anchor: Widget,
    /** Latest controlled popover content. */
    val content: Widget,
    /** Whether the controlled content is logically open and interactive. */
    val expanded: Boolean,
    /** Stable anchor-relative placement for the content. */
    val contentOffset: IntOffset,
    /** Whether an expanded popover installs a transparent dismiss barrier. */
    val dismissible: Boolean,
    /** Callback invoked only by the live expanded barrier. */
    val onDismiss: (() -> Unit)?,
    /** Whether an expanded presentation isolates background interaction and semantic traversal. */
    val modal: Boolean,
    /** Preferred vertical placement and automatic flip policy. */
    val placement: PixelPopoverPlacement,
    /** Horizontal alignment resolved from the measured anchor. */
    val alignment: PixelPopoverAlignment,
    /** Additional safe viewport margin in logical pixels. */
    val viewportMargin: Int,
    key: Any?,
) : StatefulWidget(key = key) {
    /** Creates the retained enter/exit state machine. */
    override fun createState(): State<out StatefulWidget> = PopoverState()
}

/** Owns one interruptible content-opacity transition without owning a private frame clock. */
private class PopoverState : State<PopoverWidget>() {
    /** Whether the first build has reconciled the controlled expanded value. */
    private var initialized: Boolean = false

    /** Expanded target already applied to this state machine. */
    private var appliedExpanded: Boolean = false

    /** Content retained while entering, visible, or painting its non-interactive exit. */
    private var retainedContent: Widget? = null

    /** Placement retained with outgoing content so exit never jumps to a new anchor offset. */
    private var retainedOffset: IntOffset = IntOffset(0, 10)

    /** Vertical policy retained while outgoing content completes its visual exit. */
    private var retainedPlacement: PixelPopoverPlacement = PixelPopoverPlacement.Auto

    /** Horizontal alignment retained while outgoing content completes its visual exit. */
    private var retainedAlignment: PixelPopoverAlignment = PixelPopoverAlignment.Start

    /** Edge margin retained while outgoing content completes its visual exit. */
    private var retainedViewportMargin: Int = 1

    /** Render-only geometry channel shared by this Popover's portal and follower. */
    private val anchorLink: PixelAnchoredOverlayLink = PixelAnchoredOverlayLink()

    /** Resting opacity used when no transition controller exists. */
    private var opacity: Float = 0f

    /** Host-backed controller currently moving toward the controlled expanded state. */
    private var transitionController: PixelAnimationController? = null

    /** Theme curve with its resolved delay encoded into normalized progress. */
    private var transitionCurve: Curve? = null

    /** Visual opacity captured at the beginning of the current interruptible segment. */
    private var transitionStartOpacity: Float = 0f

    /** Exact opacity endpoint of the current transition segment. */
    private var transitionTargetOpacity: Float = 0f

    /** Host ticker provider used by the currently resolved transition environment. */
    private var configuredVsync: PixelTickerProvider? = null

    /** Enter or exit token used by the currently resolved transition environment. */
    private var configuredSpec: PixelMotionSpec? = null

    /** System motion preferences used by the currently resolved transition environment. */
    private var configuredSettings: PixelMotionSettings? = null

    /** Per-instance key retaining the barrier independently from caller-provided keys. */
    private val barrierKey: Any = Any()

    /** Per-instance key retaining the content presentation when its sibling barrier changes. */
    private val contentKey: Any = Any()

    /** Per-instance key retaining the opacity render object across rapid reversals. */
    private val opacityKey: Any = Any()

    /** Per-instance key retaining the paint-only interaction boundary across exit. */
    private val interactionKey: Any = Any()

    /** Per-instance key for the no-op target covering the measured popup surface only. */
    private val surfaceAbsorberKey: Any = Any()

    /** Per-instance key retaining modal target ownership across enter and exit. */
    private val modalBoundaryKey: Any = Any()

    /** Per-instance key retaining modal focus ownership across enter and exit. */
    private val modalFocusKey: Any = Any()

    /** Per-instance key retaining the shared barrier-and-content presentation Stack. */
    private val presentationStackKey: Any = Any()

    /** Re-resolves a changed inherited theme or system motion preference from the current frame. */
    override fun didChangeDependencies() {
        if (initialized) configureForCurrentEnvironment(context = context, forceRestart = false)
    }

    /** Builds a stable anchor and optional retained content presentation. */
    override fun build(context: BuildContext): Widget {
        reconcileControlledState(context)
        transitionController?.let(context::watch)
        /** Host viewport and insets that trigger retained re-placement after resize or IME changes. */
        val media = MediaQuery.maybeOf(context)
        /** Logical Host width, or zero so render constraints provide the non-Host fallback. */
        val viewportWidth = media?.logicalWidth ?: 0
        /** Logical Host height, or zero so render constraints provide the non-Host fallback. */
        val viewportHeight = media?.logicalHeight ?: 0
        /** Combined stable safe area and transient IME exclusion used by the follower. */
        val safeInsets = media?.overlaySafeInsets() ?: PixelWindowInsets.Zero
        /** Ambient logical direction used to resolve Start and End popup alignment. */
        val textDirection = Directionality.of(context)
        /** Route-level modal owner, when this Popover is content of a unified popup route. */
        val routeModalPresence = context.getInheritedWidgetOfExactType<PixelModalFocusPresence>()
        /** Whether this Popover must create its own focus and render interaction owner. */
        val ownsModal = widget.modal && routeModalPresence?.coalesceNestedModal != true
        /** Alpha sampled from the current retained transition for this build. */
        val visualOpacity = currentOpacity()
        /** Anchored visual subtree retained throughout enter, visible, and exit phases. */
        val contentPresentation = retainedContent?.let { child ->
            AnchoredOverlayFollowerWidget(
                link = anchorLink,
                placement = retainedPlacement,
                alignment = retainedAlignment,
                textDirection = textDirection,
                contentOffset = retainedOffset,
                safeInsets = safeInsets,
                viewportMargin = retainedViewportMargin,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                child = Opacity(
                    opacity = visualOpacity,
                    child = PopoverInteractionBoundary(
                        interactive = widget.expanded,
                        child = PixelOverlaySurface(
                            child = child,
                            key = child.key?.let { "$it-overlay-surface" } ?: surfaceAbsorberKey,
                        ),
                        key = interactionKey,
                    ),
                    key = opacityKey,
                ),
                key = contentKey,
            )
        }
        /** Transparent dismiss target installed only while the controlled popover is open. */
        val barrier = if (widget.expanded && widget.dismissible && widget.onDismiss != null) {
            ModalBarrier(
                color = PixelColor.Transparent,
                dismissible = true,
                onDismiss = widget.onDismiss,
                key = barrierKey,
            )
        } else {
            null
        }
        /** Render-level boundary that isolates modal interaction while preserving exit paint. */
        val modalInteraction = if (barrier != null || contentPresentation != null) {
            ModalInteractionScopeWidget(
                active = widget.expanded && ownsModal,
                child = Stack(
                    children = listOfNotNull(barrier, contentPresentation),
                    key = presentationStackKey,
                ),
                key = modalBoundaryKey,
            )
        } else {
            null
        }
        /** Optional focus boundary paired with the render-level modal interaction owner. */
        val presentation = modalInteraction?.let { interactionBoundary ->
            if (ownsModal) {
                StandaloneModalBoundaryFactory.create(
                    active = widget.expanded,
                    onDismissRequest = widget.onDismiss,
                    coalesceNestedMenu = true,
                    child = interactionBoundary,
                    key = modalFocusKey,
                )
            } else {
                interactionBoundary
            }
        }
        return AnchoredOverlayPortalWidget(
            anchor = widget.anchor,
            presentation = presentation,
            link = anchorLink,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            key = widget.key,
        )
    }

    /** Reconciles controlled visibility and content updates before creating this frame. */
    private fun reconcileControlledState(context: BuildContext) {
        if (!initialized) {
            initialized = true
            appliedExpanded = widget.expanded
            opacity = 0f
            if (widget.expanded) {
                retainedContent = widget.content
                retainedOffset = widget.contentOffset
                retainedPlacement = widget.placement
                retainedAlignment = widget.alignment
                retainedViewportMargin = widget.viewportMargin.coerceAtLeast(0)
            }
            configureForCurrentEnvironment(context = context, forceRestart = true)
            return
        }
        if (appliedExpanded != widget.expanded) {
            if (widget.expanded) {
                retainedContent = widget.content
                retainedOffset = widget.contentOffset
                retainedPlacement = widget.placement
                retainedAlignment = widget.alignment
                retainedViewportMargin = widget.viewportMargin.coerceAtLeast(0)
            }
            appliedExpanded = widget.expanded
            configureForCurrentEnvironment(context = context, forceRestart = true)
            return
        }
        if (widget.expanded) {
            retainedContent = widget.content
            retainedOffset = widget.contentOffset
            retainedPlacement = widget.placement
            retainedAlignment = widget.alignment
            retainedViewportMargin = widget.viewportMargin.coerceAtLeast(0)
        }
        configureForCurrentEnvironment(context = context, forceRestart = false)
    }

    /** Resolves the active token and retargets only when its inherited environment changed. */
    private fun configureForCurrentEnvironment(context: BuildContext, forceRestart: Boolean) {
        /** Nearest host-backed motion scope, absent only outside a configured Host. */
        val scope = PixelMotionScope.maybeOf(context)
        /** Theme token selected from the current logical enter or exit direction. */
        val spec = if (appliedExpanded) {
            PixelMotionTheme.of(context).popoverEnter
        } else {
            PixelMotionTheme.of(context).popoverExit
        }
        /** Motion accessibility settings applied when resolving the selected token. */
        val settings = scope?.settings ?: PixelMotionSettings.Default
        /** Whether inherited clock, token, or accessibility settings require retargeting. */
        val environmentChanged = configuredVsync !== scope?.vsync ||
            configuredSpec != spec ||
            configuredSettings != settings
        if (!forceRestart && !environmentChanged) return

        /** Visual endpoint sampled before the previous controller is disposed. */
        val fromOpacity = currentOpacity()
        disposeTransitionController()
        transitionStartOpacity = fromOpacity
        transitionTargetOpacity = if (appliedExpanded) 1f else 0f
        opacity = fromOpacity
        configuredVsync = scope?.vsync
        configuredSpec = spec
        configuredSettings = settings
        /** Concrete transition after animator scale and reduced-motion policy are applied. */
        val resolved = spec.resolve(settings)
        /** Complete controller duration including the resolved pre-animation delay. */
        val totalDuration = resolved.delay + resolved.duration
        if (
            scope == null ||
            resolved.isImmediate ||
            resolved.transition == PixelMotionTransitionPreset.None ||
            totalDuration == Duration.ZERO ||
            fromOpacity == transitionTargetOpacity
        ) {
            completeTransition(expanded = appliedExpanded)
            return
        }
        /** Controller driven by the inherited Host ticker for this transition segment. */
        val controller = PixelAnimationController(duration = totalDuration, vsync = scope.vsync)
        transitionController = controller
        transitionCurve = popoverDelayedCurve(
            delay = resolved.delay,
            duration = resolved.duration,
            curve = resolved.curve,
        )
        controller.addListener {
            if (
                transitionController === controller &&
                controller.status == PixelAnimationStatus.Completed
            ) {
                completeTransition(expanded = appliedExpanded)
            }
        }
        controller.forward(from = 0f)
    }

    /** Commits a terminal alpha and removes outgoing content only after its exit paint completes. */
    private fun completeTransition(expanded: Boolean) {
        opacity = if (expanded) 1f else 0f
        disposeTransitionController()
        if (!expanded) retainedContent = null
        if (mounted) setState { Unit }
    }

    /** Samples the current visual alpha so rapid reversals never restart from a stale endpoint. */
    private fun currentOpacity(): Float {
        /** Active transition segment, or null while resting at [opacity]. */
        val controller = transitionController ?: return opacity
        /** Delayed and curved normalized progress of the active transition segment. */
        val progress = (transitionCurve ?: return opacity)
            .transform(controller.value.coerceIn(0f, 1f))
            .coerceIn(0f, 1f)
        return (
            transitionStartOpacity +
                (transitionTargetOpacity - transitionStartOpacity) * progress
            ).coerceIn(0f, 1f)
    }

    /** Disposes only the component-owned controller and ticker. */
    private fun disposeTransitionController() {
        transitionController?.dispose()
        transitionController = null
        transitionCurve = null
    }

    /** Releases any live transition when the Popover leaves the retained tree. */
    override fun dispose() {
        disposeTransitionController()
    }
}

/** Encodes a resolved Popover delay before applying its active interpolation curve. */
private fun popoverDelayedCurve(
    delay: Duration,
    duration: Duration,
    curve: Curve,
): Curve {
    /** Combined delay and interpolation time represented by normalized controller progress. */
    val total = delay + duration
    if (delay == Duration.ZERO) return curve
    if (duration == Duration.ZERO) return Curve { progress -> if (progress >= 1f) 1f else 0f }
    /** Normalized portion of `total` during which opacity remains at its start value. */
    val delayFraction = when {
        delay.isInfinite() -> 1f
        total.isInfinite() && duration.isInfinite() -> 0f
        total.isInfinite() -> 1f
        else -> (delay.inWholeNanoseconds.toDouble() / total.inWholeNanoseconds.toDouble()).toFloat()
    }.coerceIn(0f, 1f)
    return Curve { progress ->
        when {
            progress <= delayFraction -> 0f
            delayFraction >= 1f -> 0f
            else -> curve.transform(
                ((progress - delayFraction) / (1f - delayFraction)).coerceIn(0f, 1f),
            )
        }
    }
}

/** Stable retained boundary that makes outgoing Popover content paint-only immediately. */
private class PopoverInteractionBoundary(
    /** Whether child interaction, targets, semantics, and finder collection remain enabled. */
    private val interactive: Boolean,
    /** Retained content subtree shared by enter and exit phases. */
    private val child: Widget,
    /** Stable identity that prevents content state replacement across phase changes. */
    override val key: Any? = null,
) : StatelessWidget(key = key), ElementSubtreeVisibility {
    /** Hides logically dismissed content from widget finders while retaining its Element. */
    override val exposesSubtreeToWidgetCollection: Boolean
        get() = interactive

    /** Uses the shared retained render gate so only paint survives logical dismissal. */
    override fun build(context: BuildContext): Widget {
        return VisualOnlyWidget(
            child = child,
            visualOnly = !interactive,
            key = key?.let { "$it-visual-only" },
        )
    }
}

/**
 * 像素菜单的一行。
 *
 * [onSelected] 只处理该行动作；关闭菜单、路由跳转或状态更新由调用方决定。
 */
public data class PixelMenuItem(
    /** Spoken and painted primary text for this menu row. */
    val label: String,
    /** Selection callback invoked when the enabled row is activated. */
    val onSelected: () -> Unit,
    /** Whether this row exports and accepts its activation action. */
    val enabled: Boolean = true,
    /** Optional shortcut hint painted at the trailing edge of the row. */
    val shortcut: String? = null,
    /** Structured selection state announced independently from [label]. */
    val selected: Boolean = false,
    /** Stable item identity required when a dynamic menu is reordered. */
    val key: Any? = null,
)

/** Legacy Menu fill used only to recognize an omitted theme override. */
private val LEGACY_MENU_FILL_COLOR: PixelColor = PixelColor.Black

/** Legacy Menu outline used only to recognize an omitted theme override. */
private val LEGACY_MENU_BORDER_COLOR: PixelColor = PixelColor.White

/** Legacy Menu collection label retained when no theme provider is mounted. */
private const val LEGACY_MENU_SEMANTIC_LABEL: String = "Menu"

/** Legacy shortcut foreground retained when no theme provider is mounted. */
private val LEGACY_MENU_SHORTCUT_COLOR: PixelColor = PixelColor.fromRgb(160, 160, 160)

/** Selects the component-token family used by a standalone Menu or Dropdown popup. */
private enum class PixelMenuTokenFamily {
    /** Standalone Menu surface and row tokens. */
    Menu,

    /** Dropdown popup surface and row tokens shared with its anchor. */
    Dropdown,
}

/**
 * 纵向像素菜单。
 *
 * 组件不持有选中状态，也不自动关闭弹出层；每行点击时只调用对应 [PixelMenuItem.onSelected]。
 * [semanticLabel] 命名 collection 容器；[onDismissRequest] 同时导出 Android dismiss action。
 * [modal] 默认隔离菜单外部的交互和 semantics；嵌入 [Popover] 时由外层统一管理。
 */
public fun Menu(
    items: List<PixelMenuItem>,
    enabled: Boolean = true,
    fillColor: PixelColor = PixelColor.Black,
    borderColor: PixelColor = PixelColor.White,
    key: Any? = null,
    semanticLabel: String = "Menu",
    onDismissRequest: (() -> Unit)? = null,
    modal: Boolean = true,
): Widget {
    return buildMenu(
        items = items,
        states = PixelControlStateSet.Normal,
        enabled = enabled,
        fillColor = fillColor.takeUnless { color -> color == LEGACY_MENU_FILL_COLOR },
        borderColor = borderColor.takeUnless { color -> color == LEGACY_MENU_BORDER_COLOR },
        key = key,
        semanticLabel = semanticLabel.takeUnless { label -> label == LEGACY_MENU_SEMANTIC_LABEL },
        onDismissRequest = onDismissRequest,
        modal = modal,
        useLegacyFallbacks = true,
        tokenFamily = PixelMenuTokenFamily.Menu,
    )
}

/**
 * 执行 `OverlayControls` 的 `Menu` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware Menu whose surface and rows resolve from the active component tokens.
 *
 * [PixelControlState.Loading] leaves rows focusable while removing activation. Disabled removes
 * rows from focus traversal. Focus remains an additive outline over selected, error, and pressed
 * base colors.
 *
 * @param items Ordered controlled menu rows.
 * @param states Persistent visual and capability states shared by the Menu and every row.
 * @param enabled Whether menu rows may participate in interaction.
 * @param fillColor Optional explicit surface fill above component and foundation tokens.
 * @param borderColor Optional explicit outline above component and foundation tokens.
 * @param key Stable Menu collection identity.
 * @param semanticLabel Optional collection label; null resolves from theme labels.
 * @param onDismissRequest Optional Escape, Back, and semantic dismiss callback.
 * @param modal Whether this Menu owns modal focus and interaction isolation.
 */
@kotlin.jvm.JvmName("MenuWithControlStates")
public fun Menu(
    items: List<PixelMenuItem>,
    states: PixelControlStateSet,
    enabled: Boolean = true,
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    key: Any? = null,
    semanticLabel: String? = null,
    onDismissRequest: (() -> Unit)? = null,
    modal: Boolean = true,
): Widget {
    return buildMenu(
        items = items,
        states = states,
        enabled = enabled,
        fillColor = fillColor,
        borderColor = borderColor,
        key = key,
        semanticLabel = semanticLabel,
        onDismissRequest = onDismissRequest,
        modal = modal,
        useLegacyFallbacks = false,
        tokenFamily = PixelMenuTokenFamily.Menu,
    )
}

/** Creates the retained themed Menu implementation shared by both public overloads. */
private fun buildMenu(
    items: List<PixelMenuItem>,
    states: PixelControlStateSet,
    enabled: Boolean,
    fillColor: PixelColor?,
    borderColor: PixelColor?,
    key: Any?,
    semanticLabel: String?,
    onDismissRequest: (() -> Unit)?,
    modal: Boolean,
    useLegacyFallbacks: Boolean,
    tokenFamily: PixelMenuTokenFamily,
): Widget {
    return PixelMenuWidget(
        items = items,
        states = states,
        enabled = enabled,
        fillColor = fillColor,
        borderColor = borderColor,
        semanticLabel = semanticLabel,
        onDismissRequest = onDismissRequest,
        modal = modal,
        useLegacyFallbacks = useLegacyFallbacks,
        tokenFamily = tokenFamily,
        key = key,
    )
}

/** Resolves Menu theme values at the mounted context rather than at factory invocation time. */
private data class PixelMenuWidget(
    /** Ordered controlled Menu rows. */
    val items: List<PixelMenuItem>,
    /** Persistent caller states shared by the collection and its rows. */
    val states: PixelControlStateSet,
    /** Caller-level row availability. */
    val enabled: Boolean,
    /** Optional explicit Menu surface fill. */
    val fillColor: PixelColor?,
    /** Optional explicit Menu surface outline. */
    val borderColor: PixelColor?,
    /** Optional explicit collection label. */
    val semanticLabel: String?,
    /** Optional modal and semantics dismissal callback. */
    val onDismissRequest: (() -> Unit)?,
    /** Whether this Menu should create a modal boundary. */
    val modal: Boolean,
    /** Whether no-theme rendering must retain pre-token resting defaults. */
    val useLegacyFallbacks: Boolean,
    /** Component-token family applied to both the collection surface and every row. */
    val tokenFamily: PixelMenuTokenFamily,
    /** Stable retained collection identity. */
    override val key: Any?,
) : StatelessWidget(key = key) {
    /** Resolves collection tokens, rows, semantics, and optional modal ownership. */
    override fun build(context: BuildContext): Widget {
        /** Explicit inherited graph, retained separately to detect a scope-less legacy call. */
        val inheritedTheme = PixelTheme.maybeTokensOf(context)
        /** Complete graph used for every token lookup in this frame. */
        val theme = inheritedTheme ?: PixelThemeTokens.Default
        /** Explicit localization bundle used only for text and semantic status resolution. */
        val localizations = PixelLocalizations.maybeOf(context)
        /** Menu- or Dropdown-specific state color and geometry tokens. */
        val tokens = when (tokenFamily) {
            PixelMenuTokenFamily.Menu -> theme.components.menu
            PixelMenuTokenFamily.Dropdown -> theme.components.dropdown
        }
        /** Persistent collection states after caller availability is normalized. */
        var effectiveStates = states
        if (!enabled) effectiveStates += PixelControlState.Disabled
        /** True only for an old resting call outside an explicit PixelTheme provider. */
        val legacyResting = useLegacyFallbacks && inheritedTheme == null && effectiveStates.isNormal
        /** Surface fill following explicit parameter, legacy fallback, then component role order. */
        val resolvedFillColor = fillColor ?: if (legacyResting) {
            LEGACY_MENU_FILL_COLOR
        } else {
            tokens.resolveContainerColor(effectiveStates, theme.colors)
        }
        /** Surface outline following explicit parameter, legacy fallback, then component role order. */
        val resolvedBorderColor = borderColor ?: if (legacyResting) {
            LEGACY_MENU_BORDER_COLOR
        } else {
            tokens.resolveBorderColor(effectiveStates, theme.colors)
        }
        /** Collection name preserving nullable explicit text, provider, theme, then English order. */
        val resolvedSemanticLabel = semanticLabel
            ?: localizations?.labels?.menu
            ?: inheritedTheme?.labels?.menu
            ?: LEGACY_MENU_SEMANTIC_LABEL
        /** Loading status resolved independently from every visual compatibility branch. */
        val resolvedLoadingLabel = localizations?.labels?.loading
            ?: inheritedTheme?.labels?.loading
            ?: PixelLabelTokens.Default.loading
        /** Error status resolved independently from every visual compatibility branch. */
        val resolvedErrorLabel = localizations?.labels?.error
            ?: inheritedTheme?.labels?.error
            ?: PixelLabelTokens.Default.error
        /** Ordered row widgets derived from the caller's stable item list. */
        val rows = items.mapIndexed { index, item ->
            /** Stable business key, with an index fallback for source compatibility. */
            val itemKey = item.key ?: key?.let { "$it-$index" }
            /** Row states combine Menu policy with controlled selection and row availability. */
            var rowStates = effectiveStates
            if (item.selected) rowStates += PixelControlState.Selected
            if (!item.enabled) rowStates += PixelControlState.Disabled
            PixelMenuRow(
                item = item,
                rowIndex = index,
                states = rowStates,
                useLegacyFallbacks = useLegacyFallbacks,
                tokenFamily = tokenFamily,
                key = itemKey,
            )
        }
        /** Menu content before surface color, border, padding, and minimum width are applied. */
        val rowColumn = Column(
            children = rows,
            spacing = 0,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )
        /** Scope-less legacy calls retain natural width; themed/new calls use overlay size tokens. */
        val minimumWidth = if (useLegacyFallbacks && inheritedTheme == null) {
            0
        } else {
            maxOf(tokens.resolveMinimumWidth(theme.sizes), theme.sizes.overlayMinimumWidth)
        }
        /** Painted Menu surface shared by visual rows and collection semantics. */
        val menuSurface = ConstrainedBox(
            constraints = PixelBoxConstraints(
                minWidth = minimumWidth,
                minHeight = if (legacyResting) 0 else tokens.resolveMinimumHeight(theme.sizes),
            ),
            child = PixelSurface(
                padding = if (legacyResting) EdgeInsets.all(1) else tokens.resolvePadding(theme.spacing),
                decoration = PixelSurfaceDecoration(
                    fillColor = resolvedFillColor,
                    borderColor = resolvedBorderColor,
                    borderWidth = if (legacyResting) 1 else tokens.resolveBorderWidth(theme.borders),
                    cornerRadius = if (legacyResting) 0 else tokens.resolveCornerRadius(theme.radii),
                    shadowColor = theme.colors.shadow,
                    shadowOffset = if (legacyResting) 0 else tokens.resolveElevation(theme.elevations),
                ),
                child = rowColumn,
                key = key,
            ),
            key = key?.let { "$it-constraints" },
        )
        /** Every dismiss channel is a mutation action and is absent for Loading or Disabled. */
        val effectiveDismiss = onDismissRequest?.takeIf {
            PixelControlState.Disabled !in effectiveStates &&
                PixelControlState.Loading !in effectiveStates
        }
        /** Collection-level semantic node wrapping all independently actionable rows. */
        val semanticMenu = Semantics(
            label = resolvedSemanticLabel,
            role = PixelSemanticRole.MENU,
            enabled = PixelControlState.Disabled !in effectiveStates &&
                PixelControlState.Loading !in effectiveStates,
            value = resolvedLoadingLabel.takeIf { PixelControlState.Loading in effectiveStates },
            error = resolvedErrorLabel.takeIf { PixelControlState.Error in effectiveStates },
            collectionInfo = PixelSemanticsCollectionInfo(
                rowCount = items.size,
                columnCount = 1,
                selectionMode = PixelSemanticsSelectionMode.SINGLE,
            ),
            actions = PixelSemanticsActions(
                onDismiss = effectiveDismiss?.let { dismiss ->
                    {
                        dismiss()
                        true
                    }
                },
            ),
            child = menuSurface,
            key = key?.let { "$it-semantics" },
        )
        return if (modal) {
            ContextualMenuModalBoundary(
                child = semanticMenu,
                onDismissRequest = effectiveDismiss,
                key = key?.let { "$it-modal-boundary" },
            )
        } else {
            semanticMenu
        }
    }
}

/** Creates one retained Menu row with automatic focus and canonical activation handling. */
private fun PixelMenuRow(
    item: PixelMenuItem,
    rowIndex: Int,
    states: PixelControlStateSet,
    useLegacyFallbacks: Boolean,
    tokenFamily: PixelMenuTokenFamily,
    key: Any?,
): Widget {
    /** Disabled is the only state that removes this row from focus traversal. */
    val focusable = PixelControlState.Disabled !in states
    /** Loading keeps focus but rejects pointer, keyboard, and semantic activation. */
    val interactive = focusable && PixelControlState.Loading !in states
    /** Pointer, keyboard, and semantics share the exact same Boolean action. */
    val activate: (() -> Boolean)? = item.onSelected.takeIf { interactive }?.let { callback ->
        {
            callback()
            true
        }
    }
    return AutomaticFocusAction(
        enabled = focusable,
        debugLabel = item.label,
        onKeyEvent = activate?.let(::activationKeyHandler),
        key = key,
    ) { _, focusNode ->
        PixelMenuRowWidget(
            item = item,
            rowIndex = rowIndex,
            states = states,
            focusNode = focusNode,
            activate = activate,
            useLegacyFallbacks = useLegacyFallbacks,
            tokenFamily = tokenFamily,
            key = key,
        )
    }
}

/** Retained Menu row configuration whose hover and pressed states are runtime-owned. */
private data class PixelMenuRowWidget(
    /** Controlled Menu item data. */
    val item: PixelMenuItem,
    /** Zero-based collection position exported to accessibility. */
    val rowIndex: Int,
    /** Persistent selection, capability, and validation states. */
    val states: PixelControlStateSet,
    /** Effective focus node owned by AutomaticFocusAction. */
    val focusNode: FocusNode,
    /** Shared activation callback, absent for Disabled and Loading. */
    val activate: (() -> Boolean)?,
    /** Whether scope-less Normal rendering retains the old row defaults. */
    val useLegacyFallbacks: Boolean,
    /** Component-token family shared with the owning collection surface. */
    val tokenFamily: PixelMenuTokenFamily,
    /** Stable row identity. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates the retained pointer-state owner for this row. */
    override fun createState(): State<out StatefulWidget> = PixelMenuRowState()
}

/** Owns Menu-row hover and press flags while resolving all persistent state combinations. */
private class PixelMenuRowState : State<PixelMenuRowWidget>() {
    /** Whether this row currently owns a captured pointer press. */
    private var pressed: Boolean = false

    /** Whether a mouse or stylus currently hovers over this row. */
    private var hovered: Boolean = false

    /** Resolves row colors, focus outline, pointer behavior, and semantic state. */
    override fun build(context: BuildContext): Widget {
        /** Explicit inherited graph retained to recognize a scope-less legacy row. */
        val inheritedTheme = PixelTheme.maybeTokensOf(context)
        /** Complete theme graph used by this frame. */
        val theme = inheritedTheme ?: PixelThemeTokens.Default
        /** Explicit localization bundle used only for row semantic status text. */
        val localizations = PixelLocalizations.maybeOf(context)
        /** Loading status resolved without participating in geometry or color selection. */
        val resolvedLoadingLabel = localizations?.labels?.loading
            ?: inheritedTheme?.labels?.loading
            ?: PixelLabelTokens.Default.loading
        /** Error status resolved without participating in geometry or color selection. */
        val resolvedErrorLabel = localizations?.labels?.error
            ?: inheritedTheme?.labels?.error
            ?: PixelLabelTokens.Default.error
        /** Menu or Dropdown state and geometry tokens shared with the collection surface. */
        val tokens = when (widget.tokenFamily) {
            PixelMenuTokenFamily.Menu -> theme.components.menu
            PixelMenuTokenFamily.Dropdown -> theme.components.dropdown
        }
        context.watch(widget.focusNode)
        /** Whether this row can currently receive activation input. */
        val interactive = widget.activate != null
        /** Complete runtime states after focus, press, and hover are merged. */
        val runtimeStates = mergeControlStates(
            persistent = widget.states,
            disabled = PixelControlState.Disabled in widget.states,
            pressed = interactive && pressed,
            hovered = interactive && hovered,
            focused = widget.focusNode.isFocused,
        )
        if (!interactive) {
            pressed = false
            hovered = false
        }
        /** Whether a non-focus state requires a visible role-based state treatment. */
        val hasStateVisual = listOf(
            PixelControlState.Hovered,
            PixelControlState.Pressed,
            PixelControlState.Selected,
            PixelControlState.Disabled,
            PixelControlState.Error,
            PixelControlState.Loading,
        ).any { state -> state in runtimeStates }
        /** Scope-less legacy Normal row detector. */
        val legacyResting = widget.useLegacyFallbacks && inheritedTheme == null && !hasStateVisual
        /** Current row fill resolved from the state map, with the old transparent rest state retained. */
        val fillColor = if (legacyResting) {
            null
        } else {
            tokens.resolveContainerColor(runtimeStates, theme.colors)
        }
        /** Current row foreground resolved independently from its fill. */
        val contentColor = if (legacyResting) {
            PixelTextStyle.Default.color
        } else {
            tokens.resolveContentColor(runtimeStates, theme.colors) ?: theme.colors.onSurface
        }
        /** Current row outline; Normal legacy rows intentionally remain borderless. */
        val borderColor = if (legacyResting || tokens.borderWidth == 0) {
            null
        } else {
            tokens.resolveBorderColor(runtimeStates, theme.colors)
        }
        /** Base label metrics preserve old defaults outside a theme and use label tokens otherwise. */
        val labelStyle = if (legacyResting) {
            PixelTextStyle.Default
        } else {
            theme.typography.label.resolve(theme.colors).copy(color = contentColor)
        }
        /** Shortcut metrics use the caption role while following the same state foreground. */
        val shortcutStyle = if (legacyResting) {
            PixelTextStyle(color = LEGACY_MENU_SHORTCUT_COLOR)
        } else {
            theme.typography.caption.resolve(theme.colors).copy(color = contentColor)
        }
        /** Optional shortcut rendered at the trailing edge. */
        val shortcut = widget.item.shortcut?.let { shortcutText ->
            Text(shortcutText, style = shortcutStyle)
        }
        /** Ordered title and optional shortcut content for this row. */
        val rowContent = Row(
            children = buildList {
                add(Expanded(child = Text(widget.item.label, style = labelStyle)))
                if (shortcut != null) add(shortcut)
            },
            spacing = theme.spacing.small,
            crossAxisAlignment = CrossAxisAlignment.CENTER,
        )
        /** Row surface before focus and pointer layers are added. */
        val surface = ConstrainedBox(
            constraints = PixelBoxConstraints(
                minWidth = if (legacyResting) 0 else tokens.resolveMinimumWidth(theme.sizes),
                minHeight = if (legacyResting) 0 else tokens.resolveMinimumHeight(theme.sizes),
            ),
            child = PixelSurface(
                child = rowContent,
                padding = if (legacyResting) {
                    EdgeInsets.symmetric(horizontal = 2, vertical = 2)
                } else {
                    tokens.resolvePadding(theme.spacing)
                },
                decoration = PixelSurfaceDecoration(
                    fillColor = fillColor,
                    borderColor = borderColor,
                    borderWidth = if (legacyResting) 0 else tokens.resolveBorderWidth(theme.borders),
                    cornerRadius = if (legacyResting) 0 else tokens.resolveCornerRadius(theme.radii),
                ),
                key = widget.key,
            ),
            key = widget.key?.let { "$it-constraints" },
        )
        /** Additive focus layer that never replaces selected, error, or pressed base colors. */
        val focusedSurface = withControlFocusIndicator(
            child = surface,
            states = runtimeStates,
            componentTokens = tokens,
            colors = theme.colors,
            borders = theme.borders,
            key = widget.key?.let { "$it-focus-indicator" },
        )
        /** Pointer target exists only while the normalized shared action is available. */
        val interactiveSurface = widget.activate?.let { activate ->
            InteractionDetector(
                child = focusedSurface,
                onTap = { activate() },
                onPressedChanged = ::updatePressed,
                onHoveredChanged = ::updateHovered,
                key = widget.key,
            )
        } ?: focusedSurface
        return Semantics(
            label = widget.item.label,
            role = PixelSemanticRole.MENU_ITEM,
            enabled = widget.activate != null,
            focused = widget.focusNode.isFocused,
            value = resolvedLoadingLabel.takeIf { PixelControlState.Loading in runtimeStates },
            error = resolvedErrorLabel.takeIf { PixelControlState.Error in runtimeStates },
            selected = widget.item.selected,
            collectionItemInfo = PixelSemanticsCollectionItemInfo(
                rowIndex = widget.rowIndex,
                columnIndex = 0,
                selected = widget.item.selected,
            ),
            excludeDescendants = true,
            actions = PixelSemanticsActions(onClick = widget.activate),
            child = interactiveSurface,
            key = widget.key?.let { "$it-semantics" },
        )
    }

    /** Updates retained pressed state exactly once per pointer ownership transition. */
    private fun updatePressed(nextPressed: Boolean) {
        if (pressed == nextPressed) return
        setState { pressed = nextPressed }
    }

    /** Updates retained hover state exactly once per pointer boundary transition. */
    private fun updateHovered(nextHovered: Boolean) {
        if (hovered == nextHovered) return
        setState { hovered = nextHovered }
    }
}

/** Lets a Menu reuse its enclosing Popover owner instead of creating a duplicate modal token. */
private class ContextualMenuModalBoundary(
    /** Fully built Menu semantics and visual subtree. */
    val child: Widget,
    /** Escape/Back callback used only when this Menu owns the modal. */
    val onDismissRequest: (() -> Unit)?,
    /** Stable retained identity for the contextual boundary. */
    override val key: Any?,
) : StatelessWidget(key = key) {
    /** Creates a modal only when no ancestor already owns the presentation. */
    override fun build(context: BuildContext): Widget {
        /** Nearest modal policy permitting Popover- or route-level token coalescing. */
        val modalPresence = context.getInheritedWidgetOfExactType<PixelModalFocusPresence>()
        if (
            modalPresence?.coalesceNestedMenu == true ||
            modalPresence?.coalesceNestedModal == true
        ) {
            return child
        }
        return StandaloneModalBoundaryFactory.create(
            active = true,
            onDismissRequest = onDismissRequest,
            child = ModalInteractionScopeWidget(
                active = true,
                child = child,
                key = key?.let { "$it-interaction" },
            ),
            key = key,
        )
    }
}

/**
 * 受控下拉菜单。
 *
 * [expanded] 和选中值由调用方维护；点击 anchor 只调用 [onToggle]，点击菜单项只调用 item 自身动作。
 */
public fun Dropdown(
    label: String,
    selectedText: String,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    items: List<PixelMenuItem>,
    enabled: Boolean = true,
    contentOffset: IntOffset = IntOffset(0, 14),
    key: Any? = null,
): Widget {
    return buildDropdown(
        label = label,
        selectedText = selectedText,
        expanded = expanded,
        onToggle = onToggle,
        items = items,
        states = PixelControlStateSet.Normal,
        enabled = enabled,
        contentOffset = contentOffset.takeUnless { offset -> offset == LEGACY_DROPDOWN_CONTENT_OFFSET },
        fillColor = null,
        borderColor = null,
        textStyle = null,
        semanticLabel = null,
        useLegacyFallbacks = true,
        key = key,
    )
}

/** Legacy Dropdown popup offset used only to recognize an omitted theme spacing override. */
private val LEGACY_DROPDOWN_CONTENT_OFFSET: IntOffset = IntOffset(0, 14)

/** Legacy Dropdown outline retained for a scope-less resting anchor. */
private val LEGACY_DROPDOWN_BORDER_COLOR: PixelColor = PixelColor.White

/** Legacy Dropdown fallback label used only when all caller text is empty. */
private const val LEGACY_DROPDOWN_SEMANTIC_LABEL: String = "Dropdown"

/**
 * 执行 `OverlayControls` 的 `Dropdown` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware controlled Dropdown with a token-resolved anchor and Menu presentation.
 *
 * Loading leaves the anchor focusable but removes toggle, expand, and collapse actions. Disabled
 * removes it from traversal. Expanded is represented as Selected for visual state resolution while
 * remaining a separate structured semantic value.
 *
 * @param label Visible field label and preferred accessibility name.
 * @param selectedText Controlled visible value.
 * @param expanded Controlled popup expansion state.
 * @param onToggle Controlled expansion callback; null is treated as Disabled.
 * @param items Controlled popup rows.
 * @param states Persistent visual and capability states.
 * @param enabled Caller-level interaction availability.
 * @param contentOffset Optional explicit anchor offset above theme spacing.
 * @param fillColor Optional explicit anchor fill above component tokens.
 * @param borderColor Optional explicit anchor outline above component tokens.
 * @param textStyle Optional explicit anchor typography above component and foundation tokens.
 * @param semanticLabel Optional explicit accessibility name above label tokens.
 * @param key Stable anchor, popup, focus, and retained presentation identity.
 */
@kotlin.jvm.JvmName("DropdownWithControlStates")
public fun Dropdown(
    label: String,
    selectedText: String,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    items: List<PixelMenuItem>,
    states: PixelControlStateSet,
    enabled: Boolean = true,
    contentOffset: IntOffset? = null,
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    textStyle: PixelTextStyle? = null,
    semanticLabel: String? = null,
    key: Any? = null,
): Widget {
    return buildDropdown(
        label = label,
        selectedText = selectedText,
        expanded = expanded,
        onToggle = onToggle,
        items = items,
        states = states,
        enabled = enabled,
        contentOffset = contentOffset,
        fillColor = fillColor,
        borderColor = borderColor,
        textStyle = textStyle,
        semanticLabel = semanticLabel,
        useLegacyFallbacks = false,
        key = key,
    )
}

/** Creates the mounted themed Dropdown implementation shared by both public overloads. */
private fun buildDropdown(
    label: String,
    selectedText: String,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    items: List<PixelMenuItem>,
    states: PixelControlStateSet,
    enabled: Boolean,
    contentOffset: IntOffset?,
    fillColor: PixelColor?,
    borderColor: PixelColor?,
    textStyle: PixelTextStyle?,
    semanticLabel: String?,
    useLegacyFallbacks: Boolean,
    key: Any?,
): Widget {
    return PixelDropdownWidget(
        label = label,
        selectedText = selectedText,
        expanded = expanded,
        onToggle = onToggle,
        items = items,
        states = states,
        enabled = enabled,
        contentOffset = contentOffset,
        fillColor = fillColor,
        borderColor = borderColor,
        textStyle = textStyle,
        semanticLabel = semanticLabel,
        useLegacyFallbacks = useLegacyFallbacks,
        key = key,
    )
}

/** Resolves inherited Dropdown tokens before constructing its focusable anchor and popup. */
private data class PixelDropdownWidget(
    /** Visible field label. */
    val label: String,
    /** Controlled visible value. */
    val selectedText: String,
    /** Controlled popup expansion state. */
    val expanded: Boolean,
    /** Controlled toggle callback. */
    val onToggle: (() -> Unit)?,
    /** Ordered controlled Menu rows. */
    val items: List<PixelMenuItem>,
    /** Persistent caller visual and capability states. */
    val states: PixelControlStateSet,
    /** Caller-level interaction availability. */
    val enabled: Boolean,
    /** Optional explicit popup offset. */
    val contentOffset: IntOffset?,
    /** Optional explicit anchor fill. */
    val fillColor: PixelColor?,
    /** Optional explicit anchor outline. */
    val borderColor: PixelColor?,
    /** Optional explicit anchor typography. */
    val textStyle: PixelTextStyle?,
    /** Optional explicit accessibility name. */
    val semanticLabel: String?,
    /** Whether scope-less resting rendering retains old anchor geometry. */
    val useLegacyFallbacks: Boolean,
    /** Stable Dropdown identity. */
    override val key: Any?,
) : StatelessWidget(key = key) {
    /** Builds the normalized anchor action, themed Menu, and controlled Popover. */
    override fun build(context: BuildContext): Widget {
        /** Explicit inherited graph retained for scope-less compatibility detection. */
        val inheritedTheme = PixelTheme.maybeTokensOf(context)
        /** Complete theme used to resolve fallback spacing and labels. */
        val theme = inheritedTheme ?: PixelThemeTokens.Default
        /** Anchor states after expansion and public availability are normalized. */
        var anchorStates = states
        if (expanded) anchorStates += PixelControlState.Selected
        if (!enabled || onToggle == null) anchorStates += PixelControlState.Disabled
        /** Disabled alone removes focus eligibility. */
        val focusable = PixelControlState.Disabled !in anchorStates
        /** Loading suppresses activation without clearing focus eligibility. */
        val interactive = focusable && PixelControlState.Loading !in anchorStates
        /** Pointer, keyboard, and all semantic expansion actions share this callback. */
        val activate: (() -> Boolean)? = onToggle?.takeIf { interactive }?.let { toggle ->
            {
                toggle()
                true
            }
        }
        /** Focus-aware stateful anchor exporting one canonical Dropdown semantic node. */
        val anchor = AutomaticFocusAction(
            enabled = focusable,
            debugLabel = semanticLabel ?: label.ifBlank { selectedText },
            onKeyEvent = activate?.let(::activationKeyHandler),
            key = key?.let { "$it-anchor-focus" },
        ) { _, focusNode ->
            PixelDropdownAnchorWidget(
                label = label,
                selectedText = selectedText,
                expanded = expanded,
                states = anchorStates,
                focusNode = focusNode,
                activate = activate,
                fillColor = fillColor,
                borderColor = borderColor,
                textStyle = textStyle,
                semanticLabel = semanticLabel,
                useLegacyFallbacks = useLegacyFallbacks,
                key = key?.let { "$it-anchor" },
            )
        }
        /** Menu states preserve caller Error/Loading while excluding anchor-only expansion state. */
        var menuStates = states
        if (!enabled || onToggle == null) menuStates += PixelControlState.Disabled
        /** Popup Menu built with the same compatibility and inherited-theme policy. */
        val menu = buildMenu(
            items = items,
            states = menuStates,
            enabled = PixelControlState.Disabled !in menuStates,
            fillColor = null,
            borderColor = null,
            key = key?.let { "$it-menu" },
            semanticLabel = null,
            onDismissRequest = if (expanded) activate?.let { action -> { action() } } else null,
            modal = false,
            useLegacyFallbacks = useLegacyFallbacks,
            tokenFamily = PixelMenuTokenFamily.Dropdown,
        )
        /** Explicit offset, legacy fallback, then the current foundation spacing token. */
        val resolvedContentOffset = contentOffset ?: if (useLegacyFallbacks && inheritedTheme == null) {
            LEGACY_DROPDOWN_CONTENT_OFFSET
        } else {
            IntOffset(0, theme.spacing.medium)
        }
        return Popover(
            anchor = anchor,
            content = menu,
            expanded = expanded,
            contentOffset = resolvedContentOffset,
            dismissible = expanded && activate != null,
            onDismiss = if (expanded) activate?.let { action -> { action() } } else null,
            modal = true,
            key = key,
        )
    }
}

/** Retained Dropdown anchor configuration whose transient pointer states are runtime-owned. */
private data class PixelDropdownAnchorWidget(
    /** Visible field label. */
    val label: String,
    /** Controlled visible value. */
    val selectedText: String,
    /** Structured expanded state. */
    val expanded: Boolean,
    /** Persistent normalized anchor states. */
    val states: PixelControlStateSet,
    /** Effective retained focus node. */
    val focusNode: FocusNode,
    /** Shared toggle action, absent for Disabled and Loading. */
    val activate: (() -> Boolean)?,
    /** Optional explicit surface fill. */
    val fillColor: PixelColor?,
    /** Optional explicit surface outline. */
    val borderColor: PixelColor?,
    /** Optional explicit text style. */
    val textStyle: PixelTextStyle?,
    /** Optional explicit semantic name. */
    val semanticLabel: String?,
    /** Whether a scope-less resting anchor retains pre-token geometry. */
    val useLegacyFallbacks: Boolean,
    /** Stable anchor identity. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates the retained hover and press owner. */
    override fun createState(): State<out StatefulWidget> = PixelDropdownAnchorState()
}

/** Owns Dropdown hover/press state and resolves its independent focus presentation. */
private class PixelDropdownAnchorState : State<PixelDropdownAnchorWidget>() {
    /** Whether this anchor currently owns a captured pointer press. */
    private var pressed: Boolean = false

    /** Whether a mouse or stylus currently hovers over this anchor. */
    private var hovered: Boolean = false

    /** Resolves the complete Dropdown anchor surface and structured semantics. */
    override fun build(context: BuildContext): Widget {
        /** Explicit inherited graph retained for scope-less compatibility detection. */
        val inheritedTheme = PixelTheme.maybeTokensOf(context)
        /** Complete theme graph used by all anchor channels. */
        val theme = inheritedTheme ?: PixelThemeTokens.Default
        /** Explicit localization bundle used only for fallback and state semantics. */
        val localizations = PixelLocalizations.maybeOf(context)
        /** Dropdown-specific state color and geometry tokens. */
        val tokens = theme.components.dropdown
        context.watch(widget.focusNode)
        /** Whether pointer input can currently mutate expansion. */
        val interactive = widget.activate != null
        /** Complete runtime states after focus, press, and hover are merged. */
        val runtimeStates = mergeControlStates(
            persistent = widget.states,
            disabled = PixelControlState.Disabled in widget.states,
            pressed = interactive && pressed,
            hovered = interactive && hovered,
            focused = widget.focusNode.isFocused,
        )
        if (!interactive) {
            pressed = false
            hovered = false
        }
        /** Whether a non-focus state requires token-based visual treatment. */
        val hasStateVisual = listOf(
            PixelControlState.Hovered,
            PixelControlState.Pressed,
            PixelControlState.Selected,
            PixelControlState.Disabled,
            PixelControlState.Error,
            PixelControlState.Loading,
        ).any { state -> state in runtimeStates }
        /** Old collapsed Normal anchor outside a provider retains original button defaults. */
        val legacyResting = widget.useLegacyFallbacks && inheritedTheme == null && !hasStateVisual
        /** Explicit fill wins before legacy resting and role-based defaults. */
        val resolvedFillColor = widget.fillColor ?: if (legacyResting) {
            null
        } else {
            tokens.resolveContainerColor(runtimeStates, theme.colors)
        }
        /** Explicit outline wins before legacy resting and role-based defaults. */
        val resolvedBorderColor = widget.borderColor ?: if (legacyResting) {
            LEGACY_DROPDOWN_BORDER_COLOR
        } else {
            tokens.resolveBorderColor(runtimeStates, theme.colors)
        }
        /** Current state foreground role used only when no explicit text style is supplied. */
        val resolvedContentColor = tokens.resolveContentColor(runtimeStates, theme.colors)
            ?: theme.colors.onSurface
        /** Explicit typography wins before legacy resting and role-aware button typography. */
        val resolvedTextStyle = widget.textStyle ?: if (legacyResting) {
            PixelTextStyle.Default
        } else {
            theme.typography.button.resolve(theme.colors).copy(color = resolvedContentColor)
        }
        /** Visible anchor text combining label, selected value, and disclosure mark. */
        val buttonText = if (widget.label.isBlank()) {
            "${widget.selectedText} v"
        } else {
            "${widget.label}: ${widget.selectedText} v"
        }
        /** Old resting anchor retains natural width; themed/state-aware anchors use overlay sizing. */
        val minimumWidth = if (legacyResting) {
            0
        } else {
            maxOf(tokens.resolveMinimumWidth(theme.sizes), theme.sizes.overlayMinimumWidth)
        }
        /** Token-resolved anchor surface before focus and pointer behavior. */
        val surface = ConstrainedBox(
            constraints = PixelBoxConstraints(
                minWidth = minimumWidth,
                minHeight = if (legacyResting) 0 else tokens.resolveMinimumHeight(theme.sizes),
            ),
            child = PixelSurface(
                padding = if (legacyResting) EdgeInsets.all(2) else tokens.resolvePadding(theme.spacing),
                decoration = PixelSurfaceDecoration(
                    fillColor = resolvedFillColor,
                    borderColor = resolvedBorderColor,
                    borderWidth = if (legacyResting) 1 else tokens.resolveBorderWidth(theme.borders),
                    cornerRadius = if (legacyResting) 0 else tokens.resolveCornerRadius(theme.radii),
                    shadowColor = theme.colors.shadow,
                    shadowOffset = if (legacyResting) 0 else tokens.resolveElevation(theme.elevations),
                ),
                child = Text(
                    buttonText,
                    style = resolvedTextStyle,
                    overflow = PixelTextOverflow.ELLIPSIS,
                    softWrap = false,
                    maxLines = 1,
                    textAlign = TextAlign.CENTER,
                ),
                key = widget.key,
            ),
            key = widget.key?.let { "$it-constraints" },
        )
        /** Additive focus outline leaves selected, error, and pressed base roles intact. */
        val focusedSurface = withControlFocusIndicator(
            child = surface,
            states = runtimeStates,
            componentTokens = tokens,
            colors = theme.colors,
            borders = theme.borders,
            key = widget.key?.let { "$it-focus-indicator" },
        )
        /** Pointer target is absent during Loading and Disabled. */
        val interactiveSurface = widget.activate?.let { activate ->
            InteractionDetector(
                child = focusedSurface,
                onTap = { activate() },
                onPressedChanged = ::updatePressed,
                onHoveredChanged = ::updateHovered,
                key = widget.key,
            )
        } ?: focusedSurface
        /** Preferred name preserving blank explicit labels and existing visible-text precedence. */
        val resolvedSemanticLabel = widget.semanticLabel
            ?: widget.label.takeIf(String::isNotBlank)
            ?: widget.selectedText.takeIf(String::isNotBlank)
            ?: localizations?.labels?.dropdown
            ?: inheritedTheme?.labels?.dropdown
            ?: LEGACY_DROPDOWN_SEMANTIC_LABEL
        /** Loading hint resolved independently from the anchor's visual compatibility branch. */
        val resolvedLoadingLabel = localizations?.labels?.loading
            ?: inheritedTheme?.labels?.loading
            ?: PixelLabelTokens.Default.loading
        /** Error status resolved independently from the anchor's visual compatibility branch. */
        val resolvedErrorLabel = localizations?.labels?.error
            ?: inheritedTheme?.labels?.error
            ?: PixelLabelTokens.Default.error
        return Semantics(
            label = resolvedSemanticLabel,
            role = PixelSemanticRole.BUTTON,
            enabled = widget.activate != null,
            focused = widget.focusNode.isFocused,
            value = widget.selectedText,
            hint = resolvedLoadingLabel.takeIf { PixelControlState.Loading in runtimeStates },
            error = resolvedErrorLabel.takeIf { PixelControlState.Error in runtimeStates },
            expanded = widget.expanded,
            excludeDescendants = true,
            actions = PixelSemanticsActions(
                onClick = widget.activate,
                onExpand = widget.activate.takeIf { !widget.expanded },
                onCollapse = widget.activate.takeIf { widget.expanded },
            ),
            child = interactiveSurface,
            key = widget.key?.let { "$it-semantics" },
        )
    }

    /** Updates retained press state exactly once per pointer ownership transition. */
    private fun updatePressed(nextPressed: Boolean) {
        if (pressed == nextPressed) return
        setState { pressed = nextPressed }
    }

    /** Updates retained hover state exactly once per pointer boundary transition. */
    private fun updateHovered(nextHovered: Boolean) {
        if (hovered == nextHovered) return
        setState { hovered = nextHovered }
    }
}

/**
 * 受控提示浮层。
 *
 * [visible] 由调用方根据焦点、长按或业务状态维护；组件不会监听 hover，也不会自动延迟显示。
 */
public fun Tooltip(
    message: String,
    visible: Boolean,
    child: Widget,
    contentOffset: IntOffset = IntOffset(0, 10),
    fillColor: PixelColor = PixelColor.Black,
    borderColor: PixelColor = PixelColor.White,
    textStyle: PixelTextStyle = PixelTextStyle.Default,
    key: Any? = null,
): Widget {
    return buildTooltip(
        message = message,
        visible = visible,
        child = child,
        states = PixelControlStateSet.Normal,
        contentOffset = contentOffset.takeUnless { offset -> offset == LEGACY_TOOLTIP_CONTENT_OFFSET },
        fillColor = fillColor.takeUnless { color -> color == LEGACY_TOOLTIP_FILL_COLOR },
        borderColor = borderColor.takeUnless { color -> color == LEGACY_TOOLTIP_BORDER_COLOR },
        textStyle = textStyle.takeUnless { style -> style == PixelTextStyle.Default },
        semanticLabel = null,
        useLegacyFallbacks = true,
        key = key,
    )
}

/** Legacy Tooltip popup offset used only to recognize an omitted theme spacing override. */
private val LEGACY_TOOLTIP_CONTENT_OFFSET: IntOffset = IntOffset(0, 10)

/** Legacy Tooltip fill used only to recognize an omitted theme override. */
private val LEGACY_TOOLTIP_FILL_COLOR: PixelColor = PixelColor.Black

/** Legacy Tooltip outline used only to recognize an omitted theme override. */
private val LEGACY_TOOLTIP_BORDER_COLOR: PixelColor = PixelColor.White

/** Legacy Tooltip accessibility fallback used only when the message is empty. */
private const val LEGACY_TOOLTIP_SEMANTIC_LABEL: String = "Tooltip"

/**
 * 执行 `OverlayControls` 的 `Tooltip` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware Tooltip with role-based surface, foreground, geometry, and semantic status.
 *
 * Tooltip is passive, so Loading and Error affect presentation and announcements without adding
 * mutation actions. Explicit visual values always win over component and foundation tokens.
 *
 * @param message Visible Tooltip message.
 * @param visible Controlled presentation visibility.
 * @param child In-flow anchor widget.
 * @param states Persistent visual status states.
 * @param contentOffset Optional explicit anchor offset above theme spacing.
 * @param fillColor Optional explicit fill above component tokens.
 * @param borderColor Optional explicit outline above component tokens.
 * @param textStyle Optional explicit typography above component and foundation tokens.
 * @param semanticLabel Optional accessibility name; null uses message or theme labels.
 * @param key Stable anchor and retained popup identity.
 */
@kotlin.jvm.JvmName("TooltipWithControlStates")
public fun Tooltip(
    message: String,
    visible: Boolean,
    child: Widget,
    states: PixelControlStateSet,
    contentOffset: IntOffset? = null,
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    textStyle: PixelTextStyle? = null,
    semanticLabel: String? = null,
    key: Any? = null,
): Widget {
    return buildTooltip(
        message = message,
        visible = visible,
        child = child,
        states = states,
        contentOffset = contentOffset,
        fillColor = fillColor,
        borderColor = borderColor,
        textStyle = textStyle,
        semanticLabel = semanticLabel,
        useLegacyFallbacks = false,
        key = key,
    )
}

/** Creates the mounted themed Tooltip shared by both public overloads. */
private fun buildTooltip(
    message: String,
    visible: Boolean,
    child: Widget,
    states: PixelControlStateSet,
    contentOffset: IntOffset?,
    fillColor: PixelColor?,
    borderColor: PixelColor?,
    textStyle: PixelTextStyle?,
    semanticLabel: String?,
    useLegacyFallbacks: Boolean,
    key: Any?,
): Widget {
    return PixelTooltipWidget(
        message = message,
        visible = visible,
        child = child,
        states = states,
        contentOffset = contentOffset,
        fillColor = fillColor,
        borderColor = borderColor,
        textStyle = textStyle,
        semanticLabel = semanticLabel,
        useLegacyFallbacks = useLegacyFallbacks,
        key = key,
    )
}

/** Resolves Tooltip tokens at the mounted popup declaration context. */
private data class PixelTooltipWidget(
    /** Visible Tooltip message. */
    val message: String,
    /** Controlled popup visibility. */
    val visible: Boolean,
    /** In-flow anchor widget. */
    val child: Widget,
    /** Persistent caller status states. */
    val states: PixelControlStateSet,
    /** Optional explicit popup offset. */
    val contentOffset: IntOffset?,
    /** Optional explicit surface fill. */
    val fillColor: PixelColor?,
    /** Optional explicit surface outline. */
    val borderColor: PixelColor?,
    /** Optional explicit message typography. */
    val textStyle: PixelTextStyle?,
    /** Optional explicit accessibility name. */
    val semanticLabel: String?,
    /** Whether scope-less Normal rendering retains old geometry and colors. */
    val useLegacyFallbacks: Boolean,
    /** Stable Tooltip identity. */
    override val key: Any?,
) : StatelessWidget(key = key) {
    /** Builds the resolved passive surface inside a non-modal Popover. */
    override fun build(context: BuildContext): Widget {
        /** Explicit inherited graph retained for scope-less compatibility detection. */
        val inheritedTheme = PixelTheme.maybeTokensOf(context)
        /** Complete graph used by every Tooltip channel. */
        val theme = inheritedTheme ?: PixelThemeTokens.Default
        /** Explicit localization bundle used only for fallback and state semantics. */
        val localizations = PixelLocalizations.maybeOf(context)
        /** Tooltip-specific state color and geometry tokens. */
        val tokens = theme.components.tooltip
        /** Scope-less old Normal call retains the pre-token resting presentation. */
        val legacyResting = useLegacyFallbacks && inheritedTheme == null && states.isNormal
        /** Explicit fill wins before compatibility and role-based defaults. */
        val resolvedFillColor = fillColor ?: if (legacyResting) {
            LEGACY_TOOLTIP_FILL_COLOR
        } else {
            tokens.resolveContainerColor(states, theme.colors)
        }
        /** Explicit outline wins before compatibility and role-based defaults. */
        val resolvedBorderColor = borderColor ?: if (legacyResting) {
            LEGACY_TOOLTIP_BORDER_COLOR
        } else {
            tokens.resolveBorderColor(states, theme.colors)
        }
        /** Current foreground role used when no explicit typography is supplied. */
        val resolvedContentColor = tokens.resolveContentColor(states, theme.colors)
            ?: theme.colors.onSurface
        /** Explicit typography wins before compatibility and caption typography tokens. */
        val resolvedTextStyle = textStyle ?: if (legacyResting) {
            PixelTextStyle.Default
        } else {
            theme.typography.caption.resolve(theme.colors).copy(color = resolvedContentColor)
        }
        /** Explicit offset, old fallback, then the current foundation spacing token. */
        val resolvedContentOffset = contentOffset ?: if (useLegacyFallbacks && inheritedTheme == null) {
            LEGACY_TOOLTIP_CONTENT_OFFSET
        } else {
            IntOffset(0, theme.spacing.medium)
        }
        /** Preferred name preserving blank explicit labels and existing message precedence. */
        val resolvedSemanticLabel = semanticLabel
            ?: message.takeIf(String::isNotBlank)
            ?: localizations?.labels?.tooltip
            ?: inheritedTheme?.labels?.tooltip
            ?: LEGACY_TOOLTIP_SEMANTIC_LABEL
        /** Loading status resolved independently from the Tooltip's visual compatibility branch. */
        val resolvedLoadingLabel = localizations?.labels?.loading
            ?: inheritedTheme?.labels?.loading
            ?: PixelLabelTokens.Default.loading
        /** Error status resolved independently from the Tooltip's visual compatibility branch. */
        val resolvedErrorLabel = localizations?.labels?.error
            ?: inheritedTheme?.labels?.error
            ?: PixelLabelTokens.Default.error
        /** Token-resolved Tooltip surface before popup retention and placement. */
        val surface = ConstrainedBox(
            constraints = PixelBoxConstraints(
                minWidth = if (legacyResting) 0 else tokens.resolveMinimumWidth(theme.sizes),
                minHeight = if (legacyResting) 0 else tokens.resolveMinimumHeight(theme.sizes),
            ),
            child = PixelSurface(
                padding = if (legacyResting) {
                    EdgeInsets.symmetric(horizontal = 3, vertical = 2)
                } else {
                    tokens.resolvePadding(theme.spacing)
                },
                decoration = PixelSurfaceDecoration(
                    fillColor = resolvedFillColor,
                    borderColor = resolvedBorderColor,
                    borderWidth = if (legacyResting) 1 else tokens.resolveBorderWidth(theme.borders),
                    cornerRadius = if (legacyResting) 0 else tokens.resolveCornerRadius(theme.radii),
                    shadowColor = theme.colors.shadow,
                    shadowOffset = if (legacyResting) 0 else tokens.resolveElevation(theme.elevations),
                ),
                child = Text(
                    message,
                    style = resolvedTextStyle,
                    softWrap = true,
                    maxLines = 2,
                    overflow = PixelTextOverflow.ELLIPSIS,
                ),
                key = key?.let { "$it-tooltip" },
            ),
            key = key?.let { "$it-tooltip-constraints" },
        )
        /** Passive semantic status remains separate from the visible message label. */
        val semanticSurface = Semantics(
            label = resolvedSemanticLabel,
            role = PixelSemanticRole.GENERIC,
            value = resolvedLoadingLabel.takeIf { PixelControlState.Loading in states },
            error = resolvedErrorLabel.takeIf { PixelControlState.Error in states },
            excludeDescendants = true,
            child = surface,
            key = key?.let { "$it-tooltip-semantics" },
        )
        return Popover(
            anchor = child,
            content = semanticSurface,
            expanded = visible,
            contentOffset = resolvedContentOffset,
            modal = false,
            key = key,
        )
    }
}

/** Combines stable system-safe padding and transient obscured regions such as the IME per edge. */
private fun MediaQueryData.overlaySafeInsets(): PixelWindowInsets {
    return viewPadding.atLeast(viewInsets)
}
