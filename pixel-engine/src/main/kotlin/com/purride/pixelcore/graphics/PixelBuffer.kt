package com.purride.pixelcore

/**
 * 像素帧缓冲。
 *
 * 每个逻辑像素存储一个 ARGB 32-bit [PixelColor] 值（0 = 透明）。
 * Engine 渲染层只使用此类型，不再区分单色 / 彩色模式。
 */
public class PixelBuffer(
    public val width: Int,
    public val height: Int,
) {
    public val pixels: IntArray = IntArray(width * height)

    public fun setPixel(x: Int, y: Int, color: PixelColor) {
        if (x !in 0 until width || y !in 0 until height) return
        pixels[y * width + x] = color.argb
    }

    public fun getPixel(x: Int, y: Int): PixelColor {
        if (x !in 0 until width || y !in 0 until height) return PixelColor.Transparent
        return PixelColor(pixels[y * width + x])
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

    public fun clear() {
        pixels.fill(PixelColor.Transparent.argb)
    }

    /**
     * 把 [source] buffer 的内容复制到当前 buffer 的 ([destX], [destY]) 位置。
     */
    public fun blit(source: PixelBuffer, destX: Int, destY: Int) {
        blitRegion(
            source = source,
            sourceX = 0,
            sourceY = 0,
            copyWidth = source.width,
            copyHeight = source.height,
            destX = destX,
            destY = destY,
        )
    }

    /**
     * 把 [source] buffer 指定区域复制到当前 buffer 的 ([destX], [destY]) 位置。
     *
     * 负的 [destX]/[destY] 表示目标偏移在缓冲左/上边界之外，此时从源的对应偏移位置开始
     * 复制（等效于"源被部分裁剪到可见区域"）。负的 [sourceX]/[sourceY] 同理。
     */
    public fun blitRegion(
        source: PixelBuffer,
        sourceX: Int,
        sourceY: Int,
        copyWidth: Int,
        copyHeight: Int,
        destX: Int,
        destY: Int,
    ) {
        var srcX = sourceX
        var srcY = sourceY
        var dstX = destX
        var dstY = destY
        var w = copyWidth
        var h = copyHeight

        // 目标左侧越界：从源的右侧偏移开始复制
        if (dstX < 0) { val skip = -dstX; srcX += skip; w -= skip; dstX = 0 }
        // 目标顶部越界：从源的下方偏移开始复制
        if (dstY < 0) { val skip = -dstY; srcY += skip; h -= skip; dstY = 0 }
        // 源左侧越界：目标右移
        if (srcX < 0) { val skip = -srcX; dstX += skip; w -= skip; srcX = 0 }
        // 源顶部越界：目标下移
        if (srcY < 0) { val skip = -srcY; dstY += skip; h -= skip; srcY = 0 }

        // 裁剪到源和目标的有效范围
        w = w.coerceAtMost(source.width - srcX).coerceAtMost(width - dstX)
        h = h.coerceAtMost(source.height - srcY).coerceAtMost(height - dstY)
        if (w <= 0 || h <= 0) return

        for (row in 0 until h) {
            val srcOffset = (srcY + row) * source.width + srcX
            val dstOffset = (dstY + row) * width + dstX
            System.arraycopy(source.pixels, srcOffset, pixels, dstOffset, w)
        }
    }
}
