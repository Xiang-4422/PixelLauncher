package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelTextStyle

internal object PixelParagraphPainter {

    fun drawRun(
        buffer: PixelBuffer,
        run: PixelParagraphRun,
        defaultTextRasterizer: PixelTextRasterizer,
        x: Int,
        y: Int,
    ) {
        val rasterizer = run.style.textRasterizer ?: defaultTextRasterizer
        val color = run.style.color
        if (run.style.usesPlainRasterizer()) {
            rasterizer.drawText(buffer = buffer, text = run.text, x = x, y = y, color = color)
            return
        }

        var cursorX = x
        run.text.forEach { character ->
            val glyphText = character.toString()
            val glyphWidth = rasterizer.measureText(glyphText).coerceAtLeast(1)
            val glyphHeight = rasterizer.measureHeight(glyphText.ifEmpty { " " }).coerceAtLeast(1)
            val scratch = PixelBuffer(width = glyphWidth, height = glyphHeight)
            rasterizer.drawText(buffer = scratch, text = glyphText, x = 0, y = 0, color = color)
            blitScaledGlyph(
                source = scratch,
                destination = buffer,
                destX = cursorX,
                destY = y,
                scale = run.style.fontScale.coerceAtLeast(1),
                color = color,
            )
            cursorX += (glyphWidth * run.style.fontScale.coerceAtLeast(1)) +
                run.style.letterSpacing.coerceAtLeast(0)
        }
    }

    private fun PixelTextStyle.usesPlainRasterizer(): Boolean {
        return letterSpacing <= 0 && fontScale <= 1
    }

    private fun blitScaledGlyph(
        source: PixelBuffer,
        destination: PixelBuffer,
        destX: Int,
        destY: Int,
        scale: Int,
        color: PixelColor,
    ) {
        for (row in 0 until source.height) {
            for (column in 0 until source.width) {
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
