package com.purride.pixellauncherv2.render

import android.content.Context

enum class PixelFontId {
    SYSTEM_RASTERIZED,
    WEN_QUAN_YI_BITMAP_SONG_16PX,
    DOTTED_SONGTI_SQUARE,
    DOTTED_SONGTI_CIRCLE,
    DOTTED_SONGTI_DIAMOND,
    ARK_PIXEL_16PX_MONOSPACED_ZH_CN,
    ARK_PIXEL_16PX_MONOSPACED_ZH_HK,
    ARK_PIXEL_16PX_MONOSPACED_ZH_TW,
    ARK_PIXEL_16PX_MONOSPACED_ZH_TR,
    ARK_PIXEL_16PX_PROPORTIONAL_ZH_CN,
    ARK_PIXEL_16PX_PROPORTIONAL_ZH_HK,
    ARK_PIXEL_16PX_PROPORTIONAL_ZH_TW,
    ARK_PIXEL_16PX_PROPORTIONAL_ZH_TR,
    GNU_UNIFONT_17_0_03,
}

data class PixelFontDefinition(
    val id: PixelFontId,
    val displayName: String,
    val settingsLabel: String,
    val familyName: String,
    val sourceName: String,
    val pixelShape: PixelShape = PixelShape.SQUARE,
    val primaryGlyphPackId: PixelGlyphPackId? = null,
    val fallbackGlyphPackIds: List<PixelGlyphPackId> = emptyList(),
    val fallbackToSystemRasterizer: Boolean = true,
)

data class ResolvedPixelFont(
    val engine: PixelFontEngine,
    val pixelShape: PixelShape,
)

object PixelFontCatalog {

    val defaultFontId: PixelFontId = PixelFontId.WEN_QUAN_YI_BITMAP_SONG_16PX

    private val definitions = listOf(
        PixelFontDefinition(
            id = PixelFontId.WEN_QUAN_YI_BITMAP_SONG_16PX,
            displayName = "WenQuanYi Bitmap Song 16px",
            settingsLabel = "WQY SONG",
            familyName = "WenQuanYi Bitmap Song",
            sourceName = "WenQuanYi",
            primaryGlyphPackId = PixelGlyphPackId.WEN_QUAN_YI_BITMAP_SONG_16PX,
            fallbackGlyphPackIds = listOf(PixelGlyphPackId.GNU_UNIFONT_17_0_03),
        ),
        PixelFontDefinition(
            id = PixelFontId.DOTTED_SONGTI_SQUARE,
            displayName = "Dotted Square",
            settingsLabel = "DOT SQR",
            familyName = "Dotted Theme",
            sourceName = "WenQuanYi + Dotted Chinese Fonts",
            pixelShape = PixelShape.SQUARE,
            primaryGlyphPackId = PixelGlyphPackId.WEN_QUAN_YI_BITMAP_SONG_16PX,
            fallbackGlyphPackIds = listOf(PixelGlyphPackId.GNU_UNIFONT_17_0_03),
        ),
        PixelFontDefinition(
            id = PixelFontId.DOTTED_SONGTI_CIRCLE,
            displayName = "Dotted Circle",
            settingsLabel = "DOT CIR",
            familyName = "Dotted Theme",
            sourceName = "WenQuanYi + Dotted Chinese Fonts",
            pixelShape = PixelShape.CIRCLE,
            primaryGlyphPackId = PixelGlyphPackId.WEN_QUAN_YI_BITMAP_SONG_16PX,
            fallbackGlyphPackIds = listOf(PixelGlyphPackId.GNU_UNIFONT_17_0_03),
        ),
        PixelFontDefinition(
            id = PixelFontId.DOTTED_SONGTI_DIAMOND,
            displayName = "Dotted Diamond",
            settingsLabel = "DOT DIA",
            familyName = "Dotted Theme",
            sourceName = "WenQuanYi + Dotted Chinese Fonts",
            pixelShape = PixelShape.DIAMOND,
            primaryGlyphPackId = PixelGlyphPackId.WEN_QUAN_YI_BITMAP_SONG_16PX,
            fallbackGlyphPackIds = listOf(PixelGlyphPackId.GNU_UNIFONT_17_0_03),
        ),
        PixelFontDefinition(
            id = PixelFontId.ARK_PIXEL_16PX_MONOSPACED_ZH_CN,
            displayName = "Ark Pixel 16px Monospaced (zh_CN)",
            settingsLabel = "ARK M CN",
            familyName = "Ark Pixel Font",
            sourceName = "TakWolf",
            primaryGlyphPackId = PixelGlyphPackId.ARK_PIXEL_16PX_MONOSPACED_ZH_CN,
            fallbackGlyphPackIds = listOf(PixelGlyphPackId.GNU_UNIFONT_17_0_03),
        ),
        PixelFontDefinition(
            id = PixelFontId.ARK_PIXEL_16PX_MONOSPACED_ZH_HK,
            displayName = "Ark Pixel 16px Monospaced (zh_HK)",
            settingsLabel = "ARK M HK",
            familyName = "Ark Pixel Font",
            sourceName = "TakWolf",
            primaryGlyphPackId = PixelGlyphPackId.ARK_PIXEL_16PX_MONOSPACED_ZH_HK,
            fallbackGlyphPackIds = listOf(PixelGlyphPackId.GNU_UNIFONT_17_0_03),
        ),
        PixelFontDefinition(
            id = PixelFontId.ARK_PIXEL_16PX_MONOSPACED_ZH_TW,
            displayName = "Ark Pixel 16px Monospaced (zh_TW)",
            settingsLabel = "ARK M TW",
            familyName = "Ark Pixel Font",
            sourceName = "TakWolf",
            primaryGlyphPackId = PixelGlyphPackId.ARK_PIXEL_16PX_MONOSPACED_ZH_TW,
            fallbackGlyphPackIds = listOf(PixelGlyphPackId.GNU_UNIFONT_17_0_03),
        ),
        PixelFontDefinition(
            id = PixelFontId.ARK_PIXEL_16PX_MONOSPACED_ZH_TR,
            displayName = "Ark Pixel 16px Monospaced (zh_TR)",
            settingsLabel = "ARK M TR",
            familyName = "Ark Pixel Font",
            sourceName = "TakWolf",
            primaryGlyphPackId = PixelGlyphPackId.ARK_PIXEL_16PX_MONOSPACED_ZH_TR,
            fallbackGlyphPackIds = listOf(PixelGlyphPackId.GNU_UNIFONT_17_0_03),
        ),
        PixelFontDefinition(
            id = PixelFontId.ARK_PIXEL_16PX_PROPORTIONAL_ZH_CN,
            displayName = "Ark Pixel 16px Proportional (zh_CN)",
            settingsLabel = "ARK P CN",
            familyName = "Ark Pixel Font",
            sourceName = "TakWolf",
            primaryGlyphPackId = PixelGlyphPackId.ARK_PIXEL_16PX_PROPORTIONAL_ZH_CN,
            fallbackGlyphPackIds = listOf(PixelGlyphPackId.GNU_UNIFONT_17_0_03),
        ),
        PixelFontDefinition(
            id = PixelFontId.ARK_PIXEL_16PX_PROPORTIONAL_ZH_HK,
            displayName = "Ark Pixel 16px Proportional (zh_HK)",
            settingsLabel = "ARK P HK",
            familyName = "Ark Pixel Font",
            sourceName = "TakWolf",
            primaryGlyphPackId = PixelGlyphPackId.ARK_PIXEL_16PX_PROPORTIONAL_ZH_HK,
            fallbackGlyphPackIds = listOf(PixelGlyphPackId.GNU_UNIFONT_17_0_03),
        ),
        PixelFontDefinition(
            id = PixelFontId.ARK_PIXEL_16PX_PROPORTIONAL_ZH_TW,
            displayName = "Ark Pixel 16px Proportional (zh_TW)",
            settingsLabel = "ARK P TW",
            familyName = "Ark Pixel Font",
            sourceName = "TakWolf",
            primaryGlyphPackId = PixelGlyphPackId.ARK_PIXEL_16PX_PROPORTIONAL_ZH_TW,
            fallbackGlyphPackIds = listOf(PixelGlyphPackId.GNU_UNIFONT_17_0_03),
        ),
        PixelFontDefinition(
            id = PixelFontId.ARK_PIXEL_16PX_PROPORTIONAL_ZH_TR,
            displayName = "Ark Pixel 16px Proportional (zh_TR)",
            settingsLabel = "ARK P TR",
            familyName = "Ark Pixel Font",
            sourceName = "TakWolf",
            primaryGlyphPackId = PixelGlyphPackId.ARK_PIXEL_16PX_PROPORTIONAL_ZH_TR,
            fallbackGlyphPackIds = listOf(PixelGlyphPackId.GNU_UNIFONT_17_0_03),
        ),
        PixelFontDefinition(
            id = PixelFontId.GNU_UNIFONT_17_0_03,
            displayName = "GNU Unifont 17.0.03",
            settingsLabel = "UNIFONT",
            familyName = "GNU Unifont",
            sourceName = "Unifoundry",
            primaryGlyphPackId = PixelGlyphPackId.GNU_UNIFONT_17_0_03,
        ),
        PixelFontDefinition(
            id = PixelFontId.SYSTEM_RASTERIZED,
            displayName = "System Rasterized",
            settingsLabel = "SYSTEM",
            familyName = "System",
            sourceName = "Android",
            primaryGlyphPackId = null,
            fallbackGlyphPackIds = emptyList(),
            fallbackToSystemRasterizer = true,
        ),
    )

    fun all(): List<PixelFontDefinition> = definitions

    fun definition(id: PixelFontId): PixelFontDefinition {
        return definitions.firstOrNull { it.id == id }
            ?: definitions.first { it.id == defaultFontId }
    }

    fun settingsFontOptions(): List<PixelFontId> {
        return listOf(
            PixelFontId.WEN_QUAN_YI_BITMAP_SONG_16PX,
            PixelFontId.ARK_PIXEL_16PX_PROPORTIONAL_ZH_CN,
            PixelFontId.GNU_UNIFONT_17_0_03,
            PixelFontId.SYSTEM_RASTERIZED,
        )
    }

    fun settingsLabel(id: PixelFontId): String = definition(id).settingsLabel
}

class PixelFontResolver(
    private val context: Context,
) {

    private val glyphPackLoader = GlyphPackAssetLoader(context)

    fun createFont(fontId: PixelFontId): ResolvedPixelFont {
        val definition = PixelFontCatalog.definition(fontId)
        val sources = mutableListOf<GlyphSource>()
        val packIds = buildList {
            add(PixelGlyphPackId.ARK_PIXEL_10PX_MONOSPACED_LATIN)
            definition.primaryGlyphPackId?.let(::add)
            addAll(definition.fallbackGlyphPackIds)
        }
        if (packIds.isNotEmpty()) {
            sources += BitmapGlyphSource(
                packs = packIds.map { packId -> glyphPackLoader.load(packId) },
            )
        }
        if (definition.fallbackToSystemRasterizer || sources.isEmpty()) {
            sources += TextRasterizer()
        }

        return ResolvedPixelFont(
            engine = PixelFontEngine(CompositeGlyphProvider(sources)),
            pixelShape = definition.pixelShape,
        )
    }
}
