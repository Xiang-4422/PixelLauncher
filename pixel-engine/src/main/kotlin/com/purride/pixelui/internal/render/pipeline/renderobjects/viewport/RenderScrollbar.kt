package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.state.PixelListState

internal class RenderScrollbar(
    child: RenderBox? = null,
    private var state: PixelListState,
    private var thumbColor: PixelColor,
    private var trackColor: PixelColor?,
    private var width: Int,
) : SingleChildRenderObject() {
    init {
        setRenderObjectChild(child)
    }

    fun updateScrollbar(
        state: PixelListState,
        thumbColor: PixelColor,
        trackColor: PixelColor?,
        width: Int,
    ) {
        if (this.state === state && this.thumbColor == thumbColor && this.trackColor == trackColor && this.width == width) return
        this.state = state
        this.thumbColor = thumbColor
        this.trackColor = trackColor
        this.width = width
        markNeedsLayout()
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        renderChild?.layout(constraints)
        size = renderChild?.size ?: RenderSize(constraints.maxWidth, constraints.maxHeight)
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        renderChild?.paint(context, offsetX, offsetY)
        val metrics = scrollbarMetrics(offsetX, offsetY) ?: return
        val barX = metrics.trackBounds.left
        val safeWidth = metrics.trackBounds.width
        trackColor?.let { context.fillRect(barX, offsetY, safeWidth, size.height, it) }
        context.fillRect(
            metrics.thumbBounds.left,
            metrics.thumbBounds.top,
            metrics.thumbBounds.width,
            metrics.thumbBounds.height,
            thumbColor,
        )
    }

    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        renderChild?.hitTest(localX, localY, result)
    }

    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) = renderChild?.collectClickTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) = renderChild?.collectPagerTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) = renderChild?.collectListTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>) {
        renderChild?.collectScrollbarTargets(offsetX, offsetY, targets)
        val metrics = scrollbarMetrics(offsetX, offsetY) ?: return
        val childListTargets = mutableListOf<PixelListTarget>()
        renderChild?.collectListTargets(offsetX, offsetY, childListTargets)
        val listTarget = childListTargets.lastOrNull { it.state === state } ?: return
        targets += PixelScrollbarTarget(
            bounds = metrics.trackBounds,
            thumbBounds = metrics.thumbBounds,
            viewportHeightPx = listTarget.viewportHeightPx,
            contentHeightPx = listTarget.contentHeightPx,
            state = state,
            controller = listTarget.controller,
        )
    }
    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>) = renderChild?.collectTextInputTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) = renderChild?.collectSliderTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectSemantics(offsetX: Int, offsetY: Int, nodes: MutableList<com.purride.pixelui.PixelSemanticsNode>) = renderChild?.collectSemantics(offsetX, offsetY, nodes) ?: Unit

    private fun scrollbarMetrics(offsetX: Int, offsetY: Int): ScrollbarMetrics? {
        val viewport = state.viewportHeightPx.coerceAtLeast(size.height)
        val content = state.contentHeightPx
        if (content <= viewport || viewport <= 0 || size.height <= 0) return null
        val safeWidth = width.coerceAtLeast(1).coerceAtMost(size.width.coerceAtLeast(1))
        val barX = offsetX + size.width - safeWidth
        val thumbHeight = ((viewport.toLong() * size.height) / content).toInt().coerceIn(1, size.height)
        val maxOffset = (content - viewport).coerceAtLeast(1)
        val thumbTravel = (size.height - thumbHeight).coerceAtLeast(0)
        val thumbTop = ((state.scrollOffsetPx.coerceIn(0f, maxOffset.toFloat()) * thumbTravel) / maxOffset).toInt()
        return ScrollbarMetrics(
            trackBounds = PixelRect(barX, offsetY, safeWidth, size.height),
            thumbBounds = PixelRect(barX, offsetY + thumbTop, safeWidth, thumbHeight),
        )
    }

    private val renderChild: RenderBox?
        get() = child as? RenderBox

    private data class ScrollbarMetrics(
        val trackBounds: PixelRect,
        val thumbBounds: PixelRect,
    )
}
