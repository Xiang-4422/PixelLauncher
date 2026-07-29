package com.purride.pixellauncherv2.launcher

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelTextRasterizer

/**
 * 沙钟的种子渲染：把时间文本栅格化成一张与仿真场地等大的 [PixelBuffer]，
 * 供 PixelMatterCapture 捕获为粒子原位。
 *
 * 流程：1x 画字 → 整数倍最近邻放大（点阵美学允许且无损）→ 在场地内定位。
 * 数字锚定在场地**上三分之一**处：粒子坍塌需要下落空间，贴着底画就没有戏可看。
 */
object SandClockSeedRenderer {

    /** 数字最大占场宽比例。 */
    private const val MAX_WIDTH_RATIO = 0.8f

    /** 数字最大占场高比例。 */
    private const val MAX_HEIGHT_RATIO = 0.30f

    /** 数字垂直中心在场高的位置。 */
    private const val CENTER_Y_RATIO = 0.32f

    /**
     * 渲染时间种子。返回 null 表示无法产出可捕获的种子
     * （文本为空、场地太小连 1x 都放不下）。
     */
    fun renderTimeSeed(
        text: String,
        rasterizer: PixelTextRasterizer,
        fieldWidth: Int,
        fieldHeight: Int,
        color: PixelColor,
    ): PixelBuffer? {
        if (text.isBlank() || fieldWidth <= 0 || fieldHeight <= 0) return null
        val textWidth = rasterizer.measureText(text)
        val textHeight = rasterizer.measureHeight(text)
        if (textWidth <= 0 || textHeight <= 0) return null

        val scale = scaleFor(textWidth, textHeight, fieldWidth, fieldHeight)
        if (scale < 1) return null

        val small = PixelBuffer(width = textWidth, height = textHeight)
        rasterizer.drawText(small, text, x = 0, y = 0, color = color)
        val glyph = if (scale == 1) small else upscale(small, scale)

        val field = PixelBuffer(width = fieldWidth, height = fieldHeight)
        val left = ((fieldWidth - glyph.width) / 2).coerceAtLeast(0)
        val top = (fieldHeight * CENTER_Y_RATIO - glyph.height / 2f).toInt().coerceAtLeast(0)
        blit(source = glyph, target = field, left = left, top = top)
        return field
    }

    /** 放大倍数：宽高各自的预算取小者；场地放不下 1x 时返回 0。 */
    fun scaleFor(textWidth: Int, textHeight: Int, fieldWidth: Int, fieldHeight: Int): Int {
        if (textWidth <= 0 || textHeight <= 0) return 0
        val byWidth = (fieldWidth * MAX_WIDTH_RATIO).toInt() / textWidth
        val byHeight = (fieldHeight * MAX_HEIGHT_RATIO).toInt() / textHeight
        val scale = minOf(byWidth, byHeight)
        return if (scale >= 1) scale else if (textWidth <= fieldWidth && textHeight <= fieldHeight) 1 else 0
    }

    /** 整数倍最近邻放大：每个源像素扩成 factor×factor 块。 */
    fun upscale(source: PixelBuffer, factor: Int): PixelBuffer {
        require(factor >= 1) { "upscale factor must be >= 1, was $factor" }
        if (factor == 1) return source
        val target = PixelBuffer(width = source.width * factor, height = source.height * factor)
        for (y in 0 until source.height) {
            val sourceRow = y * source.width
            for (x in 0 until source.width) {
                val color = source.pixels[sourceRow + x]
                if (color == 0) continue
                val baseY = y * factor
                val baseX = x * factor
                for (dy in 0 until factor) {
                    val targetRow = (baseY + dy) * target.width
                    for (dx in 0 until factor) {
                        target.pixels[targetRow + baseX + dx] = color
                    }
                }
            }
        }
        return target
    }

    /** 把 [source] 原样拷进 [target]，越界部分裁掉。 */
    private fun blit(source: PixelBuffer, target: PixelBuffer, left: Int, top: Int) {
        for (y in 0 until source.height) {
            val targetY = top + y
            if (targetY !in 0 until target.height) continue
            val sourceRow = y * source.width
            val targetRow = targetY * target.width
            for (x in 0 until source.width) {
                val targetX = left + x
                if (targetX !in 0 until target.width) continue
                val color = source.pixels[sourceRow + x]
                if (color != 0) {
                    target.pixels[targetRow + targetX] = color
                }
            }
        }
    }
}
