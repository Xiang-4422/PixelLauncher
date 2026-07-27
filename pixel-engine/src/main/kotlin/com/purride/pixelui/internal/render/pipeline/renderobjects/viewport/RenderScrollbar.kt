package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.state.PixelListState

/** 定义 `RenderScrollbar` 在 `RenderScrollbar` 中承担的数据与行为边界。
 *
 * Paints one token-resolved scrollbar and exports its controller-backed drag target.
 */
public class RenderScrollbar(
    child: RenderBox? = null,
    /** List state matched against the wrapped viewport target. */
    private var state: PixelListState,
    /** Final state-resolved thumb color. */
    private var thumbColor: PixelColor,
    /** Final state-resolved track color. */
    private var trackColor: PixelColor,
    /** Optional state-resolved outline color. */
    private var borderColor: PixelColor?,
    /** 由 foundation token 解析出的边框宽度。 Foundation-resolved outline width. */
    private var borderWidth: Int,
    /** Foundation-resolved pixel stair-step radius. */
    private var cornerRadius: Int,
    /** Foundation-resolved scrollbar width. */
    private var width: Int,
    /** Whether this render object may export a mutable target. */
    private var enabled: Boolean,
    /** Callback receiving retained press ownership changes. */
    private var onPressedChanged: ((Boolean) -> Unit)?,
    /** Callback receiving retained mouse/stylus hover ownership changes. */
    private var onHoveredChanged: ((Boolean) -> Unit)?,
) : SingleChildRenderObject() {
    /** Installs the first retained viewport child. */
    init {
        setRenderObjectChild(child)
    }

    /** 更新 `RenderScrollbar` 的 `updateScrollbar` 状态并保持派生数据一致。
 *
 * Applies a rebuilt visual and interaction snapshot without replacing retained identity.
 */
    public fun updateScrollbar(
        state: PixelListState,
        thumbColor: PixelColor,
        trackColor: PixelColor,
        borderColor: PixelColor?,
        borderWidth: Int,
        cornerRadius: Int,
        width: Int,
        enabled: Boolean,
        onPressedChanged: ((Boolean) -> Unit)?,
        onHoveredChanged: ((Boolean) -> Unit)?,
    ) {
        if (
            this.state === state &&
            this.thumbColor == thumbColor &&
            this.trackColor == trackColor &&
            this.borderColor == borderColor &&
            this.borderWidth == borderWidth &&
            this.cornerRadius == cornerRadius &&
            this.width == width &&
            this.enabled == enabled &&
            this.onPressedChanged === onPressedChanged &&
            this.onHoveredChanged === onHoveredChanged
        ) {
            return
        }
        this.state = state
        this.thumbColor = thumbColor
        this.trackColor = trackColor
        this.borderColor = borderColor
        this.borderWidth = borderWidth.coerceAtLeast(0)
        this.cornerRadius = cornerRadius.coerceAtLeast(0)
        this.width = width
        this.enabled = enabled
        this.onPressedChanged = onPressedChanged
        this.onHoveredChanged = onHoveredChanged
        markNeedsLayout()
        markNeedsPaint()
    }

    /** Sizes the overlay exactly to its wrapped viewport. */
    override fun layout(constraints: RenderConstraints) {
        renderChild?.layout(constraints)
        size = renderChild?.size ?: RenderSize(constraints.maxWidth, constraints.maxHeight)
    }

    /** Paints child content first, followed by the track, outline, and proportional thumb. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        renderChild?.paint(context, offsetX, offsetY)
        /** Current geometry; null means the viewport has no scrollable overflow. */
        val metrics = scrollbarMetrics(offsetX, offsetY) ?: return
        paintPixelRoundedRect(context, metrics.trackBounds, trackColor, cornerRadius)
        borderColor?.let { color ->
            paintPixelRoundedBorder(
                context = context,
                bounds = metrics.trackBounds,
                color = color,
                width = borderWidth,
                radius = cornerRadius,
            )
        }
        paintPixelRoundedRect(
            context = context,
            bounds = metrics.thumbBounds,
            color = thumbColor,
            radius = cornerRadius,
        )
    }

    /** Preserves child hit testing because drag ownership is exported through frame targets. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        renderChild?.hitTest(localX, localY, result)
    }

    /** Forwards child click targets unchanged. */
    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>): Unit = renderChild?.collectClickTargets(offsetX, offsetY, targets) ?: Unit
    /** Forwards child pager targets unchanged. */
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>): Unit = renderChild?.collectPagerTargets(offsetX, offsetY, targets) ?: Unit
    /** Forwards child list targets used to pair scrollbar geometry with the correct owner. */
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>): Unit = renderChild?.collectListTargets(offsetX, offsetY, targets) ?: Unit
    /** Exports one mutable scrollbar target only while this component is interactive. */
    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>) {
        renderChild?.collectScrollbarTargets(offsetX, offsetY, targets)
        if (!enabled) return
        /** Current interactive geometry; absent when the child cannot scroll. */
        val metrics = scrollbarMetrics(offsetX, offsetY) ?: return
        /** Descendant list targets searched for the state paired with this overlay. */
        val childListTargets = mutableListOf<PixelListTarget>()
        renderChild?.collectListTargets(offsetX, offsetY, childListTargets)
        /** Exact controller target whose state owns the scrollbar geometry. */
        val listTarget = childListTargets.lastOrNull { it.state === state } ?: return
        targets += PixelScrollbarTarget(
            bounds = metrics.trackBounds,
            thumbBounds = metrics.thumbBounds,
            viewportHeightPx = listTarget.viewportHeightPx,
            contentHeightPx = listTarget.contentHeightPx,
            state = state,
            controller = listTarget.controller,
            onPressedChanged = onPressedChanged,
            onHoveredChanged = onHoveredChanged,
            source = this,
        )
    }
    /** Forwards child text-input targets unchanged. */
    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>): Unit = renderChild?.collectTextInputTargets(offsetX, offsetY, targets) ?: Unit
    /** Forwards child slider targets unchanged. */
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>): Unit = renderChild?.collectSliderTargets(offsetX, offsetY, targets) ?: Unit
    /** Forwards child semantics beneath the outer Scrollbar semantics wrapper. */
    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>): Unit = renderChild?.collectSemantics(offsetX, offsetY, targets) ?: Unit

    /** Resolves current track and thumb rectangles from controlled list geometry. */
    private fun scrollbarMetrics(offsetX: Int, offsetY: Int): ScrollbarMetrics? {
        /** Effective visible extent synchronized with the latest render size. */
        val viewport = state.viewportHeightPx.coerceAtLeast(size.height)
        /** Total controlled content extent. */
        val content = state.contentHeightPx
        if (content <= viewport || viewport <= 0 || size.height <= 0) return null
        /** Width clamped to at least one pixel and at most the viewport width. */
        val safeWidth = width.coerceAtLeast(1).coerceAtMost(size.width.coerceAtLeast(1))
        /** Right-aligned logical x coordinate of the scrollbar track. */
        val barX = offsetX + size.width - safeWidth
        /** Proportional thumb height with a one-pixel visibility floor. */
        val thumbHeight = ((viewport.toLong() * size.height) / content).toInt().coerceIn(1, size.height)
        /** Positive maximum list offset used for ratio conversion. */
        val maxOffset = (content - viewport).coerceAtLeast(1)
        /** Available logical travel between the track endpoints. */
        val thumbTravel = (size.height - thumbHeight).coerceAtLeast(0)
        /** Current proportional thumb offset inside the track. */
        val thumbTop = ((state.scrollOffsetPx.coerceIn(0f, maxOffset.toFloat()) * thumbTravel) / maxOffset).toInt()
        return ScrollbarMetrics(
            trackBounds = PixelRect(barX, offsetY, safeWidth, size.height),
            thumbBounds = PixelRect(barX, offsetY + thumbTop, safeWidth, thumbHeight),
        )
    }

    /** Wrapped child viewed as the layout-and-paint render protocol. */
    private val renderChild: RenderBox?
        get() = child as? RenderBox

    /** Immutable per-frame track and thumb geometry shared by paint and hit-target export. */
    private data class ScrollbarMetrics(
        /** Full scrollbar track bounds used for paint and pointer hit testing. */
        val trackBounds: PixelRect,
        /** Current proportional thumb bounds inside [trackBounds]. */
        val thumbBounds: PixelRect,
    )
}

/** Paints a deterministic stair-step rounded rectangle using integer scan lines only. */
private fun paintPixelRoundedRect(
    context: PaintContext,
    bounds: PixelRect,
    color: PixelColor,
    radius: Int,
) {
    if (bounds.width <= 0 || bounds.height <= 0) return
    /** Radius clamped so opposing stair steps never overlap. */
    val safeRadius = radius.coerceIn(
        0,
        ((minOf(bounds.width, bounds.height) - 1).coerceAtLeast(0)) / 2,
    )
    if (safeRadius == 0) {
        context.fillRect(bounds.left, bounds.top, bounds.width, bounds.height, color)
        return
    }
    repeat(bounds.height) { row ->
        /** Distance from this row to the nearest horizontal edge. */
        val edgeDistance = minOf(row, bounds.height - 1 - row)
        /** Integer inset producing the pixel stair-step corner. */
        val inset = (safeRadius - edgeDistance).coerceAtLeast(0)
        /** Positive scan width after applying both corner insets. */
        val scanWidth = (bounds.width - inset * 2).coerceAtLeast(0)
        if (scanWidth > 0) context.fillRect(bounds.left + inset, bounds.top + row, scanWidth, 1, color)
    }
}

/** Paints an inset pixel border while preserving the same rounded-rectangle geometry. */
private fun paintPixelRoundedBorder(
    context: PaintContext,
    bounds: PixelRect,
    color: PixelColor,
    width: Int,
    radius: Int,
) {
    /** Border layers are bounded by half the shortest extent. */
    val layers = width.coerceIn(0, (minOf(bounds.width, bounds.height) + 1) / 2)
    repeat(layers) { layer ->
        /** Bounds of the current one-pixel outline layer. */
        val layerBounds = PixelRect(
            left = bounds.left + layer,
            top = bounds.top + layer,
            width = bounds.width - layer * 2,
            height = bounds.height - layer * 2,
        )
        if (layerBounds.width <= 0 || layerBounds.height <= 0) return@repeat
        /** Layer-specific corner radius after consuming outer pixels. */
        val layerRadius = (radius - layer).coerceAtLeast(0)
        repeat(layerBounds.height) { row ->
            /** Scan-line inset matching [paintPixelRoundedRect]. */
            val edgeDistance = minOf(row, layerBounds.height - 1 - row)
            /** Rounded left and right edge x offset. */
            val inset = (layerRadius - edgeDistance)
                .coerceIn(0, layerBounds.width / 2)
            /** Inclusive left edge pixel. */
            val left = layerBounds.left + inset
            /** Inclusive right edge pixel. */
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
