package com.purride.pixelcore

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** indexed pack 与既有 V1 解析和渲染结果的兼容测试。 */
class PixelIndexedGlyphPackTest {
    /** indexed 表示不得改变 advance、位图或基线。 */
    @Test
    fun indexedSourceMatchesV1Source() {
        val manifest = sampleManifest()
        val binary = buildBinary(listOf(0x41, 0x4E2D))
        val v1 = PixelGlyphPackParser.parseBinary(manifest, ByteArrayInputStream(binary))
        val indexed = PixelIndexedGlyphPackParser.parseBinary(manifest, ByteArrayInputStream(binary))
        val style = sampleStyle()
        val v1Source = BitmapGlyphSource(listOf(v1))
        val indexedSource = IndexedBitmapGlyphSource(listOf(indexed), maxUnpackedBytes = 512)

        listOf(0x41, 0x4E2D).forEach { codePoint ->
            val expected = requireNotNull(v1Source.findGlyph(codePoint, style))
            val actual = requireNotNull(indexedSource.findGlyph(codePoint, style))
            assertEquals(expected.width, actual.width)
            assertEquals(expected.height, actual.height)
            assertEquals(expected.metrics, actual.metrics)
            assertArrayEquals(expected.pixels, actual.pixels)
        }
        assertEquals(2, indexed.glyphCount)
        assertTrue(indexed.contains(0x4E2D))
    }

    /** indexed parser 必须拒绝无法二分检索的乱序输入。 */
    @Test(expected = IllegalArgumentException::class)
    fun indexedParserRejectsOutOfOrderCodePoints() {
        PixelIndexedGlyphPackParser.parseBinary(
            sampleManifest(),
            ByteArrayInputStream(buildBinary(listOf(0x4E2D, 0x41))),
        )
    }

    /** 创建两个固定宽度不同的测试字形。 */
    private fun buildBinary(codePoints: List<Int>): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(0x50474C59)
            data.writeInt(1)
            data.writeInt(8)
            data.writeInt(codePoints.size)
            codePoints.forEach { codePoint ->
                val width = if (codePoint == 0x41) 4 else 8
                val packed = ByteArray(width)
                packed[0] = 0xF0.toByte()
                data.writeInt(codePoint)
                data.writeInt(width)
                data.writeInt(width)
                data.writeInt(packed.size)
                data.write(packed)
            }
        }
        return output.toByteArray()
    }

    /** 创建与测试二进制匹配的 manifest。 */
    private fun sampleManifest(): PixelGlyphPackManifest = PixelGlyphPackManifest(
        packId = "indexed_sample",
        displayName = "Indexed Sample",
        cellHeight = 8,
        baseline = 7,
        defaultAdvance = 4,
        supportedRanges = listOf("0041-0041", "4E2D-4E2D"),
    )

    /** 创建只使用 bitmap pack 度量的固定样式。 */
    private fun sampleStyle(): GlyphStyle = GlyphStyle(
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
}
