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
