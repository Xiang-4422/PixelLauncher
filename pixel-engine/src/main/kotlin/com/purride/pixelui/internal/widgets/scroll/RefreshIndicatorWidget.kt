package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.state.PixelRefreshIndicatorState

internal data class RefreshIndicatorWidget(
    val child: Widget,
    val state: PixelRefreshIndicatorState,
    val controller: PixelRefreshIndicatorController,
    val thresholdPx: Int,
    val enabled: Boolean,
    val indicatorColor: PixelColor,
    val armedColor: PixelColor,
    val refreshingColor: PixelColor,
    val onRefresh: () -> Unit,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        return RefreshIndicatorRenderWidget(
            child = child,
            state = state,
            controller = controller,
            thresholdPx = thresholdPx,
            enabled = enabled,
            indicatorColor = indicatorColor,
            armedColor = armedColor,
            refreshingColor = refreshingColor,
            onRefresh = onRefresh,
            key = key,
        )
    }
}

private data class RefreshIndicatorRenderWidget(
    override val child: Widget,
    val state: PixelRefreshIndicatorState,
    val controller: PixelRefreshIndicatorController,
    val thresholdPx: Int,
    val enabled: Boolean,
    val indicatorColor: PixelColor,
    val armedColor: PixelColor,
    val refreshingColor: PixelColor,
    val onRefresh: () -> Unit,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderRefreshIndicator(
            state = state,
            controller = controller,
            thresholdPx = thresholdPx,
            enabled = enabled,
            indicatorColor = indicatorColor,
            armedColor = armedColor,
            refreshingColor = refreshingColor,
            onRefresh = onRefresh,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderRefreshIndicator).updateRefreshIndicator(
            state = state,
            controller = controller,
            thresholdPx = thresholdPx,
            enabled = enabled,
            indicatorColor = indicatorColor,
            armedColor = armedColor,
            refreshingColor = refreshingColor,
            onRefresh = onRefresh,
        )
    }
}
