package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.internal.MultiChildRenderObject
import com.purride.pixelui.internal.MultiChildRenderObjectWidget
import com.purride.pixelui.internal.AutomaticFocusAction
import com.purride.pixelui.internal.GestureDetectorWidget
import com.purride.pixelui.internal.InteractionDetector
import com.purride.pixelui.internal.PaintContext
import com.purride.pixelui.internal.PixelClickTarget
import com.purride.pixelui.internal.PixelListTarget
import com.purride.pixelui.internal.PixelPagerTarget
import com.purride.pixelui.internal.PixelRect
import com.purride.pixelui.internal.PixelRefreshTarget
import com.purride.pixelui.internal.PixelScrollbarTarget
import com.purride.pixelui.internal.PixelSemanticsTarget
import com.purride.pixelui.internal.PixelSliderTarget
import com.purride.pixelui.internal.PixelTextInputTarget
import com.purride.pixelui.internal.RenderBox
import com.purride.pixelui.internal.RenderConstraints
import com.purride.pixelui.internal.RenderObject
import com.purride.pixelui.internal.RenderSize
import com.purride.pixelui.internal.activationKeyHandler
import com.purride.pixelui.internal.withControlFocusIndicator
import com.purride.pixelui.animation.Curve
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelAnimationStatus
import com.purride.pixelui.animation.PixelTickerProvider
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration

/**
 * Slidable 被打开或 dismiss 的方向。
 */
public enum class SlidableDirection {
    /**
     * 向 start 侧打开，对应正向水平位移。
     */
    START,

    /**
     * 向 end 侧打开，对应负向水平位移。
     */
    END,
}

/**
 * action pane 跟随主内容滑动时的运动方式。
 */
public enum class SlidableMotion {
    /**
     * action pane 固定在内容后方。
     */
    BEHIND,

    /**
     * action pane 以抽屉效果半速跟随。
     */
    DRAWER,

    /**
     * action pane 和内容一起滚动进入。
     */
    SCROLL,
}

/**
 * Slidable 一侧的操作面板配置。
 *
 * [children] 会均分面板宽度；[extentRatio] 会钳位到 `0.1f..1.0f` 后换算为面板宽度。
 * 当 [dismissible] 为 true 且滑动距离达到 [dismissThreshold] 时，会触发外层 [Slidable]
 * 的 dismiss 回调。
 */
public data class SlidableActionPane(
    /** Ordered actions distributed evenly across the revealed pane. */
    val children: List<Widget>,
    /** Fraction of the measured row width used by the opened pane. */
    val extentRatio: Float = 0.35f,
    /** Paint transform applied while the foreground row reveals this pane. */
    val motion: SlidableMotion = SlidableMotion.BEHIND,
    /** Whether a full semantic or pointer swipe may dismiss the owning row. */
    val dismissible: Boolean = false,
    /** Fraction of the pane width required before release selects the open/dismiss endpoint. */
    val dismissThreshold: Float = 0.5f,
)

/**
 * 可水平滑出操作面板的像素行容器。
 *
 * 向右滑打开 [startActionPane]，向左滑打开 [endActionPane]。组件只管理当前滑动偏移和面板
 * 呈现，不会删除数据；需要删除或归档时在 [onDismissed] 中更新业务状态。
 * 键盘用户可用 Tab 聚焦行，用 Enter/Space 触发 [onTap]，用 Left 打开 end 面板，
 * 用 Right 打开 start 面板；面板打开后可继续用 Tab 进入其操作项。
 */
public fun Slidable(
    child: Widget,
    startActionPane: SlidableActionPane? = null,
    endActionPane: SlidableActionPane? = null,
    onTap: (() -> Unit)? = null,
    onDismissed: ((SlidableDirection) -> Unit)? = null,
    key: Any? = null,
): Widget {
    return SlidableWidget(
        child = child,
        startActionPane = startActionPane,
        endActionPane = endActionPane,
        onTap = onTap,
        onDismissed = onDismissed,
        states = PixelControlStateSet.Normal,
        semanticLabel = null,
        key = key,
    )
}

/**
 * 执行 `Slidable` 的 `Slidable` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware Slidable row whose surface and input capability resolve from the active theme.
 *
 * Loading preserves the row focus and open-pane selection geometry while blocking new pointer,
 * keyboard, and dismiss actions. Disabled removes the row and pane actions from traversal.
 *
 * @param child Controlled row content shifted above the optional action panes.
 * @param states Persistent selected, error, loading, or forced interaction states.
 * @param startActionPane Optional actions revealed toward the logical start side.
 * @param endActionPane Optional actions revealed toward the logical end side.
 * @param onTap Optional row activation callback.
 * @param onDismissed Optional full-swipe dismissal callback.
 * @param enabled Whether pointer, keyboard, semantics, and pane actions are available.
 * @param semanticLabel Optional row label; null resolves from theme localization.
 * @param key Stable retained, focus, render, and semantics identity.
 */
@kotlin.jvm.JvmName("SlidableWithControlStates")
public fun Slidable(
    child: Widget,
    states: PixelControlStateSet,
    startActionPane: SlidableActionPane? = null,
    endActionPane: SlidableActionPane? = null,
    onTap: (() -> Unit)? = null,
    onDismissed: ((SlidableDirection) -> Unit)? = null,
    enabled: Boolean = true,
    semanticLabel: String? = null,
    key: Any? = null,
): Widget {
    /** Whether the row exposes any pointer or keyboard action when capability permits. */
    val hasAction = onTap != null || startActionPane != null || endActionPane != null
    /** Persistent states normalized with explicit availability and action capability. */
    var effectiveStates = states
    if (!enabled || !hasAction) effectiveStates += PixelControlState.Disabled
    return SlidableWidget(
        child = child,
        startActionPane = startActionPane,
        endActionPane = endActionPane,
        onTap = onTap,
        onDismissed = onDismissed,
        states = effectiveStates,
        semanticLabel = semanticLabel,
        key = key,
    )
}

/**
 * Slidable action pane 内的单个像素按钮。
 *
 * 对外暴露 Button 语义，并在面板打开时支持 Tab 聚焦以及 Enter/Space 激活。
 */
public fun SlidableAction(
    label: String,
    backgroundColor: PixelColor,
    foregroundColor: PixelColor,
    onPressed: () -> Unit,
    key: Any? = null,
): Widget {
    return buildSlidableAction(
        label = label,
        onPressed = onPressed,
        states = PixelControlStateSet.Normal,
        enabled = true,
        backgroundColor = backgroundColor,
        foregroundColor = foregroundColor,
        key = key,
    )
}

/**
 * 执行 `Slidable` 的 `SlidableAction` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware Slidable pane action with theme-resolved surface, typography, and focus geometry.
 *
 * @param label Visible and spoken action name.
 * @param onPressed Shared pointer, keyboard, and semantics callback; null means Disabled.
 * @param states Persistent selected, error, loading, or forced interaction states.
 * @param enabled Explicit capability flag combined with [onPressed].
 * @param backgroundColor Optional explicit container override above theme roles.
 * @param foregroundColor Optional explicit content override above theme roles.
 * @param key Stable retained, focus, render, and semantics identity.
 */
@kotlin.jvm.JvmName("SlidableActionWithControlStates")
public fun SlidableAction(
    label: String,
    onPressed: (() -> Unit)?,
    states: PixelControlStateSet,
    enabled: Boolean = true,
    backgroundColor: PixelColor? = null,
    foregroundColor: PixelColor? = null,
    key: Any? = null,
): Widget {
    return buildSlidableAction(
        label = label,
        onPressed = onPressed,
        states = states,
        enabled = enabled,
        backgroundColor = backgroundColor,
        foregroundColor = foregroundColor,
        key = key,
    )
}

/** Normalizes capability and installs the one automatic focus boundary shared by both overloads. */
private fun buildSlidableAction(
    label: String,
    onPressed: (() -> Unit)?,
    states: PixelControlStateSet,
    enabled: Boolean,
    backgroundColor: PixelColor?,
    foregroundColor: PixelColor?,
    key: Any?,
): Widget {
    /** Persistent states after callback and explicit enabled capability are normalized. */
    var effectiveStates = states
    if (!enabled || onPressed == null) effectiveStates += PixelControlState.Disabled
    /** Disabled removes traversal; Loading retains focus while rejecting activation. */
    val focusable = PixelControlState.Disabled !in effectiveStates
    /** Shared input action available only outside Disabled and Loading. */
    val interactive = focusable && PixelControlState.Loading !in effectiveStates
    /** Pointer, keyboard, and semantics action that invokes the callback exactly once. */
    val activate: (() -> Boolean)? = onPressed?.takeIf { interactive }?.let { callback ->
        {
            callback()
            true
        }
    }
    return AutomaticFocusAction(
        enabled = focusable,
        debugLabel = label,
        onKeyEvent = activate?.let(::activationKeyHandler),
        key = key,
    ) { _, _ ->
        PixelSlidableActionWidget(
            label = label,
            states = effectiveStates,
            activate = activate,
            pointerAction = onPressed?.takeIf { interactive },
            backgroundColor = backgroundColor,
            foregroundColor = foregroundColor,
            key = key,
        )
    }
}

/** Retained pane-action configuration whose transient pointer states remain runtime-owned. */
private data class PixelSlidableActionWidget(
    /** Visible and spoken action name. */
    val label: String,
    /** Persistent normalized states. */
    val states: PixelControlStateSet,
    /** Shared activation action, or null while inert. */
    val activate: (() -> Boolean)?,
    /** 指针目标身份使用的原始回调，与 [activate] 共享同一业务动作。 */
    val pointerAction: (() -> Unit)?,
    /** Optional explicit container color. */
    val backgroundColor: PixelColor?,
    /** Optional explicit foreground color. */
    val foregroundColor: PixelColor?,
    /** Stable retained, render, and semantics identity. */
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates one retained hover and press owner for this pane action. */
    override fun createState(): State<out StatefulWidget> = PixelSlidableActionState()
}

/** Slidable pane-action hover and press state merged with persistent and focus states. */
private class PixelSlidableActionState : State<PixelSlidableActionWidget>() {
    /** Whether this action currently owns a captured pointer press. */
    private var pressed: Boolean = false

    /** Whether a pointing device currently hovers over this action. */
    private var hovered: Boolean = false

    /** Resolves current state roles, pixel geometry, input, focus, and semantics. */
    override fun build(context: BuildContext): Widget {
        /** 继承的显式主题；保持可空以便本地化独立兜底。 Explicit inherited theme, kept nullable so localization can fall back independently. */
        val inheritedTheme = PixelTheme.maybeOf(context)
        /** Complete graph used for colors, typography, labels, and state-aware fallbacks. */
        val theme = inheritedTheme ?: PixelThemeTokens.Default
        /** Explicit localization bundle used only for semantic status text. */
        val localizations = PixelLocalizations.maybeOf(context)
        /** Loading status resolved independently from action geometry and color selection. */
        val resolvedLoadingLabel = localizations?.labels?.loading
            ?: inheritedTheme?.labels?.loading
            ?: PixelLabelTokens.Default.loading
        /** Error status resolved independently from action geometry and color selection. */
        val resolvedErrorLabel = localizations?.labels?.error
            ?: inheritedTheme?.labels?.error
            ?: PixelLabelTokens.Default.error
        /** Slidable-family component roles and geometry. */
        val tokens = theme.components.slidable
        /** Automatic focus node provided by the public action boundary. */
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) context.watch(focusNode)
        /** Combined caller, focus, hover, and captured-press states. */
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
        /** Explicit foreground override, then theme role, then aggregate surface foreground. */
        val contentColor = widget.foregroundColor
            ?: tokens.resolveContentColor(resolvedStates, theme.colors)
            ?: theme.colors.onSurface
        /** Explicit background override, then state-aware theme container role. */
        val containerColor = widget.backgroundColor
            ?: tokens.resolveContainerColor(resolvedStates, theme.colors)
        /** 两个公开动作重载共用的 token 化表面装饰。 Token-resolved surface decoration shared by both public action overloads. */
        val decoration = PixelSurfaceDecoration(
            fillColor = containerColor,
            borderColor = tokens.resolveBorderColor(resolvedStates, theme.colors),
            borderWidth = tokens.resolveBorderWidth(theme.borders),
            cornerRadius = tokens.resolveCornerRadius(theme.radii),
            shadowColor = theme.colors.shadow.takeIf {
                tokens.resolveElevation(theme.elevations) > 0
            },
            shadowOffset = tokens.resolveElevation(theme.elevations),
        )
        /** Theme-resolved action label before pointer and focus decoration. */
        val label = Text(
            widget.label,
            style = theme.typography.label.resolve(theme.colors).copy(color = contentColor),
            textAlign = TextAlign.CENTER,
            overflow = TextOverflow.ELLIPSIS,
            softWrap = false,
            maxLines = 1,
        )
        /** 由 token 解析出的动作表面几何。 Token-resolved action surface geometry. */
        val surface = PixelSurface(
            decoration = decoration,
            padding = tokens.resolvePadding(theme.spacing),
            alignment = Alignment.CENTER,
            child = label,
        )
        /** 叠加式焦点层保留 selected/error/loading/pressed 的底色。 Additive focus layer preserving selected, error, loading, and pressed base colors. */
        val focusedSurface = withControlFocusIndicator(
            child = surface,
            states = resolvedStates,
            componentTokens = tokens,
            colors = theme.colors,
            borders = theme.borders,
            key = widget.key?.let { "$it-focus-indicator" },
        )
        /** Pointer wrapper exists only while the normalized activation action is available. */
        val interactiveSurface = widget.pointerAction?.let { pointerAction ->
            InteractionDetector(
                child = focusedSurface,
                onTap = pointerAction,
                onPressedChanged = ::updatePressed,
                onHoveredChanged = ::updateHovered,
                key = widget.key,
            )
        } ?: focusedSurface
        return Semantics(
            label = widget.label,
            role = PixelSemanticRole.BUTTON,
            enabled = widget.activate != null,
            focused = PixelControlState.Focused in resolvedStates,
            value = resolvedLoadingLabel.takeIf { PixelControlState.Loading in resolvedStates },
            error = resolvedErrorLabel.takeIf { PixelControlState.Error in resolvedStates },
            excludeDescendants = true,
            actions = PixelSemanticsActions(onClick = widget.activate),
            child = interactiveSurface,
            key = widget.key?.let { "$it-semantics" },
        )
    }

    /** Updates captured press state once per pointer ownership transition. */
    private fun updatePressed(nextPressed: Boolean) {
        if (pressed == nextPressed) return
        setState { pressed = nextPressed }
    }

    /** Updates hover state once per mouse or stylus boundary transition. */
    private fun updateHovered(nextHovered: Boolean) {
        if (hovered == nextHovered) return
        setState { hovered = nextHovered }
    }
}

/** 简洁与 state-aware 两个 Slidable 公开入口共享的唯一 retained 配置。 */
private class SlidableWidget(
    /** Foreground row content translated above the action panes. */
    val child: Widget,
    /** Optional action pane revealed toward logical start. */
    val startActionPane: SlidableActionPane?,
    /** Optional action pane revealed toward logical end. */
    val endActionPane: SlidableActionPane?,
    /** Optional foreground activation callback. */
    val onTap: (() -> Unit)?,
    /** Optional business callback invoked after a dismiss endpoint completes. */
    val onDismissed: ((SlidableDirection) -> Unit)?,
    /** Persistent normalized states supplied by the public facade. */
    val states: PixelControlStateSet,
    /** Optional caller semantics label; null resolves from theme localization. */
    val semanticLabel: String?,
    /** Stable retained identity shared by focus, semantics, gestures, and render state. */
    key: Any?,
) : StatefulWidget(key = key) {
    /** Creates the state that owns pane, focus, gesture, and settle-animation lifecycles. */
    override fun createState(): State<out StatefulWidget> = SlidableState()
}

/** Owns one Slidable row's controlled presentation and every input modality. */
private class SlidableState : State<SlidableWidget>() {
    /** Keyboard focus owned by the Slidable row independently from action-pane descendants. */
    private val rowFocusNode: FocusNode = FocusNode(debugLabel = "Slidable")

    /** Retained traversal scope whose descendants belong exclusively to the start pane. */
    private val startPaneFocusScope: FocusScopeNode = FocusScopeNode()

    /** Retained traversal scope whose descendants belong exclusively to the end pane. */
    private val endPaneFocusScope: FocusScopeNode = FocusScopeNode()

    /** Logically open pane; null blocks both pane focus scopes even during a closing animation. */
    private var activePane: SlidableDirection? = null

    /** Whether the row currently owns a captured pointer sequence. */
    private var pressed: Boolean = false

    /** Whether a mouse or stylus currently hovers over the row. */
    private var hovered: Boolean = false

    /** Stable resting or drag offset when no settle animation is sampling a visual value. */
    private var offsetPx = 0

    /** Visual offset captured at the beginning of the current pointer gesture. */
    private var dragBaseOffsetPx = 0

    /** Exact latest render-layout width used to calculate pane, threshold, and dismiss endpoints. */
    private var contentWidthPx = 1

    /** Controller owned only while a Host-backed settle transition is active. */
    private var settleController: PixelAnimationController? = null

    /** Curve, including any theme delay, applied to [settleController]. */
    private var settleCurve: Curve? = null

    /** Visual offset from which the active settle transition interpolates. */
    private var settleStartOffsetPx = 0

    /** Exact closed, pane, or off-screen endpoint of the active settle transition. */
    private var settleTargetOffsetPx = 0

    /** Dismiss direction delivered only after the active exit reaches its exact endpoint. */
    private var pendingDismissDirection: SlidableDirection? = null

    /** Motion environment used by the active controller so inherited changes can retarget it. */
    private var settleEnvironment: SlidableSettleEnvironment? = null

    /** Hidden panes must reject focus before their first descendants are mounted. */
    init {
        startPaneFocusScope.setFocusBlocked(true)
        endPaneFocusScope.setFocusBlocked(true)
    }

    /** Removes invalid directional state when one declarative action pane disappears. */
    override fun didUpdateWidget(oldWidget: SlidableWidget) {
        val visualOffset = currentVisualOffsetPx()
        /** Disabled and Loading both revoke any stale transient pointer presentation. */
        if (
            PixelControlState.Disabled in widget.states ||
            PixelControlState.Loading in widget.states
        ) {
            pressed = false
            hovered = false
        }
        /** Loading freezes an active settle and cancels any dismissal delivery. */
        if (PixelControlState.Loading in widget.states && settleController != null) {
            cancelSettle(commitVisualOffset = true)
            pendingDismissDirection = null
        }
        /** Disabled is terminal for an exposed pane and removes its traversal subtree. */
        if (PixelControlState.Disabled in widget.states && activePane != null) {
            cancelSettle(commitVisualOffset = false)
            offsetPx = 0
            pendingDismissDirection = null
            updateActivePane(nextPane = null)
        }
        if (
            (widget.startActionPane == null && visualOffset > 0) ||
            (widget.endActionPane == null && visualOffset < 0) ||
            (widget.startActionPane == null && activePane == SlidableDirection.START) ||
            (widget.endActionPane == null && activePane == SlidableDirection.END)
        ) {
            cancelSettle(commitVisualOffset = false)
            offsetPx = 0
            pendingDismissDirection = null
            updateActivePane(nextPane = null)
        }
    }

    /** Builds direct-manipulation gesture handling and the current retained render presentation. */
    override fun build(context: BuildContext): Widget {
        /** 继承的显式主题；保持可空以便本地化独立兜底。 Explicit inherited theme, kept nullable so localization can fall back independently. */
        val inheritedTheme = PixelTheme.maybeOf(context)
        /** Complete graph used by state-aware rows and all semantic/color fallbacks. */
        val theme = inheritedTheme ?: PixelThemeTokens.Default
        /** Explicit localization bundle used only for fallback and state semantics. */
        val localizations = PixelLocalizations.maybeOf(context)
        /** Slidable-specific state, geometry, and elevation tokens. */
        val tokens = theme.components.slidable
        /** Disabled removes focus; Loading retains it while blocking every mutation path. */
        val disabled = PixelControlState.Disabled in widget.states
        /** Loading capability state retained independently from Disabled. */
        val loading = PixelControlState.Loading in widget.states
        /** Whether at least one controlled callback or pane action exists. */
        val hasAction = widget.onTap != null || widget.startActionPane != null || widget.endActionPane != null
        /** Row remains focusable while Loading but never while Disabled. */
        val rowFocusable = hasAction && !disabled
        /** Pointer, keyboard, and dismiss mutation capability. */
        val interactive = rowFocusable && !loading
        context.watch(rowFocusNode)
        val settleEnvironment = resolveSettleEnvironment(context)
        reconcileSettleMotion(settleEnvironment)
        settleController?.let(context::watch)
        val visualOffset = currentVisualOffsetPx()
        /** Complete visual state including runtime focus, pointer state, and open-pane selection. */
        var resolvedStates = widget.states
        if (rowFocusNode.isFocused) resolvedStates += PixelControlState.Focused
        if (interactive && pressed) resolvedStates += PixelControlState.Pressed
        if (interactive && hovered) resolvedStates += PixelControlState.Hovered
        if (activePane != null) resolvedStates += PixelControlState.Selected
        /** 硬阴影范围只解析一次，同时用于颜色判断与布局。 Hard-shadow extent resolved once for both color presence and layout. */
        val elevation = tokens.resolveElevation(theme.elevations)
        /** 由组件与 foundation token 解析出的完整具体表面。 Complete concrete surface derived from component and foundation tokens. */
        val rowSurface = PixelSurface(
            decoration = PixelSurfaceDecoration(
                fillColor = tokens.resolveContainerColor(resolvedStates, theme.colors),
                borderColor = tokens.resolveBorderColor(resolvedStates, theme.colors),
                borderWidth = tokens.resolveBorderWidth(theme.borders),
                cornerRadius = tokens.resolveCornerRadius(theme.radii),
                shadowColor = theme.colors.shadow.takeIf { elevation > 0 },
                shadowOffset = elevation,
            ),
            padding = tokens.resolvePadding(theme.spacing),
            child = widget.child,
            key = widget.key?.let { "$it-surface" },
        )
        /** 叠加式焦点层保留 selected/error/loading/pressed 的底色。 Additive focus layer preserves selected, error, loading, and pressed base colors. */
        val rowChild = withControlFocusIndicator(
            child = rowSurface,
            states = resolvedStates,
            componentTokens = tokens,
            colors = theme.colors,
            borders = theme.borders,
            key = widget.key?.let { "$it-focus-indicator" },
        )
        /** Render presentation beneath the optional pointer target. */
        val renderContent = SlidableRenderWidget(
            startPane = widget.startActionPane?.toWidget(
                direction = SlidableDirection.START,
                focusScope = startPaneFocusScope,
            ),
            endPane = widget.endActionPane?.toWidget(
                direction = SlidableDirection.END,
                focusScope = endPaneFocusScope,
            ),
            child = rowChild,
            offsetPx = visualOffset,
            activePane = activePane,
            startExtentRatio = widget.startActionPane?.extentRatio ?: 0f,
            endExtentRatio = widget.endActionPane?.extentRatio ?: 0f,
            startMotion = widget.startActionPane?.motion ?: SlidableMotion.BEHIND,
            endMotion = widget.endActionPane?.motion ?: SlidableMotion.BEHIND,
            onLayoutWidthChanged = { laidOutWidth ->
                // Render layout is authoritative when the row is narrower than its screen.
                contentWidthPx = laidOutWidth.coerceAtLeast(1)
            },
        )
        /** Pointer and render subtree omitted as a target while Disabled or Loading. */
        val pointerContent = if (interactive) GestureDetectorWidget(
            onTap = { widget.onTap?.invoke() },
            onLongPress = null,
            onDoubleTap = null,
            onSwipeStart = {
                // A new pointer owns the visual immediately and cancels any pending dismiss.
                dragBaseOffsetPx = visualOffset
                cancelSettle(commitVisualOffset = true)
                pendingDismissDirection = null
            },
            onSwipeUpdate = { delta ->
                setState {
                    /** Clamped direct-manipulation position used by both paint and pane availability. */
                    val nextOffset = clampDragOffset(dragBaseOffsetPx + delta)
                    updateActivePane(directionForOffset(nextOffset))
                    offsetPx = nextOffset
                }
            },
            onSwipeEnd = {
                setState { settleOffset(context) }
            },
            onSwipeLeft = null,
            onSwipeRight = null,
            onPressedChanged = ::updatePressed,
            onHoveredChanged = ::updateHovered,
            child = renderContent,
            key = widget.key?.let { "$it:gesture" },
        ) else renderContent
        /** Stable name preserving blank explicit labels before provider, theme, and English layers. */
        val resolvedLabel = widget.semanticLabel
            ?: localizations?.labels?.slidable
            ?: inheritedTheme?.labels?.slidable
            ?: PixelLabelTokens.Default.slidable
        /** Loading status resolved independently from row geometry and color selection. */
        val resolvedLoadingLabel = localizations?.labels?.loading
            ?: inheritedTheme?.labels?.loading
            ?: PixelLabelTokens.Default.loading
        /** Error status resolved independently from row geometry and color selection. */
        val resolvedErrorLabel = localizations?.labels?.error
            ?: inheritedTheme?.labels?.error
            ?: PixelLabelTokens.Default.error
        /** Row click semantics shares the same current capability as pointer and keyboard input. */
        val semanticActivate: (() -> Boolean)? = widget.onTap?.takeIf { interactive }?.let { callback ->
            {
                callback()
                true
            }
        }
        /** Standard expand action opens the preferred available pane for accessibility services. */
        val semanticExpand: (() -> Boolean)? = if (interactive && activePane == null) {
            ::openPreferredPaneFromSemantics
        } else {
            null
        }
        /** Standard collapse action closes whichever pane is currently exposed. */
        val semanticCollapse: (() -> Boolean)? = if (interactive && activePane != null) {
            ::closePaneFromSemantics
        } else {
            null
        }
        /** Standard dismiss action is present only for an open, dismissible, callback-owned pane. */
        val semanticDismiss: (() -> Boolean)? = activePane?.takeIf { direction ->
            interactive &&
                paneFor(direction)?.dismissible == true &&
                widget.onDismissed != null
        }?.let { direction ->
            { dismissPaneFromSemantics(direction) }
        }
        /** Structured row semantics retains descendant action-pane nodes. */
        val semanticContent = Semantics(
            label = resolvedLabel,
            role = PixelSemanticRole.BUTTON,
            enabled = interactive,
            focused = rowFocusNode.isFocused,
            selected = activePane != null,
            expanded = activePane != null,
            value = resolvedLoadingLabel.takeIf { loading },
            error = resolvedErrorLabel.takeIf { PixelControlState.Error in resolvedStates },
            actions = PixelSemanticsActions(
                onClick = semanticActivate,
                onDismiss = semanticDismiss,
                onExpand = semanticExpand,
                onCollapse = semanticCollapse,
            ),
            child = pointerContent,
            key = widget.key?.let { "$it-semantics" },
        )
        return AutomaticFocusAction(
            enabled = rowFocusable,
            focusNode = rowFocusNode,
            debugLabel = resolvedLabel,
            onKeyEvent = if (interactive) ::handleRowKeyEvent else null,
            key = widget.key,
        ) { _, _ ->
            semanticContent
        }
    }

    /** Updates captured press state once per row pointer ownership transition. */
    private fun updatePressed(nextPressed: Boolean) {
        if (pressed == nextPressed) return
        setState { pressed = nextPressed }
    }

    /** Updates hover state once per mouse or stylus boundary transition. */
    private fun updateHovered(nextHovered: Boolean) {
        if (hovered == nextHovered) return
        setState { hovered = nextHovered }
    }

    /** Routes row activation, pane opening/closing, and first Tab entry into the active pane. */
    private fun handleRowKeyEvent(event: PixelKeyEvent): Boolean {
        return when (event.key) {
            PixelKey.ENTER,
            PixelKey.SPACE,
            -> {
                /** Current controlled row activation callback, absent for swipe-only rows. */
                val callback = widget.onTap ?: return false
                callback()
                true
            }
            PixelKey.ARROW_LEFT -> handleLeftKey()
            PixelKey.ARROW_RIGHT -> handleRightKey()
            PixelKey.TAB -> activePaneFocusScope()?.focusInDirection(PixelFocusDirection.NEXT) == true
            PixelKey.SHIFT_TAB -> activePaneFocusScope()?.focusInDirection(PixelFocusDirection.PREVIOUS) == true
            else -> false
        }
    }

    /** Opens the end pane from closed state or closes an already-open start pane. */
    private fun handleLeftKey(): Boolean {
        return when (activePane) {
            SlidableDirection.START -> closePaneFromKeyboard()
            SlidableDirection.END -> true
            null -> openPaneFromKeyboard(SlidableDirection.END)
        }
    }

    /** Opens the start pane from closed state or closes an already-open end pane. */
    private fun handleRightKey(): Boolean {
        return when (activePane) {
            SlidableDirection.END -> closePaneFromKeyboard()
            SlidableDirection.START -> true
            null -> openPaneFromKeyboard(SlidableDirection.START)
        }
    }

    /** Animates from the current visual position to the requested pane's exact snap endpoint. */
    private fun openPaneFromKeyboard(direction: SlidableDirection): Boolean {
        /** Pane configuration required to calculate the keyboard snap endpoint. */
        val pane = paneFor(direction) ?: return false
        /** Signed endpoint matching the same extent calculation used by pointer settling. */
        val targetOffset = paneWidth(pane) * if (direction == SlidableDirection.START) 1 else -1
        updateActivePane(direction)
        startSettle(
            context = context,
            fromOffsetPx = currentVisualOffsetPx(),
            targetOffsetPx = targetOffset,
        )
        setState { Unit }
        return true
    }

    /** Starts a logical close immediately so hidden actions lose focus before exit paint completes. */
    private fun closePaneFromKeyboard(): Boolean {
        if (activePane == null) return false
        updateActivePane(nextPane = null)
        startSettle(
            context = context,
            fromOffsetPx = currentVisualOffsetPx(),
            targetOffsetPx = 0,
        )
        setState { Unit }
        return true
    }

    /** Opens the logical end pane when available, otherwise the start pane, via semantics. */
    private fun openPreferredPaneFromSemantics(): Boolean {
        /** Deterministic accessibility default matching the common trailing-action convention. */
        val direction = when {
            widget.endActionPane != null -> SlidableDirection.END
            widget.startActionPane != null -> SlidableDirection.START
            else -> return false
        }
        return openPaneFromKeyboard(direction)
    }

    /** Closes the active pane through the same logical transition used by keyboard input. */
    private fun closePaneFromSemantics(): Boolean = closePaneFromKeyboard()

    /** Moves an open dismissible pane to its full-width endpoint and schedules one callback. */
    private fun dismissPaneFromSemantics(direction: SlidableDirection): Boolean {
        /** Current pane must still be dismissible when the accessibility request executes. */
        val pane = paneFor(direction) ?: return false
        if (!pane.dismissible || widget.onDismissed == null || activePane != direction) return false
        /** Signed full-row endpoint shared with a successful dismissing pointer release. */
        val targetOffset = contentWidthPx * if (direction == SlidableDirection.START) 1 else -1
        pendingDismissDirection = direction
        updateActivePane(nextPane = null)
        startSettle(
            context = context,
            fromOffsetPx = currentVisualOffsetPx(),
            targetOffsetPx = targetOffset,
        )
        setState { Unit }
        return true
    }

    /** Returns the declarative pane configuration for one logical direction. */
    private fun paneFor(direction: SlidableDirection): SlidableActionPane? {
        return when (direction) {
            SlidableDirection.START -> widget.startActionPane
            SlidableDirection.END -> widget.endActionPane
        }
    }

    /** Returns the currently unblocked pane traversal scope, if a pane is logically open. */
    private fun activePaneFocusScope(): FocusScopeNode? {
        return when (activePane) {
            SlidableDirection.START -> startPaneFocusScope
            SlidableDirection.END -> endPaneFocusScope
            null -> null
        }
    }

    /** Converts a signed direct-manipulation offset into its logically exposed pane. */
    private fun directionForOffset(offset: Int): SlidableDirection? {
        return when {
            offset > 0 -> SlidableDirection.START
            offset < 0 -> SlidableDirection.END
            else -> null
        }
    }

    /** Applies pane focus blocking and restores the row only when a closing pane owned focus. */
    private fun updateActivePane(nextPane: SlidableDirection?) {
        if (activePane == nextPane) return
        /** Closing restores the row only when focus would otherwise be cleared with the pane. */
        val shouldRestoreRow = nextPane == null && paneOwnsPrimaryFocus()
        activePane = nextPane
        startPaneFocusScope.setFocusBlocked(nextPane != SlidableDirection.START)
        endPaneFocusScope.setFocusBlocked(nextPane != SlidableDirection.END)
        if (shouldRestoreRow && rowFocusNode.canRequestFocus) rowFocusNode.requestFocus()
    }

    /** Returns whether either pane scope currently contains this runtime's primary focus. */
    private fun paneOwnsPrimaryFocus(): Boolean {
        return startPaneFocusScope.containsPrimaryFocus() || endPaneFocusScope.containsPrimaryFocus()
    }

    /** Clamps a direct drag while allowing dismissible panes to follow the pointer off-screen. */
    private fun clampDragOffset(value: Int): Int {
        val startWidth = widget.startActionPane?.let { pane ->
            if (pane.dismissible) contentWidthPx else paneWidth(pane)
        } ?: 0
        val endWidth = widget.endActionPane?.let { pane ->
            if (pane.dismissible) contentWidthPx else paneWidth(pane)
        } ?: 0
        return value.coerceIn(-endWidth, startWidth)
    }

    /** Selects the closed, opened-pane, or full-dismiss endpoint for the released drag. */
    private fun settleOffset(context: BuildContext) {
        val visualOffset = currentVisualOffsetPx()
        val direction = when {
            visualOffset > 0 -> SlidableDirection.START
            visualOffset < 0 -> SlidableDirection.END
            else -> {
                updateActivePane(nextPane = null)
                return
            }
        }
        val pane = when (direction) {
            SlidableDirection.START -> widget.startActionPane
            SlidableDirection.END -> widget.endActionPane
        } ?: return
        val paneWidth = paneWidth(pane)
        val thresholdPx = (paneWidth * pane.dismissThreshold).toInt().coerceAtLeast(1)
        val shouldDismiss = pane.dismissible &&
            widget.onDismissed != null &&
            abs(visualOffset) >= thresholdPx
        val targetOffset = when {
            shouldDismiss && direction == SlidableDirection.START -> contentWidthPx
            shouldDismiss -> -contentWidthPx
            abs(visualOffset) >= thresholdPx && direction == SlidableDirection.START -> paneWidth
            abs(visualOffset) >= thresholdPx -> -paneWidth
            else -> 0
        }
        pendingDismissDirection = direction.takeIf { shouldDismiss }
        updateActivePane(
            nextPane = direction.takeIf { targetOffset != 0 && !shouldDismiss },
        )
        startSettle(
            context = context,
            fromOffsetPx = visualOffset,
            targetOffsetPx = targetOffset,
        )
    }

    /** Converts one pane description into its equal-width action row. */
    private fun SlidableActionPane.toWidget(
        direction: SlidableDirection,
        focusScope: FocusScopeNode,
    ): Widget {
        /** Stable suffix prevents the two retained pane scopes from exchanging descendants. */
        val directionSuffix = direction.name.lowercase()
        return FocusScope(
            node = focusScope,
            child = Row(
                spacing = 0,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                children = children.map { action -> Expanded(child = action) },
            ),
            key = widget.key?.let { "$it:$directionSuffix-pane-focus" },
        )
    }

    /** Calculates this pane's snap width from the current logical row width. */
    private fun paneWidth(pane: SlidableActionPane): Int {
        return (contentWidthPx * pane.extentRatio.coerceIn(0.1f, 1f)).toInt().coerceAtLeast(1)
    }

    /** Starts a Host-owned settle animation or applies its terminal state synchronously. */
    private fun startSettle(
        context: BuildContext,
        fromOffsetPx: Int,
        targetOffsetPx: Int,
    ) {
        cancelSettle(commitVisualOffset = false)
        settleStartOffsetPx = fromOffsetPx
        settleTargetOffsetPx = targetOffsetPx
        offsetPx = fromOffsetPx
        if (fromOffsetPx == targetOffsetPx) {
            completeSettle(targetOffsetPx)
            return
        }
        val environment = resolveSettleEnvironment(context)
        applySettleMotion(
            environment = environment,
            fromOffsetPx = fromOffsetPx,
            targetOffsetPx = targetOffsetPx,
            requestRebuildOnImmediate = true,
        )
    }

    /** Applies one resolved settle policy while preserving the selected terminal endpoint. */
    private fun applySettleMotion(
        environment: SlidableSettleEnvironment,
        fromOffsetPx: Int,
        targetOffsetPx: Int,
        requestRebuildOnImmediate: Boolean,
    ) {
        val resolved = environment.resolved
        val totalDuration = resolved.delay + resolved.duration
        if (
            environment.vsync == null ||
            environment.settings.reduceMotion ||
            resolved.isImmediate ||
            resolved.transition == PixelMotionTransitionPreset.None ||
            totalDuration == Duration.ZERO
        ) {
            completeSettle(
                targetOffsetPx = targetOffsetPx,
                requestRebuild = requestRebuildOnImmediate,
            )
            return
        }
        val controller = PixelAnimationController(
            duration = totalDuration,
            vsync = environment.vsync,
        )
        settleController = controller
        settleEnvironment = environment
        settleCurve = delayedMotionCurve(
            delay = resolved.delay,
            duration = resolved.duration,
            curve = resolved.spring?.let { spring ->
                springMotionCurve(spring = spring, duration = resolved.duration)
            } ?: resolved.curve,
        )
        controller.addListener {
            if (
                settleController === controller &&
                controller.status == PixelAnimationStatus.Completed
            ) {
                completeSettle(targetOffsetPx = targetOffsetPx)
            }
        }
        controller.forward(from = 0f)
    }

    /** Commits an exact settle endpoint and delivers a pending dismiss callback once. */
    private fun completeSettle(
        targetOffsetPx: Int,
        requestRebuild: Boolean = true,
    ) {
        offsetPx = targetOffsetPx
        settleController?.dispose()
        settleController = null
        settleCurve = null
        settleEnvironment = null
        if (targetOffsetPx == 0 || pendingDismissDirection != null) {
            updateActivePane(nextPane = null)
        }
        val dismissDirection = pendingDismissDirection
        pendingDismissDirection = null
        if (requestRebuild) setState { Unit }
        dismissDirection?.let { direction -> widget.onDismissed?.invoke(direction) }
    }

    /** Stops active settling, optionally retaining the currently sampled visual offset. */
    private fun cancelSettle(commitVisualOffset: Boolean) {
        val visualOffset = currentVisualOffsetPx()
        settleController?.dispose()
        settleController = null
        settleCurve = null
        settleEnvironment = null
        if (commitVisualOffset) offsetPx = visualOffset
    }

    /** Retargets an active settle when theme tokens or Host motion preferences change. */
    private fun reconcileSettleMotion(nextEnvironment: SlidableSettleEnvironment) {
        val controller = settleController ?: return
        val previousEnvironment = settleEnvironment ?: return
        if (
            previousEnvironment.vsync === nextEnvironment.vsync &&
            previousEnvironment.settings == nextEnvironment.settings &&
            previousEnvironment.resolved == nextEnvironment.resolved
        ) {
            return
        }
        val visualOffset = currentVisualOffsetPx()
        val targetOffset = settleTargetOffsetPx
        controller.dispose()
        settleController = null
        settleCurve = null
        settleEnvironment = null
        settleStartOffsetPx = visualOffset
        offsetPx = visualOffset
        applySettleMotion(
            environment = nextEnvironment,
            fromOffsetPx = visualOffset,
            targetOffsetPx = targetOffset,
            // This method runs inside build, which already presents the resulting terminal value.
            requestRebuildOnImmediate = false,
        )
    }

    /** Resolves the inherited clock, accessibility settings, and current Slidable motion token. */
    private fun resolveSettleEnvironment(context: BuildContext): SlidableSettleEnvironment {
        val scope = PixelMotionScope.maybeOf(context)
        val settings = scope?.settings ?: PixelMotionSettings.Default
        return SlidableSettleEnvironment(
            vsync = scope?.vsync,
            settings = settings,
            resolved = PixelMotionTheme.of(context).slidableSettle.resolve(settings),
        )
    }

    /** Samples the exact direct-drag value or the interpolated settle presentation. */
    private fun currentVisualOffsetPx(): Int {
        val controller = settleController ?: return offsetPx
        val progress = (settleCurve ?: return offsetPx)
            .transform(controller.value.coerceIn(0f, 1f))
            .coerceIn(0f, 1f)
        return (
            settleStartOffsetPx +
                (settleTargetOffsetPx - settleStartOffsetPx) * progress
            ).roundToInt()
    }

    /** Releases the component-owned ticker without affecting the Host-owned provider. */
    override fun dispose() {
        cancelSettle(commitVisualOffset = false)
    }
}

/** Inherited motion inputs captured for one active Slidable settle segment. */
private data class SlidableSettleEnvironment(
    /** Host-owned ticker provider, or `null` when the component is rendered without a Host. */
    val vsync: PixelTickerProvider?,
    /** Current accessibility and duration-scale policy. */
    val settings: PixelMotionSettings,
    /** Fully resolved theme token used by the settle segment. */
    val resolved: PixelResolvedMotion,
)

/** Converts an optional physical spring token into a normalized, duration-bounded curve. */
private fun springMotionCurve(
    spring: PixelSpringSpec,
    duration: Duration,
): Curve {
    val durationSeconds = duration.inWholeNanoseconds.toDouble() / NanosecondsPerSecond
    if (!durationSeconds.isFinite() || durationSeconds <= 0.0) {
        return Curve { progress -> progress.coerceIn(0f, 1f) }
    }
    val terminalResponse = springStepResponse(spring = spring, elapsedSeconds = durationSeconds)
    if (!terminalResponse.isFinite() || abs(terminalResponse) < MinimumSpringResponse) {
        return Curve { progress -> progress.coerceIn(0f, 1f) }
    }
    return Curve { progress ->
        when {
            progress <= 0f -> 0f
            progress >= 1f -> 1f
            else -> {
                val elapsedSeconds = durationSeconds * progress.toDouble()
                (springStepResponse(spring, elapsedSeconds) / terminalResponse)
                    .toFloat()
                    .coerceIn(0f, 1f)
            }
        }
    }
}

/** Evaluates the unit-step response of one damped second-order spring. */
private fun springStepResponse(
    spring: PixelSpringSpec,
    elapsedSeconds: Double,
): Double {
    val naturalFrequency = sqrt(spring.stiffness.toDouble() / spring.mass.toDouble())
    val dampingRatio = spring.dampingRatio.toDouble()
    val time = elapsedSeconds.coerceAtLeast(0.0)
    return when {
        dampingRatio < 1.0 - CriticalDampingTolerance -> {
            val dampedFactor = sqrt(1.0 - dampingRatio * dampingRatio)
            val dampedFrequency = naturalFrequency * dampedFactor
            val envelope = exp(-dampingRatio * naturalFrequency * time)
            1.0 - envelope * (
                cos(dampedFrequency * time) +
                    dampingRatio / dampedFactor * sin(dampedFrequency * time)
                )
        }
        dampingRatio > 1.0 + CriticalDampingTolerance -> {
            val rootFactor = sqrt(dampingRatio * dampingRatio - 1.0)
            val slowRoot = -naturalFrequency * (dampingRatio - rootFactor)
            val fastRoot = -naturalFrequency * (dampingRatio + rootFactor)
            1.0 + (
                fastRoot * exp(slowRoot * time) -
                    slowRoot * exp(fastRoot * time)
                ) / (slowRoot - fastRoot)
        }
        else -> {
            val frequencyTime = naturalFrequency * time
            1.0 - exp(-frequencyTime) * (1.0 + frequencyTime)
        }
    }
}

/** Number of nanoseconds in one SI second. */
private const val NanosecondsPerSecond: Double = 1_000_000_000.0

/** Numerical boundary used to select the stable critical-damping formula. */
private const val CriticalDampingTolerance: Double = 1e-4

/** Response magnitude below which normalization would amplify floating-point noise. */
private const val MinimumSpringResponse: Double = 1e-9

/** Builds a curve whose normalized prefix represents a theme-configured delay. */
private fun delayedMotionCurve(
    delay: Duration,
    duration: Duration,
    curve: Curve,
): Curve {
    val total = delay + duration
    if (delay == Duration.ZERO) return curve
    if (duration == Duration.ZERO) return Curve { progress -> if (progress >= 1f) 1f else 0f }
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

private class SlidableRenderWidget(
    private val startPane: Widget?,
    private val endPane: Widget?,
    private val child: Widget,
    private val offsetPx: Int,
    /** Logically interactive pane, independent from a retained opening or closing visual offset. */
    private val activePane: SlidableDirection?,
    private val startExtentRatio: Float,
    private val endExtentRatio: Float,
    private val startMotion: SlidableMotion,
    private val endMotion: SlidableMotion,
    /** Reports the exact constrained width back to the gesture state after layout. */
    private val onLayoutWidthChanged: (Int) -> Unit,
) : MultiChildRenderObjectWidget(
    children = listOfNotNull(startPane, endPane, child),
) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderSlidable(
            hasStartPane = startPane != null,
            hasEndPane = endPane != null,
            offsetPx = offsetPx,
            activePane = activePane,
            startExtentRatio = startExtentRatio,
            endExtentRatio = endExtentRatio,
            startMotion = startMotion,
            endMotion = endMotion,
            onLayoutWidthChanged = onLayoutWidthChanged,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderSlidable).update(
            hasStartPane = startPane != null,
            hasEndPane = endPane != null,
            offsetPx = offsetPx,
            activePane = activePane,
            startExtentRatio = startExtentRatio,
            endExtentRatio = endExtentRatio,
            startMotion = startMotion,
            endMotion = endMotion,
            onLayoutWidthChanged = onLayoutWidthChanged,
        )
    }
}

private class RenderSlidable(
    private var hasStartPane: Boolean,
    private var hasEndPane: Boolean,
    private var offsetPx: Int,
    /** Pane allowed to export hit, action, semantics, and focus-adjacent interaction targets. */
    private var activePane: SlidableDirection?,
    private var startExtentRatio: Float,
    private var endExtentRatio: Float,
    private var startMotion: SlidableMotion,
    private var endMotion: SlidableMotion,
    /** Callback owned by the retained State that consumes the authoritative laid-out width. */
    private var onLayoutWidthChanged: (Int) -> Unit,
) : MultiChildRenderObject() {
    /** Last width reported to State, avoiding redundant mutation on paint-only frames. */
    private var reportedLayoutWidthPx: Int = -1

    private var paneWidthPx = 0

    fun update(
        hasStartPane: Boolean,
        hasEndPane: Boolean,
        offsetPx: Int,
        activePane: SlidableDirection?,
        startExtentRatio: Float,
        endExtentRatio: Float,
        startMotion: SlidableMotion,
        endMotion: SlidableMotion,
        onLayoutWidthChanged: (Int) -> Unit,
    ) {
        this.onLayoutWidthChanged = onLayoutWidthChanged
        if (
            this.hasStartPane == hasStartPane &&
            this.hasEndPane == hasEndPane &&
            this.offsetPx == offsetPx &&
            this.activePane == activePane &&
            this.startExtentRatio == startExtentRatio &&
            this.endExtentRatio == endExtentRatio &&
            this.startMotion == startMotion &&
            this.endMotion == endMotion
        ) {
            return
        }
        this.hasStartPane = hasStartPane
        this.hasEndPane = hasEndPane
        this.offsetPx = offsetPx
        this.activePane = activePane
        this.startExtentRatio = startExtentRatio
        this.endExtentRatio = endExtentRatio
        this.startMotion = startMotion
        this.endMotion = endMotion
        markNeedsLayout()
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        val child = childBox ?: run {
            size = RenderSize.Zero
            return
        }
        val width = constraints.maxWidth.coerceAtLeast(0)
        child.layout(
            RenderConstraints(
                minWidth = width,
                maxWidth = width,
                minHeight = 0,
                maxHeight = constraints.maxHeight,
            ),
        )
        size = RenderSize(
            width = constraints.constrainWidth(width),
            height = constraints.constrainHeight(child.size.height),
        )
        if (reportedLayoutWidthPx != size.width) {
            reportedLayoutWidthPx = size.width
            onLayoutWidthChanged(size.width)
        }
        paneWidthPx = paneWidth()
        val paneConstraints = RenderConstraints(
            minWidth = paneWidthPx,
            maxWidth = paneWidthPx,
            minHeight = size.height,
            maxHeight = size.height,
        )
        startPaneBox?.layout(paneConstraints)
        endPaneBox?.layout(paneConstraints)
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val child = childBox ?: return
        val scratch = context.bufferPool.acquire(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1))
        scratch.clear()
        try {
            val scratchContext = context.derive(scratch, offsetX, offsetY)
            if (offsetPx > 0) {
                startPaneBox?.paint(scratchContext, startPaneX(), 0)
                scratch.fillRect(
                    offsetPx,
                    0,
                    size.width - offsetPx,
                    size.height,
                    PixelColor.Transparent,
                    com.purride.pixelcore.PixelBlendMode.Clear,
                )
            } else if (offsetPx < 0) {
                endPaneBox?.paint(scratchContext, endPaneX(), 0)
                scratch.fillRect(
                    0,
                    0,
                    size.width + offsetPx,
                    size.height,
                    PixelColor.Transparent,
                    com.purride.pixelcore.PixelBlendMode.Clear,
                )
            }
            child.paint(scratchContext, offsetPx, 0)
            context.buffer.blitRegion(scratch, 0, 0, size.width, size.height, offsetX, offsetY)
        } finally {
            context.bufferPool.release(scratch)
        }
    }

    override fun hitTest(localX: Int, localY: Int, result: com.purride.pixelui.internal.HitTestResult) {
        if (localX !in 0 until size.width || localY !in 0 until size.height) return
        if (
            activePane == SlidableDirection.START &&
            offsetPx > 0 &&
            localX < offsetPx.coerceAtMost(size.width)
        ) {
            startPaneBox?.hitTest(localX - startPaneX(), localY, result)
        } else if (
            activePane == SlidableDirection.END &&
            offsetPx < 0 &&
            localX >= (size.width + offsetPx).coerceAtLeast(0)
        ) {
            endPaneBox?.hitTest(localX - endPaneX(), localY, result)
        }
        childBox?.hitTest(localX - offsetPx, localY, result)
    }

    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        collectPaneTargets(offsetX, offsetY, targets)
        collectChildClickTargets(offsetX, offsetY, targets)
    }

    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) {
        childBox?.collectPagerTargets(offsetX + offsetPx, offsetY, targets)
    }

    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) {
        childBox?.collectListTargets(offsetX + offsetPx, offsetY, targets)
    }

    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>) {
        childBox?.collectScrollbarTargets(offsetX + offsetPx, offsetY, targets)
    }

    override fun collectRefreshTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelRefreshTarget>) {
        childBox?.collectRefreshTargets(offsetX + offsetPx, offsetY, targets)
    }

    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>) {
        childBox?.collectTextInputTargets(offsetX + offsetPx, offsetY, targets)
    }

    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) {
        childBox?.collectSliderTargets(offsetX + offsetPx, offsetY, targets)
    }

    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        collectPaneSemantics(offsetX, offsetY, targets)
        childBox?.collectSemantics(offsetX + offsetPx, offsetY, targets)
    }

    private fun collectPaneTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        val paneTargets = mutableListOf<PixelClickTarget>()
        val exposedBounds = when (activePane) {
            SlidableDirection.START -> {
                if (offsetPx <= 0) return
                startPaneBox?.collectClickTargets(offsetX + startPaneX(), offsetY, paneTargets)
                PixelRect(
                    left = offsetX,
                    top = offsetY,
                    width = offsetPx.coerceAtMost(size.width),
                    height = size.height,
                )
            }
            SlidableDirection.END -> {
                if (offsetPx >= 0) return
                endPaneBox?.collectClickTargets(offsetX + endPaneX(), offsetY, paneTargets)
                val exposedLeft = (size.width + offsetPx).coerceAtLeast(0)
                PixelRect(
                    left = offsetX + exposedLeft,
                    top = offsetY,
                    width = size.width - exposedLeft,
                    height = size.height,
                )
            }
            null -> return
        }
        paneTargets.forEach { target ->
            target.bounds.intersect(exposedBounds)?.let { clippedBounds ->
                targets += target.copy(bounds = clippedBounds)
            }
        }
    }

    /** Exports only the logically active pane's semantics and clips every node to exposed pixels. */
    private fun collectPaneSemantics(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelSemanticsTarget>,
    ) {
        /** Temporary pane-local collection prevents hidden or covered nodes from escaping. */
        val paneTargets = mutableListOf<PixelSemanticsTarget>()
        /** Exact visible strip shared by every semantic descendant in the active pane. */
        val exposedBounds = when (activePane) {
            SlidableDirection.START -> {
                if (offsetPx <= 0) return
                startPaneBox?.collectSemantics(offsetX + startPaneX(), offsetY, paneTargets)
                PixelRect(
                    left = offsetX,
                    top = offsetY,
                    width = offsetPx.coerceAtMost(size.width),
                    height = size.height,
                )
            }
            SlidableDirection.END -> {
                if (offsetPx >= 0) return
                endPaneBox?.collectSemantics(offsetX + endPaneX(), offsetY, paneTargets)
                val exposedLeft = (size.width + offsetPx).coerceAtLeast(0)
                PixelRect(
                    left = offsetX + exposedLeft,
                    top = offsetY,
                    width = size.width - exposedLeft,
                    height = size.height,
                )
            }
            null -> return
        }
        paneTargets.forEach { target ->
            /** Original semantic geometry before clipping to the visible pane strip. */
            val node = target.node
            /** Global node bounds used for exact intersection with the Slidable viewport. */
            val nodeBounds = PixelRect(node.left, node.top, node.width, node.height)
            nodeBounds.intersect(exposedBounds)?.let { clippedBounds ->
                targets += target.copy(
                    node = node.copy(
                        left = clippedBounds.left,
                        top = clippedBounds.top,
                        width = clippedBounds.width,
                        height = clippedBounds.height,
                    ),
                )
            }
        }
    }

    /** Clips translated content click targets to this row's viewport. */
    private fun collectChildClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ) {
        val childTargets = mutableListOf<PixelClickTarget>()
        childBox?.collectClickTargets(offsetX + offsetPx, offsetY, childTargets)
        val viewportBounds = PixelRect(
            left = offsetX,
            top = offsetY,
            width = size.width,
            height = size.height,
        )
        childTargets.forEach { target ->
            target.bounds.intersect(viewportBounds)?.let { clippedBounds ->
                targets += target.copy(bounds = clippedBounds)
            }
        }
    }

    private fun paneWidth(): Int {
        val ratio = when {
            activePane == SlidableDirection.START -> startExtentRatio
            activePane == SlidableDirection.END -> endExtentRatio
            offsetPx > 0 -> startExtentRatio
            offsetPx < 0 -> endExtentRatio
            hasEndPane -> endExtentRatio
            else -> startExtentRatio
        }
        return (size.width * ratio.coerceIn(0.1f, 1f)).toInt().coerceAtLeast(1)
    }

    private fun startPaneX(): Int = when (startMotion) {
        SlidableMotion.BEHIND -> 0
        SlidableMotion.DRAWER -> (offsetPx - paneWidthPx) / 2
        SlidableMotion.SCROLL -> offsetPx - paneWidthPx
    }

    private fun endPaneX(): Int = when (endMotion) {
        SlidableMotion.BEHIND -> size.width - paneWidthPx
        SlidableMotion.DRAWER -> size.width + (offsetPx + paneWidthPx) / 2
        SlidableMotion.SCROLL -> size.width + offsetPx
    }

    private val startPaneBox: RenderBox?
        get() = if (hasStartPane) children.getOrNull(0) as? RenderBox else null

    private val endPaneBox: RenderBox?
        get() {
            val index = when {
                hasStartPane && hasEndPane -> 1
                !hasStartPane && hasEndPane -> 0
                else -> return null
            }
            return children.getOrNull(index) as? RenderBox
        }

    private val childBox: RenderBox?
        get() = children.getOrNull(contentChildIndex) as? RenderBox

    /** Index of the translated main child after the optional retained panes. */
    private val contentChildIndex: Int
        get() = paneChildCount

    /** Number of optional pane render children mounted before the main content. */
    private val paneChildCount: Int
        get() = (if (hasStartPane) 1 else 0) + (if (hasEndPane) 1 else 0)
}
