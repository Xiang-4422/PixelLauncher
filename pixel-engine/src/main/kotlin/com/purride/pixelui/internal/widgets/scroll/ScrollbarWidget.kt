package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState

internal data class ScrollbarWidget(
    override val child: Widget,
    val state: PixelListState,
    val thumbColor: PixelColor,
    val trackColor: PixelColor?,
    val width: Int,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderScrollbar(
            state = state,
            thumbColor = thumbColor,
            trackColor = trackColor,
            width = width,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderScrollbar).updateScrollbar(
            state = state,
            thumbColor = thumbColor,
            trackColor = trackColor,
            width = width,
        )
    }
}
