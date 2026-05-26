package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelCanvas

internal data class CustomPaintWidget(
    val width: Int,
    val height: Int,
    val painter: PixelCanvas.() -> Unit,
    override val key: Any? = null,
) : LeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderCustomPaint(
            preferredWidth = width,
            preferredHeight = height,
            painter = painter,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderCustomPaint).update(
            preferredWidth = width,
            preferredHeight = height,
            painter = painter,
        )
    }
}
