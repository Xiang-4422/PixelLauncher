package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelTextRasterizerTest {

    private val style = GlyphStyle(
        cellHeight = 8,
        narrowAdvanceWidth = 4,
        wideAdvanceWidth = 8,
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
    fun styledTextRasterizerMeasuresMultilineText() {
        val rasterizer = PixelStyledTextRasterizer(
            engine = PixelFontEngine(FillGlyphProvider()),
            style = style,
            lineSpacing = 2,
        )

        assertEquals(8, rasterizer.measureText("AA"))
        assertEquals(18, rasterizer.measureHeight("A\nB"))
    }

    @Test
    fun styledTextRasterizerDrawsUsingWrappedFontEngine() {
        val rasterizer = PixelStyledTextRasterizer(
            engine = PixelFontEngine(FillGlyphProvider()),
            style = style,
            lineSpacing = 2,
        )
        val buffer = PixelBuffer(width = 20, height = 20)

        rasterizer.drawText(
            buffer = buffer,
            text = "A",
            x = 0,
            y = 0,
        )

        assertTrue((0 until style.cellHeight).all { y -> buffer.getPixel(0, y) != PixelTone.OFF.value })
    }

    private class FillGlyphProvider : GlyphProvider {
        override fun rasterizeGlyph(character: Char, style: GlyphStyle): GlyphBitmap {
            return GlyphBitmap(
                width = style.narrowAdvanceWidth,
                height = style.cellHeight,
                pixels = ByteArray(style.narrowAdvanceWidth * style.cellHeight) { 1 },
                metrics = GlyphMetrics(
                    advanceWidth = style.narrowAdvanceWidth,
                    baselineOffset = style.cellHeight - 1,
                    isWideGlyph = false,
                    inkLeft = 0,
                    inkRight = style.narrowAdvanceWidth - 1,
                ),
            )
        }
    }
}
