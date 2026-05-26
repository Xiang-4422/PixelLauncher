package com.purride.pixelui.internal

import com.purride.pixelui.PixelCanvas

internal class RenderCustomPaint(
    private var preferredWidth: Int,
    private var preferredHeight: Int,
    private var painter: PixelCanvas.() -> Unit,
) : RenderBox() {
    fun update(
        preferredWidth: Int,
        preferredHeight: Int,
        painter: PixelCanvas.() -> Unit,
    ) {
        val sizeChanged = this.preferredWidth != preferredWidth || this.preferredHeight != preferredHeight
        this.preferredWidth = preferredWidth
        this.preferredHeight = preferredHeight
        this.painter = painter
        if (sizeChanged) markNeedsLayout()
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        size = RenderSize(
            width = constraints.constrainWidth(preferredWidth.coerceAtLeast(0)),
            height = constraints.constrainHeight(preferredHeight.coerceAtLeast(0)),
        )
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        PixelCanvas(
            context = context,
            offsetX = offsetX,
            offsetY = offsetY,
            width = size.width,
            height = size.height,
        ).painter()
    }
}
