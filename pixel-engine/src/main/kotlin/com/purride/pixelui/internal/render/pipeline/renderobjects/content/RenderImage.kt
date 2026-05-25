package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmap

/**
 * [Image] widget 的 render object。
 *
 * Layout：intrinsic 尺寸 = bitmap 自身 (width, height)，再按父约束 clamp。
 * Paint：把 bitmap 像素整块拷贝到目标 buffer 的 (offsetX, offsetY)；
 *        超出 size 的部分自动裁剪，无缩放。
 *
 * 调用方若需缩放，应在构造 [PixelBitmap] 前对源 Android Bitmap 缩放。
 */
internal class RenderImage(
    private var bitmap: PixelBitmap,
) : RenderBox() {

    fun updateBitmap(next: PixelBitmap) {
        if (bitmap === next) return
        val sizeChanged = bitmap.width != next.width || bitmap.height != next.height
        bitmap = next
        if (sizeChanged) markNeedsLayout()
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        val w = constraints.constrainWidth(bitmap.width)
        val h = constraints.constrainHeight(bitmap.height)
        size = RenderSize(width = w, height = h)
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val srcW = bitmap.width
        val srcH = bitmap.height
        if (srcW <= 0 || srcH <= 0) return

        val copyW = srcW.coerceAtMost(size.width)
        val copyH = srcH.coerceAtMost(size.height)
        if (copyW <= 0 || copyH <= 0) return

        val target = context.buffer
        val targetW = target.width
        val targetH = target.height
        var dstY = offsetY
        var srcY = 0
        // 行循环裁剪到目标 buffer 的可见区域，避免 ArrayIndexOutOfBounds。
        while (srcY < copyH) {
            if (dstY in 0 until targetH) {
                val rowStartX = maxOf(0, offsetX)
                val rowEndX = minOf(targetW, offsetX + copyW)
                if (rowEndX > rowStartX) {
                    val skipLeft = rowStartX - offsetX
                    val srcOffset = srcY * srcW + skipLeft
                    val dstOffset = dstY * targetW + rowStartX
                    val rowLen = rowEndX - rowStartX
                    System.arraycopy(bitmap.pixels, srcOffset, target.pixels, dstOffset, rowLen)
                }
            }
            dstY++
            srcY++
        }
    }
}
