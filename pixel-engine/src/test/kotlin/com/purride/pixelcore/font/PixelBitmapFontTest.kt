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

    /** 无拼接相邻测量必须与普通完整字符串测量保持完全一致。 */
    @Test
    fun adjacentMeasurementMatchesCombinedText() {
        /** 覆盖窄字形、空格、fallback 和 supplementary scalar 的片段组合。 */
        val pairs = listOf(
            "A" to "B",
            "A " to "a",
            "你" to "A",
            "🙂" to "B",
        )

        pairs.forEach { (first, second) ->
            assertEquals(
                font.measureText(first + second),
                font.measureAdjacentText(first, second),
            )
        }
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
            color = PixelColor.White,
        )

        assertTrue((0 until 7).any { y -> buffer.getPixel(0, y) != PixelColor.Transparent })
        assertTrue((9 until 16).any { y -> buffer.getPixel(0, y) != PixelColor.Transparent })
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
            color = PixelColor.White,
        )

        assertEquals(9, compactFont.measureText("AB"))
        assertEquals(5, compactFont.measureHeight("AB"))
        assertTrue((0 until buffer.height).any { y ->
            (0 until buffer.width).any { x -> buffer.getPixel(x, y) != PixelColor.Transparent }
        })
    }

    @Test
    fun fontMetricsExposeBuiltinBitmapBaseline() {
        val metrics = font.fontMetrics("A")

        assertEquals(7, metrics.cellHeight)
        assertEquals(6, metrics.baseline)
        assertEquals(6, metrics.ascent)
        assertEquals(1, metrics.descent)
        assertEquals(0, metrics.inkTop)
        assertEquals(6, metrics.inkBottom)
    }
}
