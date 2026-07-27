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
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.Semantics
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.getInheritedWidgetOfExactType
import com.purride.pixelui.state.PixelListState
import kotlin.time.Duration

/**
 * Retained themed scrollbar that merges caller, pointer, and focus states before rendering.
 *
 * The child remains the owner of list geometry and mutation. This wrapper owns only visual motion
 * and forwards interaction callbacks through [RenderScrollbar]'s target snapshot.
 */
internal data class ScrollbarWidget(
    /** Scrollable viewport rendered below the overlay track and thumb. */
    val child: Widget,
    /** List state shared with the viewport target discovered by [RenderScrollbar]. */
    val state: PixelListState,
    /** Persistent semantic states supplied by the public component overload. */
    val states: PixelControlStateSet,
    /** Optional concrete thumb color taking precedence over component roles. */
    val thumbColor: PixelColor?,
    /** Optional concrete track color taking precedence over component roles. */
    val trackColor: PixelColor?,
    /** Optional concrete width taking precedence over component geometry tokens. */
    val width: Int?,
    /** Whether this frame may export a mutable scrollbar target. */
    val enabled: Boolean,
    /** Optional spoken label taking precedence over the theme label token. */
    val semanticLabel: String?,
    /** Stable retained identity shared by semantics and the render leaf. */
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    /** Creates one independent pointer-feedback and color-motion owner. */
    override fun createState(): State<out StatefulWidget> = ScrollbarWidgetState()
}

/** Owns Scrollbar hover/press micro-state and shared feedback-color transitions. */
private class ScrollbarWidgetState : State<ScrollbarWidget>() {
    /** Whether the active pointer sequence currently owns the scrollbar drag target. */
    private var pressed: Boolean = false

    /** Whether a mouse or stylus currently owns hover feedback for this scrollbar. */
    private var hovered: Boolean = false

    /** Retained animated thumb color initialized from the first resolved frame. */
    private var thumbMotion: PixelControlColorMotion? = null

    /** Retained animated track color initialized from the first resolved frame. */
    private var trackMotion: PixelControlColorMotion? = null

    /** Retained animated border color initialized from the first resolved frame. */
    private var borderMotion: PixelControlColorMotion? = null

    /** Releases every feedback ticker owned by the scrollbar. */
    override fun dispose() {
        thumbMotion?.dispose()
        trackMotion?.dispose()
        borderMotion?.dispose()
    }

    /** Resolves theme roles, geometry, semantics, and the render-target callback snapshot. */
    override fun build(context: BuildContext): Widget {
        /** Complete token graph from the nearest theme boundary. */
        val themeTokens = PixelTheme.of(context)
        /** 提供者标签只覆盖语义文本，不影响视觉 token 解析。 Provider labels override semantic text without affecting visual token resolution. */
        val localizedLabels = PixelLocalizations.maybeOf(context)?.labels ?: themeTokens.labels
        /** Scrollbar-specific state-role and geometry tokens. */
        val componentTokens = themeTokens.components.scrollbar
        /** Optional focus node inherited from an explicit caller-owned ancestor. */
        val focusNode = context.getInheritedWidgetOfExactType<FocusNodeScope>()?.node
        if (focusNode != null) context.watch(focusNode)
        /** Disabled may originate from caller state or the normalized public enabled flag. */
        val disabled = PixelControlState.Disabled in widget.states
        /** Loading may be caller-controlled and always blocks mutation. */
        val loading = PixelControlState.Loading in widget.states
        /** Final mutation gate after persistent state normalization. */
        val interactive = widget.enabled && !disabled && !loading
        if (!interactive) {
            pressed = false
            hovered = false
        }
        /** Explicit ancestor focus is represented, but Scrollbar never creates a dead focus stop. */
        val focused = !disabled && focusNode?.isFocused == true
        /** Combined state set used by every color and semantic resolver. */
        val runtimeStates = mergeControlStates(
            persistent = widget.states,
            disabled = disabled,
            pressed = interactive && pressed,
            hovered = interactive && hovered,
            focused = focused,
        )
        /** Focus is an independent concept and must not displace the active base role. */
        val baseStates = runtimeStates - PixelControlState.Focused
        /** Concrete thumb target honoring the public override before semantic roles. */
        val targetThumbColor = widget.thumbColor
            ?: componentTokens.resolveContentColor(baseStates, themeTokens.colors)
            ?: themeTokens.colors.onSurface
        /** 轨道目标色；公开覆写优先于语义角色。 Concrete track target honoring the public override before semantic roles. */
        val concreteTrackColor = widget.trackColor
            ?: componentTokens.resolveContainerColor(baseStates, themeTokens.colors)
            ?: themeTokens.colors.track
        /** Optional state-aware border role resolved independently from track and thumb. */
        val targetBorderColor = componentTokens.resolveBorderColor(baseStates, themeTokens.colors)
        /** Transparent motion target used only when the theme intentionally omits a border. */
        val concreteBorderColor = targetBorderColor ?: PixelColor.Transparent
        /** Thumb feedback channel retained across rapid state retargets. */
        val resolvedThumbMotion = thumbMotion
            ?: PixelControlColorMotion(targetThumbColor).also { motion -> thumbMotion = motion }
        /** Track feedback channel retained across rapid state retargets. */
        val resolvedTrackMotion = trackMotion
            ?: PixelControlColorMotion(concreteTrackColor).also { motion -> trackMotion = motion }
        /** Border feedback channel retained across rapid state retargets. */
        val resolvedBorderMotion = borderMotion
            ?: PixelControlColorMotion(concreteBorderColor).also { motion -> borderMotion = motion }
        /** Shared feedback motion role and current host motion policy. */
        val feedbackSpec = PixelMotionTheme.of(context).feedback
        /** Optional shared ticker and reduced-motion settings owner. */
        val motionScope = PixelMotionScope.maybeOf(context)
        /** Host-adjusted feedback policy, absent for immediate legacy trees. */
        val resolvedFeedback = motionScope?.let { scope -> feedbackSpec.resolve(scope.settings) }
        listOf(resolvedThumbMotion, resolvedTrackMotion, resolvedBorderMotion).forEach { motion ->
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
            resolvedThumbMotion.animateTo(targetThumbColor)
            resolvedTrackMotion.animateTo(concreteTrackColor)
            resolvedBorderMotion.animateTo(concreteBorderColor)
        } else {
            // Terminal states synchronously release in-flight feedback tickers.
            resolvedThumbMotion.snapTo(targetThumbColor)
            resolvedTrackMotion.snapTo(concreteTrackColor)
            resolvedBorderMotion.snapTo(concreteBorderColor)
        }
        listOf(resolvedThumbMotion, resolvedTrackMotion, resolvedBorderMotion).forEach { motion ->
            motion.watch(context)
        }
        /** 调用方省略覆写时，由组件尺寸 token 解析宽度。 Width resolved through component-size tokens when the caller omitted an override. */
        val resolvedWidth = (widget.width ?: componentTokens.resolveMinimumWidth(themeTokens.sizes))
            .coerceAtLeast(1)
        /** Border width resolved through the foundation border scale. */
        val resolvedBorderWidth = componentTokens.resolveBorderWidth(themeTokens.borders)
        /** Pixel stair-step radius resolved through the foundation radius scale. */
        val resolvedCornerRadius = componentTokens.resolveCornerRadius(themeTokens.radii)
        /** Final spoken label after explicit non-blank text wins over the localizable token. */
        val resolvedLabel = widget.semanticLabel?.takeIf { label -> label.isNotBlank() }
            ?: localizedLabels.scrollbar
        /** Render leaf exporting callbacks only while [interactive] is true. */
        val renderScrollbar = ScrollbarRenderWidget(
            child = widget.child,
            state = widget.state,
            thumbColor = resolvedThumbMotion.value,
            trackColor = resolvedTrackMotion.value,
            borderColor = resolvedBorderMotion.value.takeIf { targetBorderColor != null },
            borderWidth = resolvedBorderWidth,
            cornerRadius = resolvedCornerRadius,
            width = resolvedWidth,
            enabled = interactive,
            onPressedChanged = ::updatePressed,
            onHoveredChanged = ::updateHovered,
            key = widget.key,
        )
        /** Optional additive focus layer supplied by custom scrollbar component tokens. */
        val focusedScrollbar = withControlFocusIndicator(
            child = renderScrollbar,
            states = runtimeStates,
            componentTokens = componentTokens,
            colors = themeTokens.colors,
            borders = themeTokens.borders,
            key = widget.key?.let { "$it-focus-indicator" },
        )
        return Semantics(
            label = resolvedLabel,
            role = PixelSemanticRole.SCROLL_VIEW,
            enabled = interactive,
            focused = focused,
            value = localizedLabels.loading.takeIf {
                PixelControlState.Loading in runtimeStates
            },
            error = localizedLabels.error.takeIf {
                PixelControlState.Error in runtimeStates
            },
            selected = PixelControlState.Selected in runtimeStates,
            child = focusedScrollbar,
            key = widget.key?.let { "$it-semantics" },
        )
    }

    /** Updates retained pressed feedback only when the render target still permits mutation. */
    private fun updatePressed(nextPressed: Boolean) {
        val allowedPressed = nextPressed && widget.enabled
        if (pressed == allowedPressed) return
        setState { pressed = allowedPressed }
    }

    /** Updates retained hover feedback only when the render target still permits mutation. */
    private fun updateHovered(nextHovered: Boolean) {
        val allowedHovered = nextHovered && widget.enabled
        if (hovered == allowedHovered) return
        setState { hovered = allowedHovered }
    }
}

/** Immutable render configuration produced by [ScrollbarWidgetState]. */
private data class ScrollbarRenderWidget(
    /** Child viewport rendered below the scrollbar overlay. */
    override val child: Widget,
    /** Shared list state used to match the child list target. */
    val state: PixelListState,
    /** Final animated thumb color. */
    val thumbColor: PixelColor,
    /** Final animated track color. */
    val trackColor: PixelColor,
    /** Optional final animated border color. */
    val borderColor: PixelColor?,
    /** Resolved logical border width. */
    val borderWidth: Int,
    /** Resolved pixel stair-step radius. */
    val cornerRadius: Int,
    /** Resolved logical track width. */
    val width: Int,
    /** Whether the render object should export an interaction target. */
    val enabled: Boolean,
    /** Retained press callback receiving Host and tester ownership changes. */
    val onPressedChanged: (Boolean) -> Unit,
    /** Retained hover callback receiving Host and tester ownership changes. */
    val onHoveredChanged: (Boolean) -> Unit,
    /** Stable render identity. */
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    /** Creates the scrollbar render object with the first resolved configuration. */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderScrollbar(
            state = state,
            thumbColor = thumbColor,
            trackColor = trackColor,
            borderColor = borderColor,
            borderWidth = borderWidth,
            cornerRadius = cornerRadius,
            width = width,
            enabled = enabled,
            onPressedChanged = onPressedChanged,
            onHoveredChanged = onHoveredChanged,
        )
    }

    /** Updates the retained render object without replacing its target source identity. */
    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderScrollbar).updateScrollbar(
            state = state,
            thumbColor = thumbColor,
            trackColor = trackColor,
            borderColor = borderColor,
            borderWidth = borderWidth,
            cornerRadius = cornerRadius,
            width = width,
            enabled = enabled,
            onPressedChanged = onPressedChanged,
            onHoveredChanged = onHoveredChanged,
        )
    }
}
