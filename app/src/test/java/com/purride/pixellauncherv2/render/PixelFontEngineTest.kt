package com.purride.pixellauncherv2.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelFontEngineTest {

    private val appLabelStyle = GlyphStyle(
        cellHeight = 16,
        narrowAdvanceWidth = 8,
        wideAdvanceWidth = 16,
        oversampleFactor = 1,
        narrowMinimumSampleRatio = 1f,
        wideMinimumSampleRatio = 1f,
        narrowTextSizeRatio = 1f,
        wideTextSizeRatio = 1f,
        narrowFontWeight = PixelFontWeight.NORMAL,
        wideFontWeight = PixelFontWeight.NORMAL,
        narrowFontFamily = PixelFontFamily.MONOSPACE,
        wideFontFamily = PixelFontFamily.DEFAULT,
    )

    @Test
    fun measureTextReturnsStableWidthsForAsciiChineseAndMixedText() {
        val glyphProvider = CountingGlyphProvider()
        val pixelFontEngine = PixelFontEngine(glyphProvider)

        assertEquals(48, pixelFontEngine.measureText("WeChat", appLabelStyle))
        assertEquals(33, pixelFontEngine.measureText("\u5fae\u4fe1", appLabelStyle))
        assertEquals(89, pixelFontEngine.measureText("\u5fae\u4fe1 WeChat", appLabelStyle))
    }

    @Test
    fun trimToWidthUsesPixelWidthInsteadOfCharacterCount() {
        val glyphProvider = CountingGlyphProvider()
        val pixelFontEngine = PixelFontEngine(glyphProvider)

        val trimmed = pixelFontEngine.trimToWidth(
            text = "\u5fae\u4fe1WeChat",
            style = appLabelStyle,
            maxWidth = 40,
        )

        assertEquals("\u5fae\u4fe1", trimmed)
    }

    @Test
    fun trimToWidthKeepsSingleCjkGlyphWhenOnlyBaseWidthFits() {
        val glyphProvider = CountingGlyphProvider()
        val pixelFontEngine = PixelFontEngine(glyphProvider)

        val trimmed = pixelFontEngine.trimToWidth(
            text = "\u4e2d\u6587",
            style = appLabelStyle,
            maxWidth = 16,
        )

        assertEquals("\u4e2d", trimmed)
    }

    @Test
    fun repeatedCharactersHitGlyphCache() {
        val glyphProvider = CountingGlyphProvider()
        val pixelFontEngine = PixelFontEngine(glyphProvider)

        pixelFontEngine.measureText("AAAA", appLabelStyle)

        assertEquals(1, glyphProvider.rasterizeCount)
    }

    @Test
    fun asciiAndWideGlyphsUseDifferentAdvanceWidths() {
        val glyphProvider = CountingGlyphProvider()
        val pixelFontEngine = PixelFontEngine(glyphProvider)

        assertEquals(8, pixelFontEngine.measureText("A", appLabelStyle))
        assertEquals(16, pixelFontEngine.measureText("\u4e2d", appLabelStyle))
    }

    @Test
    fun cjkSpacingOnlyAppliesBetweenAdjacentCjkGlyphs() {
        val glyphProvider = CountingGlyphProvider()
        val pixelFontEngine = PixelFontEngine(glyphProvider)

        assertEquals(33, pixelFontEngine.measureText("\u4e2d\u6587", appLabelStyle))
        assertEquals(24, pixelFontEngine.measureText("\u4e2dA", appLabelStyle))
        assertEquals(24, pixelFontEngine.measureText("A\u4e2d", appLabelStyle))
    }

    @Test
    fun drawTextLeavesBlankColumnBetweenAdjacentCjkGlyphs() {
        val glyphProvider = CountingGlyphProvider()
        val pixelFontEngine = PixelFontEngine(glyphProvider)
        val buffer = PixelBuffer(width = 40, height = 20)

        pixelFontEngine.drawText(
            buffer = buffer,
            text = "\u4e2d\u6587",
            startX = 0,
            startY = 0,
            maxWidth = 40,
            style = appLabelStyle,
        )

        for (y in 0 until appLabelStyle.cellHeight) {
            assertEquals(PixelBuffer.OFF, buffer.getPixel(16, y))
        }
        assertTrue((0 until appLabelStyle.cellHeight).all { y -> buffer.getPixel(17, y) != PixelBuffer.OFF })
    }

    private class CountingGlyphProvider : GlyphProvider {
        var rasterizeCount: Int = 0
            private set

        override fun rasterizeGlyph(character: Char, style: GlyphStyle): GlyphBitmap {
            rasterizeCount += 1
            val isWideGlyph = character.code !in 32..126
            val width = if (isWideGlyph) style.wideAdvanceWidth else style.narrowAdvanceWidth

            return GlyphBitmap(
                width = width,
                height = style.cellHeight,
                pixels = ByteArray(width * style.cellHeight) { 1 },
                metrics = GlyphMetrics(
                    advanceWidth = width,
                    baselineOffset = style.cellHeight - 2,
                    isWideGlyph = isWideGlyph,
                ),
            )
        }
    }
}
