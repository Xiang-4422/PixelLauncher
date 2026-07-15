package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelBufferPool
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelTextStyle

/** 按段落布局生成的视觉簇绘制文本，不重新拆分 UTF-16 字符单元。 */
internal object PixelParagraphPainter {

    /** 按已经解析的视觉簇顺序绘制一个样式一致的文本运行段。 */
    fun drawRun(
        /** 当前帧的目标像素缓冲。 */
        buffer: PixelBuffer,
        /** 当前帧共享的临时缓冲池。 */
        bufferPool: PixelBufferPool,
        /** 待绘制的样式一致运行段。 */
        run: PixelParagraphRun,
        /** 样式未指定栅格器时使用的默认实现。 */
        defaultTextRasterizer: PixelTextRasterizer,
        /** 运行段左上角的横坐标。 */
        x: Int,
        /** 运行段左上角的纵坐标。 */
        y: Int,
    ) {
        /** 接收完整簇或单标量兜底内容的实际栅格器。 */
        val rasterizer = run.style.textRasterizer ?: defaultTextRasterizer
        /** 同时应用于原始字形和兜底字形的运行段颜色。 */
        val color = run.style.color
        /** 下一个完整视觉簇的左边界。 */
        var cursorX = x
        run.clusters.forEach { cluster ->
            if (cluster.renderText.isEmpty()) {
                cursorX += cluster.width
                return@forEach
            }
            if (run.style.usesPlainRasterizer()) {
                rasterizer.drawText(
                    buffer = buffer,
                    text = cluster.renderText,
                    x = cursorX,
                    y = y,
                    color = color,
                )
                cursorX += cluster.width
                return@forEach
            }
            /** 由可支持原文或单个兜底字形测得的未缩放簇宽度。 */
            val glyphWidth = rasterizer.measureText(cluster.renderText).coerceAtLeast(1)
            /** 临时字形位图使用的未缩放簇高度。 */
            val glyphHeight = rasterizer.measureHeight(cluster.renderText).coerceAtLeast(1)
            /** 从帧级池借出的独立 glyph 位图，避免每个缩放簇分配新像素数组。 */
            val scratch = bufferPool.acquire(width = glyphWidth, height = glyphHeight)
            try {
                rasterizer.drawText(
                    buffer = scratch,
                    text = cluster.renderText,
                    x = 0,
                    y = 0,
                    color = color,
                )
                blitScaledGlyph(
                    source = scratch,
                    destination = buffer,
                    destX = cursorX,
                    destY = y,
                    scale = run.style.fontScale.coerceAtLeast(1),
                    color = color,
                )
            } finally {
                bufferPool.release(scratch)
            }
            cursorX += cluster.width
        }
    }

    /** 判断栅格器能否直接绘制已经合并的运行段。 */
    private fun PixelTextStyle.usesPlainRasterizer(): Boolean {
        return letterSpacing <= 0 && fontScale <= 1
    }

    /** 使用整数最近邻缩放复制一个簇位图。 */
    private fun blitScaledGlyph(
        source: PixelBuffer,
        destination: PixelBuffer,
        destX: Int,
        destY: Int,
        scale: Int,
        color: PixelColor,
    ) {
        /** 当前源位图行。 */
        for (row in 0 until source.height) {
            /** 当前源位图列。 */
            for (column in 0 until source.width) {
                /** 以透明度决定是否输出缩放像素块的源像素。 */
                val pixel = source.getPixel(column, row)
                if (pixel.alpha > 0) {
                    repeat(scale) { scaleY ->
                        repeat(scale) { scaleX ->
                            destination.setPixel(
                                x = destX + column * scale + scaleX,
                                y = destY + row * scale + scaleY,
                                color = color,
                            )
                        }
                    }
                }
            }
        }
    }
}
