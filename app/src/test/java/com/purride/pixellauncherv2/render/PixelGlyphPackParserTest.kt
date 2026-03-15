package com.purride.pixellauncherv2.render

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PixelGlyphPackParserTest {

    @Test
    fun parsesGlyphPackAndUnpacksBitmapGlyphs() {
        val manifest = PixelGlyphPackParser.parseManifest(
            """
            {
              "packId": "sample_pack",
              "displayName": "Sample Pack",
              "cellHeight": 16,
              "baseline": 13,
              "defaultAdvance": 16,
              "supportedRanges": ["0041-0041", "4E2D-4E2D"]
            }
            """.trimIndent(),
        )

        val binary = buildBinary(
            glyphs = listOf(
                SampleGlyph(
                    codePoint = 0x0041,
                    advanceWidth = 8,
                    width = 8,
                    pixels = pixelGrid(
                        8,
                        "00011000",
                        "00100100",
                        "01000010",
                        "01111110",
                    ),
                ),
                SampleGlyph(
                    codePoint = 0x4E2D,
                    advanceWidth = 16,
                    width = 16,
                    pixels = pixelGrid(
                        16,
                        "0000000010000000",
                        "0011111111111100",
                        "0010000010000100",
                        "0011111111111100",
                    ),
                ),
            ),
        )

        val pack = PixelGlyphPackParser.parseBinary(
            manifest = manifest,
            inputStream = ByteArrayInputStream(binary),
        )
        val source = BitmapGlyphSource(listOf(pack))

        val asciiGlyph = source.findGlyph('A', GlyphStyle.APP_LABEL_16)
        val wideGlyph = source.findGlyph('\u4E2D', GlyphStyle.APP_LABEL_16)

        assertNotNull(asciiGlyph)
        assertNotNull(wideGlyph)
        assertEquals(8, asciiGlyph!!.width)
        assertEquals(16, wideGlyph!!.width)
        assertEquals(8, asciiGlyph.metrics.advanceWidth)
        assertEquals(16, wideGlyph.metrics.advanceWidth)
        assertArrayEquals(rowBits("00011000"), asciiGlyph.pixels.copyOfRange(0, 8))
        assertArrayEquals(rowBits("0011111111111100"), wideGlyph.pixels.copyOfRange(16, 32))
    }

    @Test
    fun bitmapGlyphSourceIgnoresMismatchedCellHeight() {
        val manifest = PixelGlyphPackParser.parseManifest(
            """
            {
              "packId": "sample_pack",
              "displayName": "Sample Pack",
              "cellHeight": 16,
              "baseline": 13,
              "defaultAdvance": 16,
              "supportedRanges": ["0041-0041"]
            }
            """.trimIndent(),
        )
        val binary = buildBinary(
            glyphs = listOf(
                SampleGlyph(
                    codePoint = 0x0041,
                    advanceWidth = 8,
                    width = 8,
                    pixels = pixelGrid(8, "11111111"),
                ),
            ),
        )

        val pack = PixelGlyphPackParser.parseBinary(manifest, ByteArrayInputStream(binary))
        val source = BitmapGlyphSource(listOf(pack))

        assertNull(source.findGlyph('A', GlyphStyle.UI_SMALL_10))
    }

    private fun buildBinary(glyphs: List<SampleGlyph>): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { dataOutput ->
            dataOutput.writeInt(0x50474C59)
            dataOutput.writeInt(1)
            dataOutput.writeInt(16)
            dataOutput.writeInt(glyphs.size)
            glyphs.forEach { glyph ->
                dataOutput.writeInt(glyph.codePoint)
                dataOutput.writeInt(glyph.advanceWidth)
                dataOutput.writeInt(glyph.width)
                val packed = packBits(glyph.pixels)
                dataOutput.writeInt(packed.size)
                dataOutput.write(packed)
            }
        }
        return output.toByteArray()
    }

    private fun packBits(pixels: ByteArray): ByteArray {
        val packed = ByteArray((pixels.size + 7) / 8)
        pixels.forEachIndexed { index, value ->
            if (value.toInt() == 1) {
                packed[index / 8] = (packed[index / 8].toInt() or (1 shl (7 - (index % 8)))).toByte()
            }
        }
        return packed
    }

    private fun pixelGrid(width: Int, vararg rows: String): ByteArray {
        val paddedRows = rows.toMutableList()
        repeat((16 - rows.size).coerceAtLeast(0)) {
            paddedRows += "0".repeat(width)
        }
        return paddedRows.joinToString(separator = "")
            .map { character -> if (character == '1') 1.toByte() else 0.toByte() }
            .toByteArray()
    }

    private fun rowBits(row: String): ByteArray {
        return row.map { character -> if (character == '1') 1.toByte() else 0.toByte() }
            .toByteArray()
    }

    private data class SampleGlyph(
        val codePoint: Int,
        val advanceWidth: Int,
        val width: Int,
        val pixels: ByteArray,
    )
}
