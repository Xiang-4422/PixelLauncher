package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelcore.PixelTone
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
        if (run.style.usesPlainRasterizer()) {
            rasterizer.drawText(
                buffer = buffer,
                text = run.text,
                x = x,
                y = y,
                value = run.style.tone.value,
            )
            return
        }

        var cursorX = x
        run.text.forEach { character ->
            val glyphText = character.toString()
            val glyphWidth = rasterizer.measureText(glyphText).coerceAtLeast(1)
            val glyphHeight = rasterizer.measureHeight(glyphText.ifEmpty { " " }).coerceAtLeast(1)
            val scratch = PixelBuffer(width = glyphWidth, height = glyphHeight)
            rasterizer.drawText(
                buffer = scratch,
                text = glyphText,
                x = 0,
                y = 0,
                value = run.style.tone.value,
            )
            blitScaledGlyph(
                source = scratch,
                destination = buffer,
                destX = cursorX,
                destY = y,
                scale = run.style.fontScale.coerceAtLeast(1),
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
    ) {
        for (row in 0 until source.height) {
            for (column in 0 until source.width) {
                val value = source.getPixel(column, row)
                if (value == PixelTone.OFF.value) {
                    continue
                }
                repeat(scale) { scaleY ->
                    repeat(scale) { scaleX ->
                        destination.setPixel(
                            x = destX + column * scale + scaleX,
                            y = destY + row * scale + scaleY,
                            value = value,
                        )
                    }
                }
            }
        }
    }
}
