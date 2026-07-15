package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies that the complete font pipeline retains supplementary Unicode scalar keys. */
class PixelCodePointFontEngineTest {
    /** Compact synthetic style whose narrow and wide advances are intentionally distinguishable. */
    private val style = GlyphStyle(
        cellHeight = 4,
        narrowAdvanceWidth = 3,
        wideAdvanceWidth = 6,
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

    /** A supplementary smiling face used as one two-unit UTF-16 source scalar. */
    private val supplementaryText = String(Character.toChars(SupplementaryCodePoint))

    /** Proves a supplementary pack record is looked up, measured, cached, trimmed and painted once. */
    @Test
    fun supplementaryGlyphPackRecordFlowsThroughTheRealFontEngine() {
        /** Bitmap source containing one visibly asymmetric supplementary record. */
        val source = BitmapGlyphSource(packs = listOf(supplementaryPack()))
        /** Production composite provider preserving full scalar lookup. */
        val provider = CompositeGlyphProvider(sources = listOf(source))
        /** Font engine under test. */
        val engine = PixelFontEngine(provider)
        /** Destination proving the real record is painted instead of the fallback box. */
        val buffer = PixelBuffer(width = 8, height = 4)

        assertNotNull(source.findGlyph(SupplementaryCodePoint, style))
        assertEquals(6, engine.measureText(supplementaryText, style))
        assertEquals(12, engine.measureText(supplementaryText + supplementaryText, style))
        assertEquals(supplementaryText, engine.trimToWidth(supplementaryText + "A", style, 6))
        assertEquals("", engine.trimToWidth(supplementaryText, style, 5))

        engine.drawText(
            buffer = buffer,
            text = supplementaryText,
            startX = 0,
            startY = 0,
            maxWidth = 6,
            style = style,
        )

        assertEquals(PixelColor.White, buffer.getPixel(2, 1))
        assertEquals(PixelColor.Transparent, buffer.getPixel(0, 0))
        assertTrue(engine.glyphCacheStats().hits > 0L)
    }

    /** Proves the engine never narrows one supplementary scalar into two legacy source calls. */
    @Test
    fun codePointAwareSourceReceivesOneCompleteSupplementaryKey() {
        /** Source recording both modern scalar and legacy Char entry points. */
        val source = RecordingCodePointSource()
        /** Engine using the recording source through the production composite. */
        val engine = PixelFontEngine(CompositeGlyphProvider(listOf(source)))

        assertEquals(6, engine.measureText(supplementaryText, style))
        assertEquals(listOf(SupplementaryCodePoint), source.codePointRequests)
        assertEquals(emptyList<Char>(), source.legacyRequests)
    }

    /** Proves an old Char-only provider receives one replacement request, never surrogate halves. */
    @Test
    fun legacyCharProviderGetsOneDeterministicReplacementForSupplementaryText() {
        /** Frozen Char-only provider representing a pre-M5-3D binary consumer. */
        val provider = LegacyRecordingProvider()
        /** Current engine invoking the additive scalar default method. */
        val engine = PixelFontEngine(provider)

        assertEquals(6, engine.measureText(supplementaryText, style))
        assertEquals(listOf('\uFFFD'), provider.requests)
    }

    /** Proves malformed legacy source state paints one replacement cell without rewriting the text. */
    @Test
    fun isolatedSurrogateUsesOneReplacementGlyphAndKeepsSourceOffsets() {
        /** Old retained text containing one isolated high surrogate between valid characters. */
        val malformed = "A\uD83DB"
        /** Provider recording scalar-compatible legacy requests. */
        val provider = LegacyRecordingProvider()
        /** Current engine preserving the source string while measuring replacement ink. */
        val engine = PixelFontEngine(provider)

        assertEquals(12, engine.measureText(malformed, style))
        assertEquals(malformed.substring(0, 2), engine.trimToWidth(malformed, style, 9))
        assertEquals(listOf('A', '\uFFFD', 'B'), provider.requests)
    }

    /** Proves public scalar overloads reject surrogate and out-of-range aliases. */
    @Test
    fun publicCodePointLookupsRejectNonScalarValues() {
        /** Empty production provider used only to exercise argument validation. */
        val provider = CompositeGlyphProvider(emptyList())
        /** Empty bitmap source used only to exercise argument validation. */
        val source = BitmapGlyphSource(emptyList())

        assertThrows(IllegalArgumentException::class.java) {
            provider.rasterizeGlyph(0xD800, style)
        }
        assertThrows(IllegalArgumentException::class.java) {
            source.findGlyph(0x110000, style)
        }
    }

    /** Builds one pack whose supplementary bitmap contains only pixel `(2, 1)`. */
    private fun supplementaryPack(): PixelGlyphPack {
        /** Unpacked four-row, six-column synthetic bitmap. */
        val pixels = ByteArray(24).also { bitmap -> bitmap[(1 * 6) + 2] = 1 }
        return PixelGlyphPack(
            manifest = PixelGlyphPackManifest(
                packId = "supplementary-synthetic",
                displayName = "Supplementary Synthetic",
                cellHeight = 4,
                baseline = 3,
                defaultAdvance = 6,
                supportedRanges = listOf("1F642-1F642"),
            ),
            glyphs = mapOf(
                SupplementaryCodePoint to PackedGlyphRecord(
                    codePoint = SupplementaryCodePoint,
                    advanceWidth = 6,
                    width = 6,
                    packedPixels = packBits(pixels),
                ),
            ),
        )
    }

    /** Packs row-major one-byte pixels into the glyph-pack MSB-first representation. */
    private fun packBits(pixels: ByteArray): ByteArray {
        /** Packed output with one bit per source pixel. */
        val packed = ByteArray((pixels.size + 7) / 8)
        pixels.forEachIndexed { index, value ->
            if (value.toInt() != 0) {
                packed[index / 8] =
                    (packed[index / 8].toInt() or (1 shl (7 - (index % 8)))).toByte()
            }
        }
        return packed
    }

    /** Source overriding the additive scalar API while retaining the old SPI method. */
    private class RecordingCodePointSource : GlyphSource {
        /** Complete scalar requests observed from the engine. */
        val codePointRequests: MutableList<Int> = mutableListOf()

        /** Legacy BMP requests, which must remain empty for supplementary input. */
        val legacyRequests: MutableList<Char> = mutableListOf()

        /** Records accidental legacy calls and declines the glyph. */
        override fun findGlyph(character: Char, style: GlyphStyle): GlyphBitmap? {
            legacyRequests += character
            return null
        }

        /** Records the complete scalar and returns a distinguishable wide glyph. */
        override fun findGlyph(codePoint: Int, style: GlyphStyle): GlyphBitmap? {
            codePointRequests += codePoint
            return filledGlyph(width = style.wideAdvanceWidth, style = style)
        }
    }

    /** Pre-M5-3D provider implementing only the frozen Char method. */
    private class LegacyRecordingProvider : GlyphProvider {
        /** Exact compatibility characters requested by the additive default method. */
        val requests: MutableList<Char> = mutableListOf()

        /** Records one BMP/replacement request and returns width based on ASCII classification. */
        override fun rasterizeGlyph(character: Char, style: GlyphStyle): GlyphBitmap {
            requests += character
            /** Replacement and other non-ASCII values use the wide fallback cell. */
            val width = if (character.code in 32..126) {
                style.narrowAdvanceWidth
            } else {
                style.wideAdvanceWidth
            }
            return filledGlyph(width = width, style = style)
        }
    }

    private companion object {
        /** Supplementary scalar whose UTF-16 representation contains two surrogate code units. */
        const val SupplementaryCodePoint: Int = 0x1F642

        /** Creates one solid synthetic bitmap with stable metrics. */
        fun filledGlyph(width: Int, style: GlyphStyle): GlyphBitmap {
            return GlyphBitmap(
                width = width,
                height = style.cellHeight,
                pixels = ByteArray(width * style.cellHeight) { 1 },
                metrics = GlyphMetrics(
                    advanceWidth = width,
                    baselineOffset = style.cellHeight - 1,
                    isWideGlyph = width > style.narrowAdvanceWidth,
                    requiresVisualGapProtection = false,
                    inkLeft = 0,
                    inkRight = width - 1,
                ),
            )
        }
    }
}
