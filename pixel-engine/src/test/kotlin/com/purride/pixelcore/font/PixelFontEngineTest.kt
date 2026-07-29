package com.purride.pixelcore

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

    private val monoStyle = appLabelStyle.copy(
        narrowAdvanceWidth = 16,
        wideAdvanceWidth = 16,
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
    fun visualGapAppliesToAnyAdjacentPairContainingWideGlyph() {
        val glyphProvider = CountingGlyphProvider()
        val pixelFontEngine = PixelFontEngine(glyphProvider)

        assertEquals(33, pixelFontEngine.measureText("\u4e2d\u6587", appLabelStyle))
        assertEquals(25, pixelFontEngine.measureText("\u4e2dA", appLabelStyle))
        assertEquals(25, pixelFontEngine.measureText("A\u4e2d", appLabelStyle))
        assertEquals(16, pixelFontEngine.measureText("AA", appLabelStyle))
    }

    @Test
    fun monoStyleStillKeepsGapForChineseAndMixedPairs() {
        val glyphProvider = CountingGlyphProvider()
        val pixelFontEngine = PixelFontEngine(glyphProvider)

        assertEquals(33, pixelFontEngine.measureText("\u4e2d\u6587", monoStyle))
        assertEquals(33, pixelFontEngine.measureText("\u4e2dA", monoStyle))
        assertEquals(33, pixelFontEngine.measureText("A\u4e2d", monoStyle))
        assertEquals(32, pixelFontEngine.measureText("AA", monoStyle))
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
            assertEquals(PixelColor.Transparent, buffer.getPixel(16, y))
        }
        assertTrue((0 until appLabelStyle.cellHeight).all { y -> buffer.getPixel(17, y) != PixelColor.Transparent })
    }

    @Test
    fun drawTextLeavesBlankColumnBetweenWideAndNarrowGlyphs() {
        val glyphProvider = CountingGlyphProvider()
        val pixelFontEngine = PixelFontEngine(glyphProvider)
        val buffer = PixelBuffer(width = 40, height = 20)

        pixelFontEngine.drawText(
            buffer = buffer,
            text = "\u4e2dA",
            startX = 0,
            startY = 0,
            maxWidth = 40,
            style = appLabelStyle,
        )

        for (y in 0 until appLabelStyle.cellHeight) {
            assertEquals(PixelColor.Transparent, buffer.getPixel(16, y))
        }
        assertTrue((0 until appLabelStyle.cellHeight).all { y -> buffer.getPixel(17, y) != PixelColor.Transparent })
    }

    @Test
    fun missingGlyphFallbackPaintsVisibleBoxWithNarrowOrWideAdvance() {
        val pixelFontEngine = PixelFontEngine(CompositeGlyphProvider(emptyList()))
        val buffer = PixelBuffer(width = 32, height = 20)

        assertEquals(8, pixelFontEngine.measureText("$", appLabelStyle))
        assertEquals(16, pixelFontEngine.measureText("\u4e2d", appLabelStyle))

        pixelFontEngine.drawText(
            buffer = buffer,
            text = "$\u4e2d",
            startX = 0,
            startY = 0,
            maxWidth = 32,
            style = appLabelStyle,
        )

        assertTrue((0 until appLabelStyle.cellHeight).any { y -> buffer.getPixel(0, y) != PixelColor.Transparent })
        assertTrue((0 until appLabelStyle.cellHeight).any { y -> buffer.getPixel(9, y) != PixelColor.Transparent })
    }

    @Test
    fun missingWhitespaceFallbackKeepsAdvanceButNoInk() {
        val pixelFontEngine = PixelFontEngine(CompositeGlyphProvider(emptyList()))
        val buffer = PixelBuffer(width = 8, height = 16)

        assertEquals(8, pixelFontEngine.measureText(" ", appLabelStyle))
        pixelFontEngine.drawText(
            buffer = buffer,
            text = " ",
            startX = 0,
            startY = 0,
            maxWidth = 8,
            style = appLabelStyle,
        )

        assertTrue(buffer.pixels.all { it == PixelColor.Transparent.argb })
    }

    @Test
    fun fontMetricsExposeBaselineAscentDescentAndInkBounds() {
        val pixelFontEngine = PixelFontEngine(CountingGlyphProvider())

        val metrics = pixelFontEngine.fontMetrics(text = "A\u4e2d", style = appLabelStyle)

        assertEquals(16, metrics.cellHeight)
        assertEquals(14, metrics.baseline)
        assertEquals(14, metrics.ascent)
        assertEquals(2, metrics.descent)
        assertEquals(0, metrics.inkTop)
        assertEquals(15, metrics.inkBottom)
    }

    /** 绘制与字体度量必须使用 V2 placement，而不是把位图强行贴到行框左上角。 */
    @Test
    fun drawAndMetricsHonorBitmapPlacement() {
        val provider = object : GlyphProvider {
            /** 返回一个位于光标左侧且高于行顶的单像素字形。 */
            override fun rasterizeGlyph(codePoint: Int, style: GlyphStyle): GlyphBitmap = GlyphBitmap(
                width = 1,
                height = 1,
                pixels = byteArrayOf(1),
                metrics = GlyphMetrics(
                    advanceWidth = 4,
                    baselineOffset = 7,
                    isWideGlyph = false,
                    inkLeft = -1,
                    inkRight = -1,
                    bitmapOffsetX = -1,
                    bitmapOffsetY = -2,
                ),
            )
        }
        val engine = PixelFontEngine(provider)
        val buffer = PixelBuffer(width = 8, height = 8)

        engine.drawText(
            buffer = buffer,
            text = "A",
            startX = 2,
            startY = 2,
            maxWidth = 4,
            style = appLabelStyle,
        )

        assertEquals(PixelColor.fromRgb(255, 255, 255), buffer.getPixel(1, 0))
        assertEquals(-2, engine.fontMetrics("A", appLabelStyle).inkTop)
    }

    @Test
    fun bitmapGlyphSourceCachesMetricsPerStyle() {
        val source = BitmapGlyphSource(
            packs = listOf(
                PixelGlyphPack(
                    manifest = PixelGlyphPackManifest(
                        packId = "sample",
                        displayName = "Sample",
                        cellHeight = 16,
                        baseline = 13,
                        defaultAdvance = 8,
                        supportedRanges = listOf("0041-0041"),
                    ),
                    glyphs = mapOf(
                        0x0041 to PackedGlyphRecord(
                            codePoint = 0x0041,
                            advanceWidth = 8,
                            width = 8,
                            packedPixels = ByteArray(16) { 0xFF.toByte() },
                        ),
                    ),
                ),
            ),
        )

        val narrowGlyph = source.findGlyph('A'.code, appLabelStyle)
        val wideGlyph = source.findGlyph('A'.code, appLabelStyle.copy(narrowAdvanceWidth = 4))

        assertEquals(false, narrowGlyph?.metrics?.isWideGlyph)
        assertEquals(true, wideGlyph?.metrics?.isWideGlyph)
    }

    @Test
    fun compositeGlyphProviderFallsBackAcrossSourcesThenToEmptyGlyph() {
        val provider = CompositeGlyphProvider(
            sources = listOf(
                FixedGlyphSource(codePoint = 'A'.code, width = 8),
                FixedGlyphSource(codePoint = 'B'.code, width = 7),
            ),
        )

        val firstSourceGlyph = provider.rasterizeGlyph('A'.code, appLabelStyle)
        val secondSourceGlyph = provider.rasterizeGlyph('B'.code, appLabelStyle)
        val emptyFallbackGlyph = provider.rasterizeGlyph('C'.code, appLabelStyle)

        assertEquals(8, firstSourceGlyph.metrics.advanceWidth)
        assertEquals(7, secondSourceGlyph.metrics.advanceWidth)
        assertEquals(appLabelStyle.narrowAdvanceWidth, emptyFallbackGlyph.metrics.advanceWidth)
        assertTrue(emptyFallbackGlyph.pixels.any { pixel -> pixel.toInt() != 0 })
    }

    private class CountingGlyphProvider : GlyphProvider {
        var rasterizeCount: Int = 0
            private set

        override fun rasterizeGlyph(codePoint: Int, style: GlyphStyle): GlyphBitmap {
            rasterizeCount += 1
            val isWideGlyph = codePoint !in 32..126
            val width = if (isWideGlyph) style.wideAdvanceWidth else style.narrowAdvanceWidth
            val isBlankGlyph = codePoint == ' '.code

            return GlyphBitmap(
                width = width,
                height = style.cellHeight,
                pixels = ByteArray(width * style.cellHeight) { if (isBlankGlyph) 0 else 1 },
                metrics = GlyphMetrics(
                    advanceWidth = width,
                    baselineOffset = style.cellHeight - 2,
                    isWideGlyph = isWideGlyph,
                    requiresVisualGapProtection = isWideGlyph || codePoint !in 32..126,
                    inkLeft = if (isBlankGlyph) width else 0,
                    inkRight = if (isBlankGlyph) -1 else width - 1,
                ),
            )
        }
    }

    private class FixedGlyphSource(
        private val codePoint: Int,
        private val width: Int,
    ) : GlyphSource {
        override fun findGlyph(codePoint: Int, style: GlyphStyle): GlyphBitmap? {
            if (this.codePoint != codePoint) return null
            return GlyphBitmap(
                width = width,
                height = style.cellHeight,
                pixels = ByteArray(width * style.cellHeight) { 1 },
                metrics = GlyphMetrics(
                    advanceWidth = width,
                    baselineOffset = style.cellHeight - 2,
                    isWideGlyph = width > style.narrowAdvanceWidth,
                    inkLeft = 0,
                    inkRight = width - 1,
                ),
            )
        }
    }
}
