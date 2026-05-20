package com.purride.pixelcore

/**
 * 彩色像素帧缓冲（Phase A.3 实现）。
 *
 * 内部存储为 IntArray，每个逻辑像素保存一个 ARGB 32-bit [PixelColor] 值。
 * 当前为 Phase A.2 编译占位，完整实现见 Phase A.3。
 */
public class ColorPixelBuffer(
    override val width: Int,
    override val height: Int,
) : PixelBuffer {

    public val pixels: IntArray = IntArray(width * height)

    public fun setPixel(x: Int, y: Int, color: PixelColor) {
        if (x !in 0 until width || y !in 0 until height) return
        pixels[(y * width) + x] = color.argb
    }

    public fun getPixel(x: Int, y: Int): PixelColor {
        if (x !in 0 until width || y !in 0 until height) return PixelColor.Transparent
        return PixelColor(pixels[(y * width) + x])
    }

    public fun fillRect(left: Int, top: Int, rectWidth: Int, rectHeight: Int, color: PixelColor) {
        val startX = left.coerceIn(0, width)
        val startY = top.coerceIn(0, height)
        val endX = (left + rectWidth).coerceIn(startX, width)
        val endY = (top + rectHeight).coerceIn(startY, height)
        val argb = color.argb
        for (y in startY until endY) {
            val base = y * width
            for (x in startX until endX) {
                pixels[base + x] = argb
            }
        }
    }

    public fun drawRect(left: Int, top: Int, rectWidth: Int, rectHeight: Int, color: PixelColor) {
        if (rectWidth <= 0 || rectHeight <= 0) return
        val right = left + rectWidth - 1
        val bottom = top + rectHeight - 1
        val argb = color.argb
        for (x in left..right) {
            if (x in 0 until width) {
                if (top in 0 until height) pixels[top * width + x] = argb
                if (bottom in 0 until height) pixels[bottom * width + x] = argb
            }
        }
        for (y in top..bottom) {
            if (y in 0 until height) {
                if (left in 0 until width) pixels[y * width + left] = argb
                if (right in 0 until width) pixels[y * width + right] = argb
            }
        }
    }

    override fun clear() {
        pixels.fill(PixelColor.Transparent.argb)
    }

    override fun blit(source: PixelBuffer, destX: Int, destY: Int) {
        when (source) {
            is ColorPixelBuffer -> blitColor(source, destX, destY)
            is MonoPixelBuffer -> throw UnsupportedOperationException(
                "Cannot blit Mono buffer into Color buffer",
            )
        }
    }

    private fun blitColor(source: ColorPixelBuffer, destX: Int, destY: Int) {
        val sourceStartX = 0.coerceAtLeast(-destX)
        val sourceStartY = 0.coerceAtLeast(-destY)
        val destinationStartX = destX.coerceAtLeast(0)
        val destinationStartY = destY.coerceAtLeast(0)
        val actualWidth = (source.width - sourceStartX).coerceAtMost(width - destinationStartX)
        val actualHeight = (source.height - sourceStartY).coerceAtMost(height - destinationStartY)
        if (actualWidth <= 0 || actualHeight <= 0) return

        for (row in 0 until actualHeight) {
            val srcOffset = (sourceStartY + row) * source.width + sourceStartX
            val dstOffset = (destinationStartY + row) * width + destinationStartX
            System.arraycopy(source.pixels, srcOffset, pixels, dstOffset, actualWidth)
        }
    }
}
