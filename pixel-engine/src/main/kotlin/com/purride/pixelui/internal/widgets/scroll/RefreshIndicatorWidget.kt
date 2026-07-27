package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.FocusNodeScope
import com.purride.pixelui.PixelControlColorMotion
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelLocalizations
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionTheme
import com.purride.pixelui.PixelMotionTransitionPreset
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsActions
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.Semantics
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.getInheritedWidgetOfExactType
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.state.PixelRefreshIndicatorState
import kotlin.time.Duration

/**
 * Retained pull-to-refresh component owning pointer feedback and theme-state transitions.
 *
 * Controlled pull/armed/refreshing state remains in [state]; this widget only merges it with
 * caller, pointer, and focus state before producing an immutable [RenderRefreshIndicator] frame.
 */
internal data class RefreshIndicatorWidget(
    /** Content receiving pull gestures and retaining its semantic descendants. */
    val child: Widget,
    /** Caller-owned pull distance and lifecycle phase. */
    val state: PixelRefreshIndicatorState,
    /** Controller observed for pull and refresh phase changes. */
    val controller: PixelRefreshIndicatorController,
    /** Persistent semantic states supplied by the public overload. */
    val states: PixelControlStateSet,
    /** Resolved trigger distance shared by pointer, keyboard, and semantics. */
    val thresholdPx: Int,
    /** Whether a mutable refresh target may be exported this frame. */
    val enabled: Boolean,
    /** Whether focus remains valid while Loading blocks mutation. */
    val focusable: Boolean,
    /** Optional concrete ordinary-pull foreground override. */
    val indicatorColor: PixelColor?,
    /** Optional concrete armed/Selected foreground override. */
    val armedColor: PixelColor?,
    /** Optional concrete refreshing/Loading foreground override. */
    val refreshingColor: PixelColor?,
    /** Shared keyboard and semantic lifecycle action. */
    val semanticAction: () -> Boolean,
    /** Business callback used only by a completed pointer pull. */
    val onRefresh: () -> Unit,
    /** Resolved non-blank semantic label. */
    val semanticLabel: String,
    /** Stable retained identity shared by semantics and the render leaf. */
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    /** Creates one independent hover/press and color-motion owner. */
    override fun createState(): State<out StatefulWidget> = RefreshIndicatorWidgetState()
}

/** Owns RefreshIndicator micro-state and shared feedback-color transitions. */
private class RefreshIndicatorWidgetState : State<RefreshIndicatorWidget>() {
    /** Whether an active vertical pull currently owns pressed feedback. */
    private var pressed: Boolean = false

    /** Whether a mouse or stylus currently owns refresh-boundary hover feedback. */
    private var hovered: Boolean = false

    /** Retained animated progress foreground initialized from the first resolved frame. */
    private var indicatorMotion: PixelControlColorMotion? = null

    /** Retained animated indicator track initialized from the first resolved frame. */
    private var trackMotion: PixelControlColorMotion? = null

    /** Retained animated indicator outline initialized from the first resolved frame. */
    private var borderMotion: PixelControlColorMotion? = null

    /** Releases every feedback ticker owned by this refresh boundary. */
    override fun dispose() {
        indicatorMotion?.dispose()
        trackMotion?.dispose()
        borderMotion?.dispose()
    }

    /** Resolves combined states, tokens, semantics, and the immutable render configuration. */
    override fun build(context: BuildContext): Widget {
        context.watch(widget.controller)
        /** Complete token graph from the nearest theme boundary. */
        val themeTokens = PixelTheme.of(context)
        /** 提供者标签只覆盖语义状态文本，不影响 token 解析。 Provider labels override semantic status text without affecting token resolution. */
        val localizedLabels = PixelLocalizations.maybeOf(context)?.labels ?: themeTokens.labels
        /** Refresh-specific role and geometry token family. */
        val componentTokens = themeTokens.components.refresh
        /** Effective node supplied by the public automatic-focus boundary. */
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) context.watch(focusNode)
        /** Disabled removes focus and always dominates every other state. */
        val disabled = !widget.focusable || PixelControlState.Disabled in widget.states
        /** Controlled refreshing and explicit Loading share one inert visual state. */
        val loading = widget.state.isRefreshing || PixelControlState.Loading in widget.states
        /** Armed pull is the refresh component's applicable Selected state. */
        val selected = widget.state.isArmed || PixelControlState.Selected in widget.states
        /** Final mutation gate after controlled and persistent state normalization. */
        val interactive = widget.enabled && !disabled && !loading
        if (!interactive) {
            pressed = false
            hovered = false
        }
        /** Focus survives Loading but is removed by Disabled. */
        val focused = !disabled && focusNode?.isFocused == true
        /** Pointer, focus, and persistent states merged before controlled phase flags. */
        var runtimeStates = mergeControlStates(
            persistent = widget.states,
            disabled = disabled,
            pressed = interactive && pressed,
            hovered = interactive && hovered,
            focused = focused,
        )
        if (selected) runtimeStates += PixelControlState.Selected
        if (loading) runtimeStates += PixelControlState.Loading
        /** Focus remains additive and cannot replace error/selected/loading base roles. */
        val baseStates = runtimeStates - PixelControlState.Focused
        /** 按阶段区分的具体覆写，保留公开三色 API。 Stage-specific concrete override preserving the public three-color API. */
        val explicitIndicatorColor = when {
            PixelControlState.Loading in baseStates -> widget.refreshingColor ?: widget.indicatorColor
            PixelControlState.Selected in baseStates -> widget.armedColor ?: widget.indicatorColor
            else -> widget.indicatorColor
        }
        /** Final progress foreground after explicit-over-token precedence. */
        val targetIndicatorColor = explicitIndicatorColor
            ?: componentTokens.resolveContentColor(baseStates, themeTokens.colors)
            ?: themeTokens.colors.primary
        /** Final track fill resolved independently from the progress foreground. */
        val targetTrackColor = componentTokens.resolveContainerColor(baseStates, themeTokens.colors)
            ?: themeTokens.colors.track
        /** Optional component outline role for custom token families. */
        val targetBorderColor = componentTokens.resolveBorderColor(baseStates, themeTokens.colors)
        /** Concrete transparent target used only by the retained border motion channel. */
        val concreteBorderColor = targetBorderColor ?: PixelColor.Transparent
        /** Foreground feedback channel retained across rapid state retargets. */
        val resolvedIndicatorMotion = indicatorMotion
            ?: PixelControlColorMotion(targetIndicatorColor).also { motion -> indicatorMotion = motion }
        /** Track feedback channel retained across rapid state retargets. */
        val resolvedTrackMotion = trackMotion
            ?: PixelControlColorMotion(targetTrackColor).also { motion -> trackMotion = motion }
        /** Outline feedback channel retained across rapid state retargets. */
        val resolvedBorderMotion = borderMotion
            ?: PixelControlColorMotion(concreteBorderColor).also { motion -> borderMotion = motion }
        /** Shared feedback motion role. */
        val feedbackSpec = PixelMotionTheme.of(context).feedback
        /** Optional Host-owned ticker and reduced-motion settings. */
        val motionScope = PixelMotionScope.maybeOf(context)
        /** Host-adjusted feedback policy, absent for immediate legacy trees. */
        val resolvedFeedback = motionScope?.let { scope -> feedbackSpec.resolve(scope.settings) }
        listOf(resolvedIndicatorMotion, resolvedTrackMotion, resolvedBorderMotion).forEach { motion ->
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
        if (interactive) {
            resolvedIndicatorMotion.animateTo(targetIndicatorColor)
            resolvedTrackMotion.animateTo(targetTrackColor)
            resolvedBorderMotion.animateTo(concreteBorderColor)
        } else {
            // Loading and Disabled synchronously release stale feedback tickers.
            resolvedIndicatorMotion.snapTo(targetIndicatorColor)
            resolvedTrackMotion.snapTo(targetTrackColor)
            resolvedBorderMotion.snapTo(concreteBorderColor)
        }
        listOf(resolvedIndicatorMotion, resolvedTrackMotion, resolvedBorderMotion).forEach { motion ->
            motion.watch(context)
        }
        /** Foundation-resolved indicator height, constrained by the render viewport at paint time. */
        val indicatorHeight = componentTokens.resolveMinimumHeight(themeTokens.sizes).coerceAtLeast(1)
        /** 由 foundation token 解析出的边框宽度。 Foundation-resolved outline width. */
        val borderWidth = componentTokens.resolveBorderWidth(themeTokens.borders)
        /** Foundation-resolved pixel stair-step radius. */
        val cornerRadius = componentTokens.resolveCornerRadius(themeTokens.radii)
        /** Render leaf exporting a pointer target only while mutation remains available. */
        val renderIndicator = RefreshIndicatorRenderWidget(
            child = widget.child,
            state = widget.state,
            controller = widget.controller,
            thresholdPx = widget.thresholdPx.coerceAtLeast(1),
            enabled = interactive,
            loading = loading,
            indicatorColor = resolvedIndicatorMotion.value,
            trackColor = resolvedTrackMotion.value,
            borderColor = resolvedBorderMotion.value.takeIf { targetBorderColor != null },
            borderWidth = borderWidth,
            cornerRadius = cornerRadius,
            indicatorHeight = indicatorHeight,
            onPressedChanged = ::updatePressed,
            onHoveredChanged = ::updateHovered,
            onRefresh = widget.onRefresh,
            key = widget.key,
        )
        /** Optional additive focus layer supplied by custom refresh component tokens. */
        val focusedIndicator = withControlFocusIndicator(
            child = renderIndicator,
            states = runtimeStates,
            componentTokens = componentTokens,
            colors = themeTokens.colors,
            borders = themeTokens.borders,
            key = widget.key?.let { "$it-focus-indicator" },
        )
        return Semantics(
            label = widget.semanticLabel,
            role = PixelSemanticRole.BUTTON,
            enabled = interactive,
            focused = focused,
            value = localizedLabels.loading.takeIf {
                PixelControlState.Loading in runtimeStates
            },
            error = localizedLabels.error.takeIf {
                PixelControlState.Error in runtimeStates
            },
            selected = selected,
            actions = PixelSemanticsActions(
                onClick = widget.semanticAction.takeIf { interactive },
            ),
            child = focusedIndicator,
            key = widget.key?.let { "$it-semantics" },
        )
    }

    /** Updates retained pressed state only while the current widget remains mutable. */
    private fun updatePressed(nextPressed: Boolean) {
        val allowedPressed = nextPressed && widget.enabled
        if (pressed == allowedPressed) return
        setState { pressed = allowedPressed }
    }

    /** Updates retained hover state only while the current widget remains mutable. */
    private fun updateHovered(nextHovered: Boolean) {
        val allowedHovered = nextHovered && widget.enabled
        if (hovered == allowedHovered) return
        setState { hovered = allowedHovered }
    }
}

/** Immutable render configuration produced by [RefreshIndicatorWidgetState]. */
private data class RefreshIndicatorRenderWidget(
    /** Child content rendered below the indicator. */
    override val child: Widget,
    /** Controlled refresh lifecycle state. */
    val state: PixelRefreshIndicatorState,
    /** Controller paired with [state]. */
    val controller: PixelRefreshIndicatorController,
    /** Shared positive trigger distance. */
    val thresholdPx: Int,
    /** Whether the render object may export a refresh target. */
    val enabled: Boolean,
    /** Whether an explicit or controlled Loading indicator should paint fully. */
    val loading: Boolean,
    /** Final animated progress foreground color. */
    val indicatorColor: PixelColor,
    /** Final animated full-width track color. */
    val trackColor: PixelColor,
    /** Optional final animated outline color. */
    val borderColor: PixelColor?,
    /** Resolved outline width. */
    val borderWidth: Int,
    /** Resolved pixel stair-step radius. */
    val cornerRadius: Int,
    /** Resolved maximum indicator height. */
    val indicatorHeight: Int,
    /** Retained press callback receiving Host and tester ownership changes. */
    val onPressedChanged: (Boolean) -> Unit,
    /** Retained hover callback receiving Host and tester ownership changes. */
    val onHoveredChanged: (Boolean) -> Unit,
    /** Business callback invoked after a successful pointer pull. */
    val onRefresh: () -> Unit,
    /** Stable render identity. */
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    /** Creates the refresh render object with the first resolved configuration. */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderRefreshIndicator(
            state = state,
            controller = controller,
            thresholdPx = thresholdPx,
            enabled = enabled,
            loading = loading,
            indicatorColor = indicatorColor,
            trackColor = trackColor,
            borderColor = borderColor,
            borderWidth = borderWidth,
            cornerRadius = cornerRadius,
            indicatorHeight = indicatorHeight,
            onPressedChanged = onPressedChanged,
            onHoveredChanged = onHoveredChanged,
            onRefresh = onRefresh,
        )
    }

    /** Updates the retained render object without replacing target source identity. */
    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderRefreshIndicator).updateRefreshIndicator(
            state = state,
            controller = controller,
            thresholdPx = thresholdPx,
            enabled = enabled,
            loading = loading,
            indicatorColor = indicatorColor,
            trackColor = trackColor,
            borderColor = borderColor,
            borderWidth = borderWidth,
            cornerRadius = cornerRadius,
            indicatorHeight = indicatorHeight,
            onPressedChanged = onPressedChanged,
            onHoveredChanged = onHoveredChanged,
            onRefresh = onRefresh,
        )
    }
}
