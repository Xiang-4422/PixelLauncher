package com.purride.pixelcore

import java.io.DataInputStream
import java.io.InputStream

/**
 * 像素字形包清单。
 *
 * 这一层只描述“某个字形包里有哪些字、每个字占多高的单元格、默认前进宽度是多少”，
 * 不关心这些资源来自哪一个应用，也不关心具体由谁加载。
 */
data class PixelGlyphPackManifest(
    val packId: String,
    val displayName: String,
    val cellHeight: Int,
    val baseline: Int,
    val defaultAdvance: Int,
    val supportedRanges: List<String>,
)

/**
 * 压缩字形记录。
 *
 * `packedPixels` 采用按位压缩格式，真正的位图需要在消费端按宽高解包。
 */
data class PackedGlyphRecord(
    val codePoint: Int,
    val advanceWidth: Int,
    val width: Int,
    val packedPixels: ByteArray,
)

/**
 * 完整字形包。
 */
data class PixelGlyphPack(
    val manifest: PixelGlyphPackManifest,
    val glyphs: Map<Int, PackedGlyphRecord>,
)

/**
 * 像素字形包解析器。
 *
 * 第一版保持纯 Kotlin/JVM 可测，不依赖 Android JSON 或资源加载器。
 * 上层只需要把 manifest 文本和二进制流喂进来即可。
 */
object PixelGlyphPackParser {

    private const val MAGIC = 0x50474C59 // PGLY
    private const val VERSION = 1

    fun parseManifest(json: String): PixelGlyphPackManifest {
        return PixelGlyphPackManifest(
            packId = json.readString("packId"),
            displayName = json.readString("displayName"),
            cellHeight = json.readInt("cellHeight"),
            baseline = json.readInt("baseline"),
            defaultAdvance = json.readInt("defaultAdvance"),
            supportedRanges = json.readStringArray("supportedRanges"),
        )
    }

    fun parseBinary(manifest: PixelGlyphPackManifest, inputStream: InputStream): PixelGlyphPack {
        DataInputStream(inputStream).use { dataInput ->
            val magic = dataInput.readInt()
            require(magic == MAGIC) { "Unexpected glyph pack magic: $magic" }

            val version = dataInput.readInt()
            require(version == VERSION) { "Unsupported glyph pack version: $version" }

            val cellHeight = dataInput.readInt()
            require(cellHeight == manifest.cellHeight) {
                "Manifest cellHeight ${manifest.cellHeight} does not match binary $cellHeight"
            }

            val glyphCount = dataInput.readInt()
            val glyphs = LinkedHashMap<Int, PackedGlyphRecord>(glyphCount)
            repeat(glyphCount) {
                val codePoint = dataInput.readInt()
                val advanceWidth = dataInput.readInt()
                val width = dataInput.readInt()
                val dataLength = dataInput.readInt()
                val packedPixels = ByteArray(dataLength)
                dataInput.readFully(packedPixels)
                glyphs[codePoint] = PackedGlyphRecord(
                    codePoint = codePoint,
                    advanceWidth = advanceWidth,
                    width = width,
                    packedPixels = packedPixels,
                )
            }

            return PixelGlyphPack(
                manifest = manifest,
                glyphs = glyphs,
            )
        }
    }

    private fun String.readString(fieldName: String): String {
        val regex = Regex(""""$fieldName"\s*:\s*"([^"]*)"""")
        return regex.find(this)?.groupValues?.get(1)
            ?: error("Missing string field: $fieldName")
    }

    private fun String.readInt(fieldName: String): Int {
        val regex = Regex(""""$fieldName"\s*:\s*(\d+)""")
        return regex.find(this)?.groupValues?.get(1)?.toInt()
            ?: error("Missing int field: $fieldName")
    }

    private fun String.readStringArray(fieldName: String): List<String> {
        val regex = Regex(""""$fieldName"\s*:\s*\[(.*?)]""", setOf(RegexOption.DOT_MATCHES_ALL))
        val rawList = regex.find(this)?.groupValues?.get(1)
            ?: error("Missing array field: $fieldName")

        return Regex(""""([^"]*)"""")
            .findAll(rawList)
            .map { matchResult -> matchResult.groupValues[1] }
            .toList()
    }
}
