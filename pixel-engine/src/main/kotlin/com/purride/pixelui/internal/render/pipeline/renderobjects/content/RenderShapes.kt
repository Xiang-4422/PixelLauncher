package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import kotlin.math.abs
import kotlin.math.max

/**
 * 直线 render object：Bresenham 算法，端点相对 widget 左上角。
 *
 * Layout：`(maxOf(startX, endX) + 1, maxOf(startY, endY) + 1)` 截到 constraints。
 * Paint：逐像素 `buffer.setPixel`，越界由 PixelBuffer 内部裁剪。
 */
internal class RenderLine(
    private var startX: Int,
    private var startY: Int,
    private var endX: Int,
    private var endY: Int,
    private var color: PixelColor,
) : RenderBox() {

    fun update(startX: Int, startY: Int, endX: Int, endY: Int, color: PixelColor) {
        val sizeChanged = this.startX != startX || this.startY != startY ||
            this.endX != endX || this.endY != endY
        val anyChanged = sizeChanged || this.color != color
        if (!anyChanged) return
        this.startX = startX
        this.startY = startY
        this.endX = endX
        this.endY = endY
        this.color = color
        if (sizeChanged) markNeedsLayout()
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        val intrinsicW = max(startX, endX) + 1
        val intrinsicH = max(startY, endY) + 1
        size = RenderSize(
            width = constraints.constrainWidth(intrinsicW),
            height = constraints.constrainHeight(intrinsicH),
        )
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        var x0 = startX
        var y0 = startY
        val x1 = endX
        val y1 = endY
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        val buffer = context.buffer
        while (true) {
            buffer.setPixel(offsetX + x0, offsetY + y0, color)
            if (x0 == x1 && y0 == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x0 += sx }
            if (e2 <= dx) { err += dx; y0 += sy }
        }
    }
}

/**
 * 圆形 render object：中点圆算法。
 *
 * Layout：固定 `(2*radius + 1, 2*radius + 1)`，再按 constraints clamp。
 * Paint：[filled]=true 时逐 scanline 填充；否则只画轮廓。
 *        中心 = 当前 layout box 的中点。
 */
internal class RenderCircle(
    private var radius: Int,
    private var color: PixelColor,
    private var filled: Boolean,
) : RenderBox() {

    fun update(radius: Int, color: PixelColor, filled: Boolean) {
        val sizeChanged = this.radius != radius
        val anyChanged = sizeChanged || this.color != color || this.filled != filled
        if (!anyChanged) return
        this.radius = radius
        this.color = color
        this.filled = filled
        if (sizeChanged) markNeedsLayout()
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        val intrinsic = 2 * radius + 1
        size = RenderSize(
            width = constraints.constrainWidth(intrinsic),
            height = constraints.constrainHeight(intrinsic),
        )
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        if (radius < 0) return
        val cx = offsetX + size.width / 2
        val cy = offsetY + size.height / 2
        val buffer = context.buffer
        if (filled) {
            // 逐 scanline 填充：x² + y² ≤ r² → x ≤ √(r² - y²)
            val r2 = radius * radius
            for (dy in -radius..radius) {
                val dx2 = r2 - dy * dy
                if (dx2 < 0) continue
                val dx = kotlin.math.sqrt(dx2.toDouble()).toInt()
                for (x in -dx..dx) {
                    buffer.setPixel(cx + x, cy + dy, color)
                }
            }
        } else {
            // 中点圆轮廓算法（八分对称）。
            var x = radius
            var y = 0
            var err = 0
            while (x >= y) {
                buffer.setPixel(cx + x, cy + y, color)
                buffer.setPixel(cx + y, cy + x, color)
                buffer.setPixel(cx - y, cy + x, color)
                buffer.setPixel(cx - x, cy + y, color)
                buffer.setPixel(cx - x, cy - y, color)
                buffer.setPixel(cx - y, cy - x, color)
                buffer.setPixel(cx + y, cy - x, color)
                buffer.setPixel(cx + x, cy - y, color)
                y += 1
                if (err <= 0) {
                    err += 2 * y + 1
                } else {
                    x -= 1
                    err += 2 * (y - x) + 1
                }
            }
        }
    }
}
