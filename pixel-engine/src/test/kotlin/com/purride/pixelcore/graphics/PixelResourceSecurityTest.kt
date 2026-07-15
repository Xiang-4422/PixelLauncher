package com.purride.pixelcore

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证资源对象不可变性和不可信 JSON/二进制输入的安全失败边界。 */
class PixelResourceSecurityTest {
    /** 有界读取面对零进度批量流也必须前进，并继续执行字节上限。 */
    @Test
    fun boundedReadHandlesZeroProgressWithoutInfiniteLoop() {
        /** 批量读取始终返回零、单字节读取正常前进的流。 */
        val stream = ZeroBulkReadInputStream(byteArrayOf(1, 2, 3))
        /** 通过单字节退化路径读出的完整内容。 */
        val bytes = stream.readBoundedBytes(maxBytes = 3, label = "zero-progress")

        assertArrayEquals(byteArrayOf(1, 2, 3), bytes)
        /** 超出上限时必须停止并失败。 */
        val error = expectThrows<IllegalArgumentException> {
            ZeroBulkReadInputStream(byteArrayOf(1, 2, 3))
                .readBoundedBytes(maxBytes = 2, label = "limited")
        }
        assertTrue(error.message.orEmpty().contains("byte limit 2"))
    }

    /** PixelBitmap 构造输入和公开 getter 都必须使用 defensive copy。 */
    @Test
    fun pixelBitmapDoesNotExposeMutablePixelStorage() {
        /** 调用方持有的原始数组。 */
        val source = intArrayOf(0x11223344)
        /** 从原始数组构造的不可变 bitmap。 */
        val bitmap = PixelBitmap(width = 1, height = 1, pixels = source)
        source[0] = 0
        /** 公开 getter 返回的独立副本。 */
        val exposed = bitmap.pixels
        exposed[0] = -1

        assertEquals(0x11223344, bitmap.pixelAt(0, 0))
        assertArrayEquals(intArrayOf(0x11223344), bitmap.pixels)
    }

    /** PackedGlyphRecord 构造输入和公开 getter 都不得修改内部压缩字节。 */
    @Test
    fun packedGlyphRecordDoesNotExposeMutableBytes() {
        /** 调用方持有的压缩字节。 */
        val source = byteArrayOf(0x55)
        /** 从原始字节构造的字形记录。 */
        val record = PackedGlyphRecord(
            codePoint = 0x41,
            advanceWidth = 1,
            width = 1,
            packedPixels = source,
        )
        source[0] = 0
        /** 公开 getter 返回的独立副本。 */
        val exposed = record.packedPixels
        exposed[0] = 0

        assertArrayEquals(byteArrayOf(0x55), record.packedPixels)
        assertArrayEquals(byteArrayOf(0x55), record.component4())
    }

    /** 严格 JSON 解析必须拒绝重复 key 和根对象后的尾随内容。 */
    @Test
    fun manifestRejectsDuplicateKeysAndTrailingGarbage() {
        /** 重复 version key 的解析错误。 */
        val duplicate = expectThrows<PixelResourceManifestLoadException> {
            PixelResourceManifestJsonLoader.parse("""{"version":1,"version":1}""")
        }
        /** 合法对象后附加非空白内容的解析错误。 */
        val trailing = expectThrows<PixelResourceManifestLoadException> {
            PixelResourceManifestJsonLoader.parse("""{"version":1}garbage""")
        }

        assertTrue(duplicate.message.orEmpty().contains("Duplicate JSON key 'version'"))
        assertTrue(trailing.message.orEmpty().contains("trailing JSON"))
    }

    /** 资源路径必须拒绝父目录穿越和绝对路径。 */
    @Test
    fun manifestRejectsUnsafeResourcePaths() {
        /** 父目录穿越错误。 */
        val traversal = expectThrows<PixelResourceManifestLoadException> {
            PixelResourceManifestJsonLoader.parse(
                """{"bitmaps":[{"id":"icon","path":"../private/icon.png"}]}""",
            )
        }
        /** 绝对路径错误。 */
        val absolute = expectThrows<PixelResourceManifestLoadException> {
            PixelResourceManifestJsonLoader.parse(
                """{"bitmaps":[{"id":"icon","path":"/private/icon.png"}]}""",
            )
        }

        assertTrue(traversal.message.orEmpty().contains("unsafe path segment"))
        assertTrue(absolute.message.orEmpty().contains("must be relative"))
    }

    /** 外部摘要不一致时必须在构建资源对象前失败。 */
    @Test
    fun manifestRejectsChecksumMismatch() {
        /** 与输入不可能匹配的合法格式摘要。 */
        val wrongDigest = "0".repeat(64)
        /** 摘要不匹配错误。 */
        val error = expectThrows<PixelResourceManifestLoadException> {
            PixelResourceManifestJsonLoader.parse("""{"version":1}""", wrongDigest)
        }

        assertTrue(error.message.orEmpty().contains("SHA-256 mismatch"))
    }

    /** 解析器必须在深层结构和超长字符串继续扩张前停止。 */
    @Test
    fun manifestRejectsExcessiveNestingAndStringLength() {
        /** 超过 32 层的未知字段数组。 */
        val nested = "{\"unknown\":" + "[".repeat(40) + "0" + "]".repeat(40) + "}"
        /** 超过单字符串限制的资源 id。 */
        val longId = "a".repeat(16_385)
        /** 深度限制错误。 */
        val nestingError = expectThrows<PixelResourceManifestLoadException> {
            PixelResourceManifestJsonLoader.parse(nested)
        }
        /** 字符串限制错误。 */
        val stringError = expectThrows<PixelResourceManifestLoadException> {
            PixelResourceManifestJsonLoader.parse(
                """{"bitmaps":[{"id":"$longId","path":"icon.png"}]}""",
            )
        }

        assertTrue(nestingError.message.orEmpty().contains("nesting exceeds"))
        assertTrue(stringError.message.orEmpty().contains("string exceeds"))
    }

    /** sprite 坐标相加不得发生 Int 回绕。 */
    @Test
    fun spriteSheetRejectsCoordinateOverflow() {
        /** left + width 溢出的 sheet 错误。 */
        val error = expectThrows<PixelSpriteSheetLoadException> {
            PixelSpriteSheetJsonLoader.parseDefinition(
                """
                {
                  "bitmap":"atlas.png",
                  "frames":[{"left":2147483647,"top":0,"width":1,"height":1}]
                }
                """.trimIndent(),
            )
        }

        assertTrue(error.message.orEmpty().contains("left + width overflows Int"))
    }

    /** glyph 头中的负数或超量 count 必须在集合预分配前失败。 */
    @Test
    fun glyphBinaryRejectsInvalidCountsBeforeAllocation() {
        /** 通用测试 manifest。 */
        val manifest = glyphManifest()
        /** 负数字形数量错误。 */
        val negative = expectThrows<PixelGlyphPackLoadException> {
            PixelGlyphPackParser.parseBinary(
                manifest,
                ByteArrayInputStream(binaryHeader(glyphCount = -1)),
            )
        }
        /** 超过固定上限的字形数量错误。 */
        val excessive = expectThrows<PixelGlyphPackLoadException> {
            PixelGlyphPackParser.parseBinary(
                manifest,
                ByteArrayInputStream(binaryHeader(glyphCount = 131_073)),
            )
        }

        assertTrue(negative.message.orEmpty().contains("glyph count -1"))
        assertTrue(excessive.message.orEmpty().contains("glyph count 131073"))
    }

    /** glyph 数据长度必须与尺寸精确匹配，不可信声明不能直接驱动分配。 */
    @Test
    fun glyphBinaryRejectsDeclaredLengthMismatch() {
        /** 声明 Int 最大长度但不携带像素的恶意记录。 */
        val binary = glyphBinary(
            glyphs = listOf(BinaryGlyph(codePoint = 0x41, dataLength = Int.MAX_VALUE)),
        )
        /** 精确长度校验错误。 */
        val error = expectThrows<PixelGlyphPackLoadException> {
            PixelGlyphPackParser.parseBinary(glyphManifest(), ByteArrayInputStream(binary))
        }

        assertTrue(error.message.orEmpty().contains("does not match expected 1"))
    }

    /** 重复 code point、尾随字节和截断文件都必须确定性失败。 */
    @Test
    fun glyphBinaryRejectsDuplicateTrailingAndTruncatedInput() {
        /** 两条相同 code point 的二进制。 */
        val duplicateBytes = glyphBinary(
            glyphs = listOf(BinaryGlyph(0x41), BinaryGlyph(0x41)),
        )
        /** 合法单条记录后附加一个字节。 */
        val trailingBytes = glyphBinary(listOf(BinaryGlyph(0x41))) + byteArrayOf(1)
        /** 重复字形错误。 */
        val duplicate = expectThrows<PixelGlyphPackLoadException> {
            PixelGlyphPackParser.parseBinary(glyphManifest(), ByteArrayInputStream(duplicateBytes))
        }
        /** 尾随数据错误。 */
        val trailing = expectThrows<PixelGlyphPackLoadException> {
            PixelGlyphPackParser.parseBinary(glyphManifest(), ByteArrayInputStream(trailingBytes))
        }
        /** 截断文件错误。 */
        val truncated = expectThrows<PixelGlyphPackLoadException> {
            PixelGlyphPackParser.parseBinary(
                glyphManifest(),
                ByteArrayInputStream(byteArrayOf(0x50, 0x47)),
            )
        }

        assertTrue(duplicate.message.orEmpty().contains("duplicate glyph codePoint"))
        assertTrue(trailing.message.orEmpty().contains("trailing bytes"))
        assertTrue(truncated.message.orEmpty().contains("truncated input"))
    }

    /** glyph 二进制摘要必须在协议解析前完成校验。 */
    @Test
    fun glyphBinaryRejectsChecksumMismatch() {
        /** 一条合法字形记录。 */
        val binary = glyphBinary(listOf(BinaryGlyph(0x41)))
        /** 与输入不匹配的合法格式摘要。 */
        val wrongDigest = "f".repeat(64)
        /** 摘要不匹配错误。 */
        val error = expectThrows<PixelGlyphPackLoadException> {
            PixelGlyphPackParser.parseBinary(
                glyphManifest(),
                ByteArrayInputStream(binary),
                wrongDigest,
            )
        }

        assertTrue(error.message.orEmpty().contains("SHA-256 mismatch"))
    }

    /** 构造固定 1x8 单元的字形 manifest。 */
    private fun glyphManifest(): PixelGlyphPackManifest = PixelGlyphPackManifest(
        packId = "security",
        displayName = "Security",
        cellHeight = 8,
        baseline = 7,
        defaultAdvance = 1,
        supportedRanges = listOf("0041-0041"),
    )

    /** 只写入 PGLY 头，用于恶意 count 测试。 */
    private fun binaryHeader(glyphCount: Int): ByteArray {
        /** 收集二进制结果的内存流。 */
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(0x50474C59)
            data.writeInt(1)
            data.writeInt(8)
            data.writeInt(glyphCount)
        }
        return output.toByteArray()
    }

    /** 构造带固定宽度 1、单元高 8 的 PGLY 二进制。 */
    private fun glyphBinary(glyphs: List<BinaryGlyph>): ByteArray {
        /** 收集二进制结果的内存流。 */
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(binaryHeader(glyphs.size))
            glyphs.forEach { glyph ->
                data.writeInt(glyph.codePoint)
                data.writeInt(1)
                data.writeInt(1)
                data.writeInt(glyph.dataLength)
                if (glyph.dataLength == 1) data.writeByte(0x80)
            }
        }
        return output.toByteArray()
    }

    /** 二进制测试记录，只暴露攻击面所需字段。 */
    private data class BinaryGlyph(
        /** Unicode scalar。 */
        val codePoint: Int,
        /** 声明的压缩字节长度。 */
        val dataLength: Int = 1,
    )

    /** 模拟不可信实现违反批量读取进度约定的输入流。 */
    private class ZeroBulkReadInputStream(
        /** 由单字节 read 依次返回的固定数据。 */
        private val bytes: ByteArray,
    ) : InputStream() {
        /** 下一个尚未返回的字节索引。 */
        private var index: Int = 0

        /** 单字节读取保证每次调用产生进度。 */
        override fun read(): Int {
            if (index >= bytes.size) return -1
            return bytes[index++].toInt() and 0xFF
        }

        /** 在数据尚未结束时故意返回零，覆盖防无限循环分支。 */
        override fun read(target: ByteArray, offset: Int, length: Int): Int {
            return if (index >= bytes.size) -1 else 0
        }
    }

    /** 执行代码并返回指定类型异常。 */
    private inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
        return try {
            block()
            error("Expected ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            if (error is T) error else throw error
        }
    }
}
