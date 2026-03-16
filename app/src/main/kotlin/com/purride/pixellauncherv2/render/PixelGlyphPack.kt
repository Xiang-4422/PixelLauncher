package com.purride.pixellauncherv2.render

import android.content.Context
import java.io.DataInputStream
import java.io.InputStream

enum class PixelGlyphPackId {
    ARK_PIXEL_10PX_MONOSPACED_LATIN,
    WEN_QUAN_YI_BITMAP_SONG_16PX,
    GNU_UNIFONT_17_0_03,
    ARK_PIXEL_16PX_MONOSPACED_ZH_CN,
    ARK_PIXEL_16PX_MONOSPACED_ZH_HK,
    ARK_PIXEL_16PX_MONOSPACED_ZH_TW,
    ARK_PIXEL_16PX_MONOSPACED_ZH_TR,
    ARK_PIXEL_16PX_PROPORTIONAL_ZH_CN,
    ARK_PIXEL_16PX_PROPORTIONAL_ZH_HK,
    ARK_PIXEL_16PX_PROPORTIONAL_ZH_TW,
    ARK_PIXEL_16PX_PROPORTIONAL_ZH_TR,
}

data class PixelGlyphPackDefinition(
    val id: PixelGlyphPackId,
    val assetDirectory: String,
)

object PixelGlyphPackCatalog {

    private val definitions = listOf(
        PixelGlyphPackDefinition(
            id = PixelGlyphPackId.ARK_PIXEL_10PX_MONOSPACED_LATIN,
            assetDirectory = "glyphpacks/ark_pixel_10px_monospaced_latin",
        ),
        PixelGlyphPackDefinition(
            id = PixelGlyphPackId.WEN_QUAN_YI_BITMAP_SONG_16PX,
            assetDirectory = "glyphpacks/wenquanyi_bitmap_song_16px",
        ),
        PixelGlyphPackDefinition(
            id = PixelGlyphPackId.GNU_UNIFONT_17_0_03,
            assetDirectory = "glyphpacks/gnu_unifont_17_0_03",
        ),
        PixelGlyphPackDefinition(
            id = PixelGlyphPackId.ARK_PIXEL_16PX_MONOSPACED_ZH_CN,
            assetDirectory = "glyphpacks/ark_pixel_16px_monospaced_zh_cn",
        ),
        PixelGlyphPackDefinition(
            id = PixelGlyphPackId.ARK_PIXEL_16PX_MONOSPACED_ZH_HK,
            assetDirectory = "glyphpacks/ark_pixel_16px_monospaced_zh_hk",
        ),
        PixelGlyphPackDefinition(
            id = PixelGlyphPackId.ARK_PIXEL_16PX_MONOSPACED_ZH_TW,
            assetDirectory = "glyphpacks/ark_pixel_16px_monospaced_zh_tw",
        ),
        PixelGlyphPackDefinition(
            id = PixelGlyphPackId.ARK_PIXEL_16PX_MONOSPACED_ZH_TR,
            assetDirectory = "glyphpacks/ark_pixel_16px_monospaced_zh_tr",
        ),
        PixelGlyphPackDefinition(
            id = PixelGlyphPackId.ARK_PIXEL_16PX_PROPORTIONAL_ZH_CN,
            assetDirectory = "glyphpacks/ark_pixel_16px_proportional_zh_cn",
        ),
        PixelGlyphPackDefinition(
            id = PixelGlyphPackId.ARK_PIXEL_16PX_PROPORTIONAL_ZH_HK,
            assetDirectory = "glyphpacks/ark_pixel_16px_proportional_zh_hk",
        ),
        PixelGlyphPackDefinition(
            id = PixelGlyphPackId.ARK_PIXEL_16PX_PROPORTIONAL_ZH_TW,
            assetDirectory = "glyphpacks/ark_pixel_16px_proportional_zh_tw",
        ),
        PixelGlyphPackDefinition(
            id = PixelGlyphPackId.ARK_PIXEL_16PX_PROPORTIONAL_ZH_TR,
            assetDirectory = "glyphpacks/ark_pixel_16px_proportional_zh_tr",
        ),
    )

    fun definition(id: PixelGlyphPackId): PixelGlyphPackDefinition {
        return definitions.first { it.id == id }
    }
}

data class PixelGlyphPackManifest(
    val packId: String,
    val displayName: String,
    val cellHeight: Int,
    val baseline: Int,
    val defaultAdvance: Int,
    val supportedRanges: List<String>,
)

data class PackedGlyphRecord(
    val codePoint: Int,
    val advanceWidth: Int,
    val width: Int,
    val packedPixels: ByteArray,
)

data class PixelGlyphPack(
    val manifest: PixelGlyphPackManifest,
    val glyphs: Map<Int, PackedGlyphRecord>,
)

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

class GlyphPackAssetLoader(
    private val context: Context,
) {

    private val cache = mutableMapOf<PixelGlyphPackId, PixelGlyphPack>()

    fun load(packId: PixelGlyphPackId): PixelGlyphPack {
        return cache.getOrPut(packId) {
            val definition = PixelGlyphPackCatalog.definition(packId)
            val manifest = context.assets.open("${definition.assetDirectory}/manifest.json")
                .bufferedReader(Charsets.UTF_8)
                .use { reader -> PixelGlyphPackParser.parseManifest(reader.readText()) }

            context.assets.open("${definition.assetDirectory}/glyphs.bin").use { input ->
                PixelGlyphPackParser.parseBinary(manifest, input)
            }
        }
    }
}
