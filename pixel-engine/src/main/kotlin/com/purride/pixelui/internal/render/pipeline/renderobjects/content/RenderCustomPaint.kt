package com.purride.pixelui.internal

import com.purride.pixelui.PixelCanvas
import com.purride.pixelui.advanced.PixelExperimentalApi
import com.purride.pixelui.advanced.PixelPaintContext

/** 定义 `RenderCustomPaint` 在 `RenderCustomPaint` 中承担的数据与行为边界。
 *
 * Internal retained render box backing the public `CustomPaint` Widget.
 */
public class RenderCustomPaint(
    /** Requested width before parent-constraint clamping. */
    private var preferredWidth: Int,
    /** Requested height before parent-constraint clamping. */
    private var preferredHeight: Int,
    /** Consumer callback invoked with the stable public canvas facade. */
    private var painter: PixelCanvas.() -> Unit,
) : RenderBox() {
    /** 更新 `RenderCustomPaint` 的 `update` 状态并保持派生数据一致。
 *
 * Synchronizes immutable Widget configuration and invalidates only the required stages.
 */
    public fun update(
        preferredWidth: Int,
        preferredHeight: Int,
        painter: PixelCanvas.() -> Unit,
    ) {
        /** Whether the new preferred bounds require another layout pass. */
        val sizeChanged = this.preferredWidth != preferredWidth || this.preferredHeight != preferredHeight
        this.preferredWidth = preferredWidth
        this.preferredHeight = preferredHeight
        this.painter = painter
        if (sizeChanged) markNeedsLayout()
        markNeedsPaint()
    }

    /** Clamps preferred bounds into the current retained parent constraints. */
    override fun layout(constraints: RenderConstraints) {
        size = RenderSize(
            width = constraints.constrainWidth(preferredWidth.coerceAtLeast(0)),
            height = constraints.constrainHeight(preferredHeight.coerceAtLeast(0)),
        )
    }

    /** Wraps the internal frame target in a stable canvas before invoking consumer paint code. */
    @OptIn(PixelExperimentalApi::class)
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        PixelCanvas(
            context = PixelPaintContext(
                buffer = context.buffer,
                bufferPool = context.bufferPool,
            ),
            offsetX = offsetX,
            offsetY = offsetY,
            width = size.width,
            height = size.height,
        ).painter()
    }
}
