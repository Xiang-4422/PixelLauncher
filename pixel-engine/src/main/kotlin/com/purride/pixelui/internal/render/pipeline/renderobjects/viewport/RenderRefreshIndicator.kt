package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.state.PixelRefreshIndicatorState

internal class RenderRefreshIndicator(
    child: RenderBox? = null,
    private var state: PixelRefreshIndicatorState,
    private var controller: PixelRefreshIndicatorController,
    private var thresholdPx: Int,
    private var enabled: Boolean,
    private var indicatorColor: PixelColor,
    private var armedColor: PixelColor,
    private var refreshingColor: PixelColor,
    private var onRefresh: () -> Unit,
) : SingleChildRenderObject() {
    init {
        setRenderObjectChild(child)
    }

    fun updateRefreshIndicator(
        state: PixelRefreshIndicatorState,
        controller: PixelRefreshIndicatorController,
        thresholdPx: Int,
        enabled: Boolean,
        indicatorColor: PixelColor,
        armedColor: PixelColor,
        refreshingColor: PixelColor,
        onRefresh: () -> Unit,
    ) {
        if (
            this.state === state &&
            this.controller === controller &&
            this.thresholdPx == thresholdPx &&
            this.enabled == enabled &&
            this.indicatorColor == indicatorColor &&
            this.armedColor == armedColor &&
            this.refreshingColor == refreshingColor &&
            this.onRefresh === onRefresh
        ) {
            return
        }
        this.state = state
        this.controller = controller
        this.thresholdPx = thresholdPx
        this.enabled = enabled
        this.indicatorColor = indicatorColor
        this.armedColor = armedColor
        this.refreshingColor = refreshingColor
        this.onRefresh = onRefresh
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        renderChild?.layout(constraints)
        size = renderChild?.size ?: RenderSize(constraints.maxWidth, constraints.maxHeight)
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        renderChild?.paint(context, offsetX, offsetY)
        if (!enabled && !state.isRefreshing) return
        val safeThreshold = thresholdPx.coerceAtLeast(1)
        val progress = if (state.isRefreshing) {
            1f
        } else {
            (state.pullDistancePx / safeThreshold.toFloat()).coerceIn(0f, 1f)
        }
        if (progress <= 0f) return
        val color = when {
            state.isRefreshing -> refreshingColor
            state.isArmed -> armedColor
            else -> indicatorColor
        }
        val barWidth = (size.width * progress).toInt().coerceIn(1, size.width.coerceAtLeast(1))
        context.fillRect(offsetX, offsetY, barWidth, 1, color)
        if (state.isRefreshing && size.height > 1) {
            context.fillRect(offsetX, offsetY + 1, size.width.coerceAtLeast(1), 1, color)
        }
    }

    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        if (localX !in 0 until size.width || localY !in 0 until size.height) return
        renderChild?.hitTest(localX, localY, result)
    }

    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) = renderChild?.collectClickTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) = renderChild?.collectPagerTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) = renderChild?.collectListTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>) = renderChild?.collectScrollbarTargets(offsetX, offsetY, targets) ?: Unit

    override fun collectRefreshTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelRefreshTarget>) {
        renderChild?.collectRefreshTargets(offsetX, offsetY, targets)
        val listTargets = mutableListOf<PixelListTarget>()
        renderChild?.collectListTargets(offsetX, offsetY, listTargets)
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
            source = this,
        )
    }

    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>) = renderChild?.collectTextInputTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) = renderChild?.collectSliderTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) = renderChild?.collectSemantics(offsetX, offsetY, targets) ?: Unit

    private val renderChild: RenderBox?
        get() = child as? RenderBox
}
