package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.state.PixelRefreshIndicatorState

/** 定义 `RenderRefreshIndicator` 在 `RenderRefreshIndicator` 中承担的数据与行为边界。
 *
 * Paints pull progress above content and exports one controller-backed refresh boundary.
 */
public class RenderRefreshIndicator(
    child: RenderBox? = null,
    /** Controlled refresh lifecycle state. */
    private var state: PixelRefreshIndicatorState,
    /** Controller paired with [state]. */
    private var controller: PixelRefreshIndicatorController,
    /** Positive trigger distance shared by every input path. */
    private var thresholdPx: Int,
    /** Whether this object may export a mutable refresh target. */
    private var enabled: Boolean,
    /** Whether explicit or controlled Loading should paint a full indicator. */
    private var loading: Boolean,
    /** Final state-resolved progress foreground. */
    private var indicatorColor: PixelColor,
    /** Final state-resolved full-width track. */
    private var trackColor: PixelColor,
    /** Optional state-resolved outline. */
    private var borderColor: PixelColor?,
    /** 由 foundation token 解析出的边框宽度。 Foundation-resolved outline width. */
    private var borderWidth: Int,
    /** Foundation-resolved pixel stair-step radius. */
    private var cornerRadius: Int,
    /** Foundation-resolved maximum indicator height. */
    private var indicatorHeight: Int,
    /** Retained callback for pressed-state ownership. */
    private var onPressedChanged: ((Boolean) -> Unit)?,
    /** Retained callback for mouse/stylus hover ownership. */
    private var onHoveredChanged: ((Boolean) -> Unit)?,
    /** Business callback invoked after a successful pointer pull. */
    private var onRefresh: () -> Unit,
) : SingleChildRenderObject() {
    /** Installs the first retained refresh content child. */
    init {
        setRenderObjectChild(child)
    }

    /** 更新 `RenderRefreshIndicator` 的 `updateRefreshIndicator` 状态并保持派生数据一致。
 *
 * Applies a rebuilt visual and interaction snapshot without replacing retained identity.
 */
    public fun updateRefreshIndicator(
        state: PixelRefreshIndicatorState,
        controller: PixelRefreshIndicatorController,
        thresholdPx: Int,
        enabled: Boolean,
        loading: Boolean,
        indicatorColor: PixelColor,
        trackColor: PixelColor,
        borderColor: PixelColor?,
        borderWidth: Int,
        cornerRadius: Int,
        indicatorHeight: Int,
        onPressedChanged: ((Boolean) -> Unit)?,
        onHoveredChanged: ((Boolean) -> Unit)?,
        onRefresh: () -> Unit,
    ) {
        if (
            this.state === state &&
            this.controller === controller &&
            this.thresholdPx == thresholdPx &&
            this.enabled == enabled &&
            this.loading == loading &&
            this.indicatorColor == indicatorColor &&
            this.trackColor == trackColor &&
            this.borderColor == borderColor &&
            this.borderWidth == borderWidth &&
            this.cornerRadius == cornerRadius &&
            this.indicatorHeight == indicatorHeight &&
            this.onPressedChanged === onPressedChanged &&
            this.onHoveredChanged === onHoveredChanged &&
            this.onRefresh === onRefresh
        ) {
            return
        }
        this.state = state
        this.controller = controller
        this.thresholdPx = thresholdPx
        this.enabled = enabled
        this.loading = loading
        this.indicatorColor = indicatorColor
        this.trackColor = trackColor
        this.borderColor = borderColor
        this.borderWidth = borderWidth.coerceAtLeast(0)
        this.cornerRadius = cornerRadius.coerceAtLeast(0)
        this.indicatorHeight = indicatorHeight.coerceAtLeast(1)
        this.onPressedChanged = onPressedChanged
        this.onHoveredChanged = onHoveredChanged
        this.onRefresh = onRefresh
        markNeedsPaint()
    }

    /** Sizes the refresh boundary exactly to its wrapped content. */
    override fun layout(constraints: RenderConstraints) {
        renderChild?.layout(constraints)
        size = renderChild?.size ?: RenderSize(constraints.maxWidth, constraints.maxHeight)
    }

    /** Paints content first, then any visible track, border, and pull-progress foreground. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        renderChild?.paint(context, offsetX, offsetY)
        /** Controlled refreshing is retained for defensive internal callers. */
        val paintsLoading = loading || state.isRefreshing
        // A controlled partial pull may survive into a static Disabled frame long enough for the
        // state-aware disabled color to be observable. Disabled still exports no mutable target,
        // and Host/tester reconciliation resets an actively owned pull immediately afterward.
        if (!enabled && !paintsLoading && state.pullDistancePx <= 0f) return
        /** Positive divisor shared with every interaction path. */
        val safeThreshold = thresholdPx.coerceAtLeast(1)
        /** Visible fraction, forced to full width for explicit or controlled Loading. */
        val progress = if (paintsLoading) {
            1f
        } else {
            (state.pullDistancePx / safeThreshold.toFloat()).coerceIn(0f, 1f)
        }
        if (progress <= 0f) return
        /** Indicator height constrained by the actual child viewport. */
        val safeHeight = indicatorHeight.coerceIn(1, size.height.coerceAtLeast(1))
        /** Full-width track bounds behind the progress foreground. */
        val trackBounds = PixelRect(offsetX, offsetY, size.width.coerceAtLeast(1), safeHeight)
        paintRefreshRoundedRect(context, trackBounds, trackColor, cornerRadius)
        borderColor?.let { color ->
            paintRefreshBorder(
                context = context,
                bounds = trackBounds,
                color = color,
                width = borderWidth,
                radius = cornerRadius,
            )
        }
        /** Progress width mapped deterministically from the current pull fraction. */
        val barWidth = (size.width * progress).toInt().coerceIn(1, size.width.coerceAtLeast(1))
        paintRefreshRoundedRect(
            context = context,
            bounds = PixelRect(offsetX, offsetY, barWidth, safeHeight),
            color = indicatorColor,
            radius = cornerRadius,
        )
    }

    /** Preserves descendant hit testing inside the refresh boundary. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        if (localX !in 0 until size.width || localY !in 0 until size.height) return
        renderChild?.hitTest(localX, localY, result)
    }

    /** Forwards child click targets so they retain precedence during gesture arbitration. */
    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>): Unit = renderChild?.collectClickTargets(offsetX, offsetY, targets) ?: Unit
    /** Forwards child pager targets unchanged. */
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>): Unit = renderChild?.collectPagerTargets(offsetX, offsetY, targets) ?: Unit
    /** Forwards child list targets used to enforce leading-edge pull eligibility. */
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>): Unit = renderChild?.collectListTargets(offsetX, offsetY, targets) ?: Unit
    /** Forwards nested scrollbar targets unchanged. */
    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>): Unit = renderChild?.collectScrollbarTargets(offsetX, offsetY, targets) ?: Unit

    /** Exports one mutable refresh target only while this boundary remains interactive. */
    override fun collectRefreshTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelRefreshTarget>) {
        renderChild?.collectRefreshTargets(offsetX, offsetY, targets)
        if (!enabled) return
        /** Descendant lists searched for the one intersecting this refresh boundary. */
        val listTargets = mutableListOf<PixelListTarget>()
        renderChild?.collectListTargets(offsetX, offsetY, listTargets)
        /** Optional controlled list state used to reject pulls away from its leading edge. */
        val sourceListState = listTargets.lastOrNull { target ->
            target.bounds.intersect(PixelRect(offsetX, offsetY, size.width, size.height)) != null
        }?.state
        targets += PixelRefreshTarget(
            bounds = PixelRect(offsetX, offsetY, size.width, size.height),
            thresholdPx = thresholdPx.coerceAtLeast(1),
            enabled = enabled,
            sourceListState = sourceListState,
            state = state,
            controller = controller,
            onRefresh = onRefresh,
            onPressedChanged = onPressedChanged,
            onHoveredChanged = onHoveredChanged,
            source = this,
        )
    }

    /** Forwards child text-input targets unchanged. */
    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>): Unit = renderChild?.collectTextInputTargets(offsetX, offsetY, targets) ?: Unit
    /** Forwards child slider targets unchanged. */
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>): Unit = renderChild?.collectSliderTargets(offsetX, offsetY, targets) ?: Unit
    /** Forwards child semantics beneath the outer RefreshIndicator semantics node. */
    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>): Unit = renderChild?.collectSemantics(offsetX, offsetY, targets) ?: Unit

    /** Wrapped child viewed as the layout-and-paint render protocol. */
    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

/** Paints one integer stair-step rounded rectangle for the refresh indicator. */
private fun paintRefreshRoundedRect(
    context: PaintContext,
    bounds: PixelRect,
    color: PixelColor,
    radius: Int,
) {
    if (bounds.width <= 0 || bounds.height <= 0) return
    /** Radius clamped so opposing pixel stair steps never overlap. */
    val safeRadius = radius.coerceIn(
        0,
        ((minOf(bounds.width, bounds.height) - 1).coerceAtLeast(0)) / 2,
    )
    if (safeRadius == 0) {
        context.fillRect(bounds.left, bounds.top, bounds.width, bounds.height, color)
        return
    }
    repeat(bounds.height) { row ->
        /** Distance to the nearest horizontal edge for this scan line. */
        val edgeDistance = minOf(row, bounds.height - 1 - row)
        /** Stair-step inset from both vertical edges. */
        val inset = (safeRadius - edgeDistance).coerceAtLeast(0)
        /** Remaining positive scan width. */
        val scanWidth = (bounds.width - inset * 2).coerceAtLeast(0)
        if (scanWidth > 0) context.fillRect(bounds.left + inset, bounds.top + row, scanWidth, 1, color)
    }
}

/** Paints an inset refresh-indicator border using the same stair-step geometry. */
private fun paintRefreshBorder(
    context: PaintContext,
    bounds: PixelRect,
    color: PixelColor,
    width: Int,
    radius: Int,
) {
    /** Border layers limited by the shortest extent. */
    val layers = width.coerceIn(0, (minOf(bounds.width, bounds.height) + 1) / 2)
    repeat(layers) { layer ->
        /** Current inset outline bounds. */
        val layerBounds = PixelRect(
            left = bounds.left + layer,
            top = bounds.top + layer,
            width = bounds.width - layer * 2,
            height = bounds.height - layer * 2,
        )
        if (layerBounds.width <= 0 || layerBounds.height <= 0) return@repeat
        /** Current inset radius after consuming outer layers. */
        val layerRadius = (radius - layer).coerceAtLeast(0)
        repeat(layerBounds.height) { row ->
            /** Rounded scan-line inset. */
            val edgeDistance = minOf(row, layerBounds.height - 1 - row)
            /** Final x inset for both outline edges. */
            val inset = (layerRadius - edgeDistance).coerceIn(0, layerBounds.width / 2)
            /** Inclusive left outline coordinate. */
            val left = layerBounds.left + inset
            /** Inclusive right outline coordinate. */
            val right = layerBounds.left + layerBounds.width - 1 - inset
            if (row == 0 || row == layerBounds.height - 1) {
                context.fillRect(left, layerBounds.top + row, (right - left + 1).coerceAtLeast(0), 1, color)
            } else if (left <= right) {
                context.setColor(left, layerBounds.top + row, color)
                if (right != left) context.setColor(right, layerBounds.top + row, color)
            }
        }
    }
}
