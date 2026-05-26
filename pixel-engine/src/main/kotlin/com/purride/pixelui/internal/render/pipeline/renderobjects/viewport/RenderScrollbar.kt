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
        val viewport = state.viewportHeightPx.coerceAtLeast(size.height)
        val content = state.contentHeightPx
        if (content <= viewport || viewport <= 0) return
        val safeWidth = width.coerceAtLeast(1).coerceAtMost(size.width.coerceAtLeast(1))
        val barX = offsetX + size.width - safeWidth
        trackColor?.let { context.fillRect(barX, offsetY, safeWidth, size.height, it) }
        val thumbHeight = ((viewport.toLong() * size.height) / content).toInt().coerceIn(1, size.height)
        val maxOffset = (content - viewport).coerceAtLeast(1)
        val thumbTravel = (size.height - thumbHeight).coerceAtLeast(0)
        val thumbTop = ((state.scrollOffsetPx.coerceIn(0f, maxOffset.toFloat()) * thumbTravel) / maxOffset).toInt()
        context.fillRect(barX, offsetY + thumbTop, safeWidth, thumbHeight, thumbColor)
    }

    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        renderChild?.hitTest(localX, localY, result)
    }

    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) = renderChild?.collectClickTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) = renderChild?.collectPagerTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) = renderChild?.collectListTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>) = renderChild?.collectTextInputTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) = renderChild?.collectSliderTargets(offsetX, offsetY, targets) ?: Unit

    private val renderChild: RenderBox?
        get() = child as? RenderBox
}
