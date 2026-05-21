package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────
//  Public-facing widget wrapper (internal to pixel-engine)
// ─────────────────────────────────────────────────────────────────────

/**
 * 像素风滑块 widget。
 *
 * @param value     当前值，0.0..1.0
 * @param onDrag    手指滑动时持续调用（值已钳位到 0..1）
 * @param onRelease 手指抬起时调用（值已钳位到 0..1）
 * @param activeColor  已填充区域颜色，默认橙色
 * @param trackColor   轨道边框/空区域颜色，默认白色
 */
internal data class SliderWidget(
    val value: Float,
    val onDrag: (Float) -> Unit,
    val onRelease: (Float) -> Unit,
    val activeColor: PixelColor = PixelColor.fromRgb(200, 100, 0),
    val trackColor: PixelColor = PixelColor.White,
    override val key: Any? = null,
) : LeafRenderObjectWidget(key = key) {

    override fun createRenderObject(context: BuildContext): RenderObject =
        RenderSlider(
            value = value,
            onDrag = onDrag,
            onRelease = onRelease,
            activeColor = activeColor,
            trackColor = trackColor,
        )

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderSlider).update(
            value = value,
            onDrag = onDrag,
            onRelease = onRelease,
            activeColor = activeColor,
            trackColor = trackColor,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
//  RenderSlider — leaf render object
// ─────────────────────────────────────────────────────────────────────

internal class RenderSlider(
    private var value: Float,
    private var onDrag: (Float) -> Unit,
    private var onRelease: (Float) -> Unit,
    private var activeColor: PixelColor,
    private var trackColor: PixelColor,
) : RenderBox() {

    override fun layout(constraints: RenderConstraints) {
        size = RenderSize(
            width = constraints.maxWidth,
            height = HEIGHT,
        )
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val w = size.width
        val h = size.height
        if (w < 3 || h < 3) return

        val v = value.coerceIn(0f, 1f)

        // Outer border
        context.drawRect(offsetX, offsetY, w, h, trackColor)

        // Filled (active) region: inside the border, left portion
        val innerW = w - 2
        val fillW = (innerW * v).roundToInt().coerceIn(0, innerW)
        if (fillW > 0) {
            context.fillRect(offsetX + 1, offsetY + 1, fillW, h - 2, activeColor)
        }

        // Thumb: 1-px-wide bright column at the fill edge (only if not at extremes)
        if (fillW in 1 until innerW) {
            val thumbX = offsetX + 1 + fillW - 1
            for (y in offsetY + 1 until offsetY + h - 1) {
                context.buffer.setPixel(thumbX, y, trackColor)
            }
        }
    }

    override fun collectSliderTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelSliderTarget>,
    ) {
        targets += PixelSliderTarget(
            bounds = PixelRect(
                left = offsetX,
                top = offsetY,
                width = size.width,
                height = size.height,
            ),
            onDrag = onDrag,
            onRelease = onRelease,
        )
    }

    fun update(
        value: Float,
        onDrag: (Float) -> Unit,
        onRelease: (Float) -> Unit,
        activeColor: PixelColor,
        trackColor: PixelColor,
    ) {
        val changed = this.value != value ||
            this.onDrag !== onDrag ||
            this.onRelease !== onRelease ||
            this.activeColor != activeColor ||
            this.trackColor != trackColor
        if (!changed) return
        this.value = value
        this.onDrag = onDrag
        this.onRelease = onRelease
        this.activeColor = activeColor
        this.trackColor = trackColor
        markNeedsPaint()
    }

    companion object {
        const val HEIGHT = 7
    }
}
