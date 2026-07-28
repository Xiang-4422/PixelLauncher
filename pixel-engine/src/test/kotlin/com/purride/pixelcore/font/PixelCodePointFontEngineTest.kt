package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证整条字体链路始终以完整 Unicode 标量为键，不丢失补充平面码位。 */
class PixelCodePointFontEngineTest {
    /** 窄宽 advance 刻意可区分的紧凑合成样式。 */
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

    /** 作为一个占两个 UTF-16 码元的源标量使用的补充平面笑脸。 */
    private val supplementaryText = String(Character.toChars(SupplementaryCodePoint))

    /** 证明补充平面字形记录的查找、测量、缓存、截断和绘制都各自只发生一次。 */
    @Test
    fun supplementaryGlyphPackRecordFlowsThroughTheRealFontEngine() {
        /** 含一条视觉上不对称的补充平面记录的位图字形来源。 */
        val source = BitmapGlyphSource(packs = listOf(supplementaryPack()))
        /** 保持完整标量查找的生产用组合提供器。 */
        val provider = CompositeGlyphProvider(sources = listOf(source))
        /** 被测字体引擎。 */
        val engine = PixelFontEngine(provider)
        /** 用于证明绘制的是真实记录而非兜底方框的目标缓冲。 */
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

    /** 证明引擎不会把一个补充平面标量拆成两次 source 查询。 */
    @Test
    fun codePointAwareSourceReceivesOneCompleteSupplementaryKey() {
        /** 记录每一次 canonical 标量入口调用的字形来源。 */
        val source = RecordingCodePointSource()
        /** 通过生产用组合提供器使用记录型来源的引擎。 */
        val engine = PixelFontEngine(CompositeGlyphProvider(listOf(source)))

        assertEquals(6, engine.measureText(supplementaryText, style))
        assertEquals(listOf(SupplementaryCodePoint), source.codePointRequests)
    }

    /** 证明 provider 收到的是完整补充平面标量，而不是替换字符。 */
    @Test
    fun providerReceivesCompleteSupplementaryScalar() {
        /** 精确记录引擎请求了哪些标量的提供器。 */
        val provider = RecordingScalarProvider()
        /** 调用 canonical 标量方法的当前引擎。 */
        val engine = PixelFontEngine(provider)

        assertEquals(6, engine.measureText(supplementaryText, style))
        assertEquals(listOf(SupplementaryCodePoint), provider.requests)
    }

    /** 证明畸形 UTF-16 输入只绘制一个替换字符单元，且不改写原文本。 */
    @Test
    fun isolatedSurrogateUsesOneReplacementGlyphAndKeepsSourceOffsets() {
        /** 在两个合法字符之间夹一个孤立高位代理项的调用方文本。 */
        val malformed = "A\uD83DB"
        /** 记录该畸形输入触发的全部标量请求的提供器。 */
        val provider = RecordingScalarProvider()
        /** 在测量替换字形墨迹时仍保持源字符串不变的当前引擎。 */
        val engine = PixelFontEngine(provider)

        assertEquals(12, engine.measureText(malformed, style))
        assertEquals(malformed.substring(0, 2), engine.trimToWidth(malformed, style, 9))
        assertEquals(listOf('A'.code, ReplacementCodePoint, 'B'.code), provider.requests)
    }

    /** 证明公开标量入口会拒绝代理项和越界别名。 */
    @Test
    fun publicCodePointLookupsRejectNonScalarValues() {
        /** 仅用于触发参数校验的空生产提供器。 */
        val provider = CompositeGlyphProvider(emptyList())
        /** 仅用于触发参数校验的空位图来源。 */
        val source = BitmapGlyphSource(emptyList())

        assertThrows(IllegalArgumentException::class.java) {
            provider.rasterizeGlyph(0xD800, style)
        }
        assertThrows(IllegalArgumentException::class.java) {
            source.findGlyph(0x110000, style)
        }
    }

    /** 构造一个补充平面位图只点亮像素 `(2, 1)` 的字形包。 */
    private fun supplementaryPack(): PixelGlyphPack {
        /** 解包后的四行六列合成位图。 */
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

    /** 把行主序的单字节像素打包成字形包使用的高位在前表示。 */
    private fun packBits(pixels: ByteArray): ByteArray {
        /** 每个源像素占一位的打包输出。 */
        val packed = ByteArray((pixels.size + 7) / 8)
        pixels.forEachIndexed { index, value ->
            if (value.toInt() != 0) {
                packed[index / 8] =
                    (packed[index / 8].toInt() or (1 shl (7 - (index % 8)))).toByte()
            }
        }
        return packed
    }

    /** 记录引擎查找过的每一个完整标量的字形来源。 */
    private class RecordingCodePointSource : GlyphSource {
        /** 从引擎观察到的完整标量请求。 */
        val codePointRequests: MutableList<Int> = mutableListOf()

        /** 记录完整标量并返回一个可区分的宽字形。 */
        override fun findGlyph(codePoint: Int, style: GlyphStyle): GlyphBitmap? {
            codePointRequests += codePoint
            return filledGlyph(width = style.wideAdvanceWidth, style = style)
        }
    }

    /** 记录经 canonical 入口请求的精确标量的提供器。 */
    private class RecordingScalarProvider : GlyphProvider {
        /** 引擎按分发顺序请求的完整标量。 */
        val requests: MutableList<Int> = mutableListOf()

        /** 记录一次标量请求，并按 ASCII 分类返回宽度。 */
        override fun rasterizeGlyph(codePoint: Int, style: GlyphStyle): GlyphBitmap {
            requests += codePoint
            /** 替换字符及其它非 ASCII 标量使用宽兜底单元。 */
            val width = if (codePoint in 32..126) {
                style.narrowAdvanceWidth
            } else {
                style.wideAdvanceWidth
            }
            return filledGlyph(width = width, style = style)
        }
    }

    private companion object {
        /** UTF-16 表示占两个代理项码元的补充平面标量。 */
        const val SupplementaryCodePoint: Int = 0x1F642

        /** 针对畸形 UTF-16 输入产生的确定性替换标量。 */
        const val ReplacementCodePoint: Int = 0xFFFD

        /** 创建一个度量稳定的实心合成位图。 */
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
