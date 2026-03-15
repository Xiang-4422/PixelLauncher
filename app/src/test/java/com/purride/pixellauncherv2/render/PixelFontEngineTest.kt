package com.purride.pixellauncherv2.render

import org.junit.Assert.assertEquals
import org.junit.Test

class PixelFontEngineTest {

    @Test
    fun measureTextReturnsStableWidthsForAsciiChineseAndMixedText() {
        val glyphProvider = CountingGlyphProvider()
        val pixelFontEngine = PixelFontEngine(glyphProvider)

        assertEquals(48, pixelFontEngine.measureText("WeChat", GlyphStyle.APP_LABEL_16))
        assertEquals(32, pixelFontEngine.measureText("\u5fae\u4fe1", GlyphStyle.APP_LABEL_16))
        assertEquals(88, pixelFontEngine.measureText("\u5fae\u4fe1 WeChat", GlyphStyle.APP_LABEL_16))
    }

    @Test
    fun trimToWidthUsesPixelWidthInsteadOfCharacterCount() {
        val glyphProvider = CountingGlyphProvider()
        val pixelFontEngine = PixelFontEngine(glyphProvider)

        val trimmed = pixelFontEngine.trimToWidth(
            text = "\u5fae\u4fe1WeChat",
            style = GlyphStyle.APP_LABEL_16,
            maxWidth = 40,
        )

        assertEquals("\u5fae\u4fe1W", trimmed)
    }

    @Test
    fun repeatedCharactersHitGlyphCache() {
        val glyphProvider = CountingGlyphProvider()
        val pixelFontEngine = PixelFontEngine(glyphProvider)

        pixelFontEngine.measureText("AAAA", GlyphStyle.APP_LABEL_16)

        assertEquals(1, glyphProvider.rasterizeCount)
    }

    @Test
    fun asciiAndWideGlyphsUseDifferentAdvanceWidths() {
        val glyphProvider = CountingGlyphProvider()
        val pixelFontEngine = PixelFontEngine(glyphProvider)

        assertEquals(8, pixelFontEngine.measureText("A", GlyphStyle.APP_LABEL_16))
        assertEquals(16, pixelFontEngine.measureText("\u4e2d", GlyphStyle.APP_LABEL_16))
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
