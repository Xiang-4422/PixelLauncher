package com.purride.pixelcore

import kotlin.math.max
import kotlin.math.min

/**
 * 像素帧缓冲。
 *
 * 第一版依然采用最简单的单通道字节缓冲，每个逻辑像素只保存一个离散色阶值。
 * 这样既能保持内核层简单，也足够支撑当前 demo 的文字、表面、按钮和分页过渡。
 */
class PixelBuffer(
    val width: Int,
    val height: Int,
    val pixels: ByteArray = ByteArray(width * height),
) {

    fun clear(value: Byte = PixelTone.OFF.value) {
        pixels.fill(value)
    }

    fun setPixel(x: Int, y: Int, value: Byte = PixelTone.ON.value) {
        if (x !in 0 until width || y !in 0 until height) {
            return
        }
        pixels[(y * width) + x] = value
    }

    fun getPixel(x: Int, y: Int): Byte {
        if (x !in 0 until width || y !in 0 until height) {
            return PixelTone.OFF.value
        }
        return pixels[(y * width) + x]
    }

    fun fillRect(
        left: Int,
        top: Int,
        rectWidth: Int,
        rectHeight: Int,
        value: Byte,
    ) {
        val startX = left.coerceIn(0, width)
        val startY = top.coerceIn(0, height)
        val endX = (left + rectWidth).coerceIn(startX, width)
        val endY = (top + rectHeight).coerceIn(startY, height)
        for (y in startY until endY) {
            for (x in startX until endX) {
                setPixel(x, y, value)
            }
        }
    }

    fun drawRect(
        left: Int,
        top: Int,
        rectWidth: Int,
        rectHeight: Int,
        value: Byte,
    ) {
        if (rectWidth <= 0 || rectHeight <= 0) {
            return
        }
        val right = left + rectWidth - 1
        val bottom = top + rectHeight - 1
        for (x in left..right) {
            setPixel(x, top, value)
            setPixel(x, bottom, value)
        }
        for (y in top..bottom) {
            setPixel(left, y, value)
            setPixel(right, y, value)
        }
    }

    fun blit(
        source: PixelBuffer,
        destX: Int,
        destY: Int,
        sourceX: Int = 0,
        sourceY: Int = 0,
        copyWidth: Int = source.width,
        copyHeight: Int = source.height,
    ) {
        val safeSourceX = sourceX.coerceIn(0, source.width)
        val safeSourceY = sourceY.coerceIn(0, source.height)
        val safeCopyWidth = min(copyWidth, source.width - safeSourceX)
        val safeCopyHeight = min(copyHeight, source.height - safeSourceY)
        if (safeCopyWidth <= 0 || safeCopyHeight <= 0) {
            return
        }

        val destinationStartX = max(0, destX)
        val destinationStartY = max(0, destY)
        val sourceStartX = safeSourceX + max(0, -destX)
        val sourceStartY = safeSourceY + max(0, -destY)
        val destinationEndX = min(width, destX + safeCopyWidth)
        val destinationEndY = min(height, destY + safeCopyHeight)
        val actualWidth = destinationEndX - destinationStartX
        val actualHeight = destinationEndY - destinationStartY
        if (actualWidth <= 0 || actualHeight <= 0) {
            return
        }

        for (row in 0 until actualHeight) {
            for (column in 0 until actualWidth) {
                val value = source.getPixel(sourceStartX + column, sourceStartY + row)
                setPixel(destinationStartX + column, destinationStartY + row, value)
            }
        }
    }

    fun copy(): PixelBuffer {
        return PixelBuffer(
            width = width,
            height = height,
            pixels = pixels.copyOf(),
        )
    }
}

/**
 * 像素色阶。
 *
 * 第一版只保留三个档位：背景关闭、主内容、强调色。
 * 这套离散值会映射到调色板中的真实颜色。
 */
enum class PixelTone(val value: Byte) {
    OFF(0),
    ON(1),
    ACCENT(2),
}
