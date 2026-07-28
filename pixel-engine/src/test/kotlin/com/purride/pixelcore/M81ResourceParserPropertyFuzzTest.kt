package com.purride.pixelcore

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M8-1 资源解析属性测试，固定种子后可完整复现失败样本。 */
class M81ResourceParserPropertyFuzzTest {
    /** 随机生成合法 catalog，并验证摘要校验、顺序和二次解析结果完全一致。 */
    @Test
    fun validCatalogsRoundTripDeterministically() {
        /** 本测试固定使用的随机种子。 */
        val seed = 2026071401L
        /** 只由固定种子驱动的伪随机源。 */
        val random = Random(seed)

        repeat(300) { iteration ->
            /** 当前轮生成的合法 catalog JSON。 */
            val json = validCatalogJson(random, iteration)
            /** 与当前 UTF-8 输入严格对应的 SHA-256。 */
            val digest = sha256(json.toByteArray(Charsets.UTF_8))
            /** 第一次解析得到的 catalog。 */
            val first = PixelResourceManifestJsonLoader.parseCatalog(json, digest)
            /** 同一输入的第二次解析结果。 */
            val second = PixelResourceManifestJsonLoader.parseCatalog(json, digest)

            assertEquals(first, second)
            assertEquals(PixelResourceManifestVersion, first.resources.version)
            assertTrue(first.resources.bitmaps.isNotEmpty())
            /** 当前 catalog 中可以被 sprite sheet 引用的 bitmap id。 */
            val bitmapIds = first.resources.bitmaps.map { definition -> definition.id }.toSet()
            assertTrue(first.resources.spriteSheets.all { definition -> definition.bitmap in bitmapIds })
            /** 所有资源类型拼接后的全局 id。 */
            val allIds = buildList {
                addAll(first.resources.bitmaps.map { definition -> definition.id })
                addAll(first.resources.spriteSheets.map { definition -> definition.id })
                addAll(first.colors.map { definition -> definition.id })
                addAll(first.fonts.map { definition -> definition.id })
            }
            assertEquals(allIds.size, allIds.toSet().size)
        }
    }

    /** 任意有界文本必须成功为合法 manifest，或只抛出稳定的公开解析异常。 */
    @Test
    fun arbitraryManifestTextHasATotalPublicOutcome() {
        /** 本测试固定使用的随机种子。 */
        val seed = 2026071402L
        /** 只由固定种子驱动的伪随机源。 */
        val random = Random(seed)
        /** 覆盖 JSON 结构、转义、空白、数字和普通字符的字母表。 */
        val alphabet = "{}[],:\\\"/.-+ abcdefABCDEF0123456789\\n\\t"

        repeat(2_000) { iteration ->
            /** 当前随机文本长度；迭代号参与上界以覆盖空串和渐增长度。 */
            val length = random.nextInt(1 + (iteration % 257))
            /** 当前轮不可信 manifest 文本。 */
            val text = buildString(length) {
                repeat(length) {
                    append(alphabet[random.nextInt(alphabet.length)])
                }
            }
            try {
                /** 少量随机文本可能恰好构成合法 manifest。 */
                val manifest = PixelResourceManifestJsonLoader.parse(text)
                assertEquals(PixelResourceManifestVersion, manifest.version)
                assertEquals(
                    manifest.bitmaps.size,
                    manifest.bitmaps.map { definition -> definition.id }.toSet().size,
                )
                assertEquals(
                    manifest.spriteSheets.size,
                    manifest.spriteSheets.map { definition -> definition.id }.toSet().size,
                )
            } catch (error: PixelResourceManifestLoadException) {
                assertTrue(
                    "seed=$seed iteration=$iteration 的解析错误必须包含诊断信息",
                    error.message.orEmpty().isNotBlank(),
                )
            }
        }
    }

    /** 合法随机 glyph 二进制必须保持记录顺序、尺寸和压缩像素完全一致。 */
    @Test
    fun validGlyphBinariesRoundTripDeterministically() {
        /** 本测试固定使用的随机种子。 */
        val seed = 2026071403L
        /** 只由固定种子驱动的伪随机源。 */
        val random = Random(seed)
        /** 所有生成二进制共享的固定字形 manifest。 */
        val manifest = glyphManifest()

        repeat(300) { iteration ->
            /** 当前轮字形数量。 */
            val glyphCount = 1 + random.nextInt(24)
            /** 当前轮按 code point 顺序生成的字形记录。 */
            val records = List(glyphCount) { index ->
                /** 当前字形宽度。 */
                val width = 1 + random.nextInt(12)
                /** 当前字形的精确压缩字节数。 */
                val packedLength = width
                /** 当前字形的确定性随机像素。 */
                val pixels = ByteArray(packedLength).also(random::nextBytes)
                BinaryGlyph(
                    codePoint = 0x20 + index,
                    advanceWidth = 1 + random.nextInt(12),
                    width = width,
                    pixels = pixels,
                )
            }
            /** 当前轮编码后的完整 PGLY 文件。 */
            val bytes = glyphBinary(manifest.cellHeight, records)
            /** 经公开解析器还原的字形包。 */
            val parsed = PixelGlyphPackParser.parseBinary(
                manifest,
                ByteArrayInputStream(bytes),
                sha256(bytes),
            )

            assertEquals("seed=$seed iteration=$iteration", records.size, parsed.glyphs.size)
            records.forEach { expected ->
                /** 与期望 code point 对应的解析记录。 */
                val actual = checkNotNull(parsed.glyphs[expected.codePoint])
                assertEquals(expected.advanceWidth, actual.advanceWidth)
                assertEquals(expected.width, actual.width)
                assertArrayEquals(expected.pixels, actual.packedPixels)
            }
        }
    }

    /** 任意有界二进制必须成功为合法 glyph 包，或只抛出稳定的公开解析异常。 */
    @Test
    fun arbitraryGlyphBytesHaveATotalPublicOutcome() {
        /** 本测试固定使用的随机种子。 */
        val seed = 2026071404L
        /** 只由固定种子驱动的伪随机源。 */
        val random = Random(seed)
        /** 任意二进制解析使用的固定 manifest。 */
        val manifest = glyphManifest()

        repeat(2_000) { iteration ->
            /** 当前轮任意输入字节。 */
            val bytes = ByteArray(random.nextInt(513)).also(random::nextBytes)
            try {
                /** 极低概率下输入可能恰好符合完整协议。 */
                val parsed = PixelGlyphPackParser.parseBinary(manifest, ByteArrayInputStream(bytes))
                assertTrue(parsed.glyphs.size <= PixelResourceSafetyLimits.MaxGlyphCount)
                assertTrue(parsed.glyphs.all { (codePoint, record) -> codePoint == record.codePoint })
            } catch (error: PixelGlyphPackLoadException) {
                assertTrue(
                    "seed=$seed iteration=$iteration 的 glyph 错误必须包含诊断信息",
                    error.message.orEmpty().isNotBlank(),
                )
            }
        }
    }

    /** 构造一份含所有资源类型、全局 id 唯一的合法 catalog。 */
    private fun validCatalogJson(random: Random, iteration: Int): String {
        /** 当前轮 bitmap 数量。 */
        val bitmapCount = 1 + random.nextInt(8)
        /** 当前轮 sprite sheet 数量。 */
        val sheetCount = random.nextInt(8)
        /** 当前轮颜色数量。 */
        val colorCount = random.nextInt(8)
        /** 当前轮字体数量。 */
        val fontCount = random.nextInt(5)
        /** 保留声明顺序的 bitmap JSON。 */
        val bitmaps = (0 until bitmapCount).joinToString(",") { index ->
            "{\"id\":\"bitmap-$iteration-$index\",\"path\":\"images/$iteration/$index.png\"}"
        }
        /** 只引用本轮已存在 bitmap 的 sprite sheet JSON。 */
        val sheets = (0 until sheetCount).joinToString(",") { index ->
            /** 当前 sheet 随机选择的合法 bitmap 索引。 */
            val bitmapIndex = random.nextInt(bitmapCount)
            "{\"id\":\"sheet-$iteration-$index\",\"path\":\"sheets/$iteration/$index.json\"," +
                "\"bitmap\":\"bitmap-$iteration-$bitmapIndex\"}"
        }
        /** 当前轮命名颜色 JSON。 */
        val colors = (0 until colorCount).joinToString(",") { index ->
            /** 当前颜色的无符号 RGB 部分。 */
            val rgb = random.nextInt(0x1000000)
            "{\"id\":\"color-$iteration-$index\",\"value\":\"#%06X\"}".format(rgb)
        }
        /** 当前轮字体资源 JSON。 */
        val fonts = (0 until fontCount).joinToString(",") { index ->
            "{\"id\":\"font-$iteration-$index\",\"manifest\":\"fonts/$index/manifest.json\"," +
                "\"binary\":\"fonts/$index/glyphs.bin\"}"
        }
        return "{" +
            "\"version\":1," +
            "\"metadata\":{\"seed\":\"2026071401\",\"iteration\":\"$iteration\"}," +
            "\"bitmaps\":[$bitmaps]," +
            "\"spriteSheets\":[$sheets]," +
            "\"colors\":[$colors]," +
            "\"fonts\":[$fonts]" +
            "}"
    }

    /** 构造固定单元高度的字形 manifest。 */
    private fun glyphManifest(): PixelGlyphPackManifest = PixelGlyphPackManifest(
        packId = "m8-1-property",
        displayName = "M8-1 Property",
        cellHeight = 8,
        baseline = 7,
        defaultAdvance = 6,
        supportedRanges = listOf("0020-007F"),
    )

    /** 按 PGLY v1 协议编码一组字形记录。 */
    private fun glyphBinary(cellHeight: Int, records: List<BinaryGlyph>): ByteArray {
        /** 收集编码结果的内存流。 */
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(0x50474C59)
            data.writeInt(1)
            data.writeInt(cellHeight)
            data.writeInt(records.size)
            records.forEach { record ->
                data.writeInt(record.codePoint)
                data.writeInt(record.advanceWidth)
                data.writeInt(record.width)
                data.writeInt(record.pixels.size)
                data.write(record.pixels)
            }
        }
        return output.toByteArray()
    }

    /** 计算小写十六进制 SHA-256。 */
    private fun sha256(bytes: ByteArray): String {
        /** JDK 提供的 SHA-256 摘要器。 */
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    /** 属性测试内部使用的一条未编码字形记录。 */
    private data class BinaryGlyph(
        /** Unicode scalar。 */
        val codePoint: Int,
        /** 水平 advance。 */
        val advanceWidth: Int,
        /** 位图宽度。 */
        val width: Int,
        /** 已按行打包的像素字节。 */
        val pixels: ByteArray,
    )
}
