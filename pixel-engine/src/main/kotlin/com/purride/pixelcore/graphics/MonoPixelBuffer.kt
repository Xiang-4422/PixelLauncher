package com.purride.pixelcore

import kotlin.math.max
import kotlin.math.min

/**
 * 单色像素帧缓冲。
 *
 * 内部存储为 ByteArray，每个逻辑像素保存一个 [PixelTone] 的字节值。
 * 字节布局与原 PixelBuffer 完全一致，保证 golden 测试零字节差异。
 *
 * 公开 API 使用 [PixelTone] 类型；内核层渲染路径（字形栅格化等热路径）
 * 可通过 [setPixelByte] / [getPixelByte] 直接操作字节，避免枚举查找开销。
 */
public class MonoPixelBuffer(
    override val width: Int,
    override val height: Int,
) : PixelBuffer {

    public val pixels: ByteArray = ByteArray(width * height)

    // ── PixelTone 公开 API ────────────────────────────────────────────────

    public fun setPixel(x: Int, y: Int, tone: PixelTone) {
        setPixelByte(x, y, tone.value)
    }

    public fun getPixel(x: Int, y: Int): PixelTone {
        val b = getPixelByte(x, y)
        return when (b) {
            PixelTone.ON.value -> PixelTone.ON
            PixelTone.ACCENT.value -> PixelTone.ACCENT
            else -> PixelTone.OFF
        }
    }

    // ── Byte 内部 API（供栅格化热路径使用）─────────────────────────────────

    internal fun setPixelByte(x: Int, y: Int, value: Byte) {
        if (x !in 0 until width || y !in 0 until height) return
        pixels[(y * width) + x] = value
    }

    internal fun getPixelByte(x: Int, y: Int): Byte {
        if (x !in 0 until width || y !in 0 until height) return PixelTone.OFF.value
        return pixels[(y * width) + x]
    }

    // ── 矩形绘制 ─────────────────────────────────────────────────────────

    public fun fillRect(
        left: Int,
        top: Int,
        rectWidth: Int,
        rectHeight: Int,
        tone: PixelTone,
    ) {
        val startX = left.coerceIn(0, width)
        val startY = top.coerceIn(0, height)
        val endX = (left + rectWidth).coerceIn(startX, width)
        val endY = (top + rectHeight).coerceIn(startY, height)
        for (y in startY until endY) {
            for (x in startX until endX) {
                setPixelByte(x, y, tone.value)
            }
        }
    }

    public fun drawRect(
        left: Int,
        top: Int,
        rectWidth: Int,
        rectHeight: Int,
        tone: PixelTone,
    ) {
        if (rectWidth <= 0 || rectHeight <= 0) return
        val right = left + rectWidth - 1
        val bottom = top + rectHeight - 1
        for (x in left..right) {
            setPixelByte(x, top, tone.value)
            setPixelByte(x, bottom, tone.value)
        }
        for (y in top..bottom) {
            setPixelByte(left, y, tone.value)
            setPixelByte(right, y, tone.value)
        }
    }

    // ── PixelBuffer sealed interface ──────────────────────────────────────

    override fun clear() {
        pixels.fill(PixelTone.OFF.value)
    }

    override fun blit(source: PixelBuffer, destX: Int, destY: Int) {
        when (source) {
            is MonoPixelBuffer -> blitMono(source, destX, destY)
            is ColorPixelBuffer -> throw UnsupportedOperationException(
                "Cannot blit Color buffer into Mono buffer",
            )
        }
    }

    /**
     * 带完整源偏移的 blit 重载，供文本渲染等内部路径使用。
     */
    public fun blit(
        source: MonoPixelBuffer,
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
        if (safeCopyWidth <= 0 || safeCopyHeight <= 0) return

        val destinationStartX = max(0, destX)
        val destinationStartY = max(0, destY)
        val sourceStartX = safeSourceX + max(0, -destX)
        val sourceStartY = safeSourceY + max(0, -destY)
        val destinationEndX = min(width, destX + safeCopyWidth)
        val destinationEndY = min(height, destY + safeCopyHeight)
        val actualWidth = destinationEndX - destinationStartX
        val actualHeight = destinationEndY - destinationStartY
        if (actualWidth <= 0 || actualHeight <= 0) return

        val sourceStride = source.width
        val destinationStride = width
        for (row in 0 until actualHeight) {
            val sourceOffset = (sourceStartY + row) * sourceStride + sourceStartX
            val destinationOffset = (destinationStartY + row) * destinationStride + destinationStartX
            System.arraycopy(source.pixels, sourceOffset, pixels, destinationOffset, actualWidth)
        }
    }

    public fun copy(): MonoPixelBuffer {
        val copy = MonoPixelBuffer(width = width, height = height)
        pixels.copyInto(copy.pixels)
        return copy
    }

    // ── 私有辅助 ──────────────────────────────────────────────────────────

    private fun blitMono(source: MonoPixelBuffer, destX: Int, destY: Int) {
        blit(source = source, destX = destX, destY = destY)
    }
}
