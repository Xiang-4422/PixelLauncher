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
    private var nonOpaquePixelCount: Int = width * height

    public fun setPixel(x: Int, y: Int, color: PixelColor) {
        if (x !in 0 until width || y !in 0 until height) return
        val index = y * width + x
        val argb = color.argb
        val result = when ((argb ushr 24) and 0xFF) {
            0 -> return
            0xFF -> argb
            else -> blendSrcOver(src = argb, dst = pixels[index])
        }
        writePixel(index, result)
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
        val alpha = (argb ushr 24) and 0xFF
        if (alpha == 0) return
        if (alpha == 0xFF) {
            for (y in startY until endY) {
                val base = y * width
                for (x in startX until endX) {
                    writePixel(base + x, argb)
                }
            }
            return
        }
        for (y in startY until endY) {
            val base = y * width
            for (x in startX until endX) {
                val index = base + x
                writePixel(index, blendSrcOver(src = argb, dst = pixels[index]))
            }
        }
    }

    public fun drawRect(left: Int, top: Int, rectWidth: Int, rectHeight: Int, color: PixelColor) {
        if (rectWidth <= 0 || rectHeight <= 0) return
        val right = left + rectWidth - 1
        val bottom = top + rectHeight - 1
        val argb = color.argb
        val alpha = (argb ushr 24) and 0xFF
        if (alpha == 0) return
        for (x in left..right) {
            if (x in 0 until width) {
                if (top in 0 until height) {
                    val index = top * width + x
                    writePixel(index, if (alpha == 0xFF) argb else blendSrcOver(src = argb, dst = pixels[index]))
                }
                if (bottom in 0 until height) {
                    val index = bottom * width + x
                    writePixel(index, if (alpha == 0xFF) argb else blendSrcOver(src = argb, dst = pixels[index]))
                }
            }
        }
        for (y in top..bottom) {
            if (y in 0 until height) {
                if (left in 0 until width) {
                    val index = y * width + left
                    writePixel(index, if (alpha == 0xFF) argb else blendSrcOver(src = argb, dst = pixels[index]))
                }
                if (right in 0 until width) {
                    val index = y * width + right
                    writePixel(index, if (alpha == 0xFF) argb else blendSrcOver(src = argb, dst = pixels[index]))
                }
            }
        }
    }

    public fun clear() {
        pixels.fill(PixelColor.Transparent.argb)
        nonOpaquePixelCount = width * height
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

        if (source.nonOpaquePixelCount == 0) {
            for (row in 0 until h) {
                val srcOffset = (srcY + row) * source.width + srcX
                val dstOffset = (dstY + row) * width + dstX
                System.arraycopy(source.pixels, srcOffset, pixels, dstOffset, w)
            }
            return
        }

        for (row in 0 until h) {
            val srcOffset = (srcY + row) * source.width + srcX
            val dstOffset = (dstY + row) * width + dstX
            for (blendColumn in 0 until w) {
                val dstIndex = dstOffset + blendColumn
                writePixel(dstIndex, blendSrcOver(src = source.pixels[srcOffset + blendColumn], dst = pixels[dstIndex]))
            }
        }
    }

    private fun writePixel(index: Int, argb: Int) {
        val oldOpaque = ((pixels[index] ushr 24) and 0xFF) == 0xFF
        val newOpaque = ((argb ushr 24) and 0xFF) == 0xFF
        if (oldOpaque && !newOpaque) nonOpaquePixelCount += 1
        if (!oldOpaque && newOpaque) nonOpaquePixelCount -= 1
        pixels[index] = argb
    }

    public companion object {
        public fun blendSrcOver(src: Int, dst: Int): Int {
            val sa = (src ushr 24) and 0xFF
            if (sa == 0) return dst
            if (sa == 0xFF) return src

            val da = (dst ushr 24) and 0xFF
            val sr = (src ushr 16) and 0xFF
            val sg = (src ushr 8) and 0xFF
            val sb = src and 0xFF
            val dr = (dst ushr 16) and 0xFF
            val dg = (dst ushr 8) and 0xFF
            val db = dst and 0xFF

            val invSa = 255 - sa
            val outA = sa + (da * invSa + 127) / 255
            if (outA == 0) return 0
            val srcPremulR = sr * sa
            val srcPremulG = sg * sa
            val srcPremulB = sb * sa
            val dstScale = (da * invSa + 127) / 255
            val outR = (srcPremulR + dr * dstScale + outA / 2) / outA
            val outG = (srcPremulG + dg * dstScale + outA / 2) / outA
            val outB = (srcPremulB + db * dstScale + outA / 2) / outA
            return (outA shl 24) or
                (outR.coerceIn(0, 255) shl 16) or
                (outG.coerceIn(0, 255) shl 8) or
                outB.coerceIn(0, 255)
        }
    }
}
