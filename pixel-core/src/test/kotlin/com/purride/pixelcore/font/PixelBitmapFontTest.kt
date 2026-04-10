package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelBitmapFontTest {

    private val font = PixelBitmapFont()

    @Test
    fun measureTextKeepsFiveBySevenBitmapSpacing() {
        assertEquals(5, font.measureText("A"))
        assertEquals(11, font.measureText("AA"))
        assertEquals(17, font.measureText("A A"))
        assertEquals(11, font.measureText("aA"))
    }

    @Test
    fun measureHeightAccountsForMultilineSpacing() {
        assertEquals(7, font.measureHeight("A"))
        assertEquals(16, font.measureHeight("A\nB"))
    }

    @Test
    fun drawTextSupportsMultilineAndLowercaseFallback() {
        val buffer = PixelBuffer(width = 24, height = 20)

        font.drawText(
            buffer = buffer,
            text = "a\n?",
            x = 0,
            y = 0,
        )

        assertTrue((0 until 7).any { y -> buffer.getPixel(0, y) != PixelTone.OFF.value })
        assertTrue((9 until 16).any { y -> buffer.getPixel(0, y) != PixelTone.OFF.value })
    }

    @Test
    fun customGlyphSizeDoesNotCrashAndDrawsPixels() {
        val compactFont = PixelBitmapFont(
            glyphWidth = 4,
            glyphHeight = 5,
            letterSpacing = 1,
            lineSpacing = 1,
        )
        val buffer = PixelBuffer(width = 16, height = 8)

        compactFont.drawText(
            buffer = buffer,
            text = "AB",
            x = 0,
            y = 0,
        )

        assertEquals(9, compactFont.measureText("AB"))
        assertEquals(5, compactFont.measureHeight("AB"))
        assertTrue((0 until buffer.height).any { y ->
            (0 until buffer.width).any { x -> buffer.getPixel(x, y) != PixelTone.OFF.value }
        })
    }
}
