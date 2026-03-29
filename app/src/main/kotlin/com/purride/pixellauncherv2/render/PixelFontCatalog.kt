package com.purride.pixellauncherv2.render

import android.content.Context

enum class PixelFontSize(val px: Int) {
    PX_8(8),
    PX_10(10),
    PX_12(12),
}

enum class PixelFontStyle {
    MONO,
    PROP,
}

data class FusionFontSelection(
    val size: PixelFontSize,
    val style: PixelFontStyle,
)

data class FusionFontPackDefinition(
    val selection: FusionFontSelection,
    val latinPackId: PixelGlyphPackId,
    val zhHansPackId: PixelGlyphPackId,
)

data class ResolvedPixelFont(
    val engine: PixelFontEngine,
    val pixelShape: PixelShape,
)

object PixelFontCatalog {

    val defaultFontSize: PixelFontSize = PixelFontSize.PX_10
    val defaultFontStyle: PixelFontStyle = PixelFontStyle.PROP

    private val definitions = listOf(
        buildDefinition(PixelFontSize.PX_8, PixelFontStyle.MONO),
        buildDefinition(PixelFontSize.PX_8, PixelFontStyle.PROP),
        buildDefinition(PixelFontSize.PX_10, PixelFontStyle.MONO),
        buildDefinition(PixelFontSize.PX_10, PixelFontStyle.PROP),
        buildDefinition(PixelFontSize.PX_12, PixelFontStyle.MONO),
        buildDefinition(PixelFontSize.PX_12, PixelFontStyle.PROP),
    )

    fun fontSizeOptions(): List<PixelFontSize> = PixelFontSize.entries

    fun fontStyleOptions(): List<PixelFontStyle> = PixelFontStyle.entries

    fun definition(size: PixelFontSize, style: PixelFontStyle): FusionFontPackDefinition {
        return definitions.first { it.selection.size == size && it.selection.style == style }
    }

    fun sizeLabel(size: PixelFontSize): String = "${size.px}PX"

    fun styleLabel(style: PixelFontStyle): String {
        return when (style) {
            PixelFontStyle.MONO -> "MONO"
            PixelFontStyle.PROP -> "PROP"
        }
    }

    fun combinedLabel(size: PixelFontSize, style: PixelFontStyle): String {
        return "FUSION ${size.px} ${styleLabel(style)}"
    }

    private fun buildDefinition(size: PixelFontSize, style: PixelFontStyle): FusionFontPackDefinition {
        val latinPackId = when (size) {
            PixelFontSize.PX_8 -> when (style) {
                PixelFontStyle.MONO -> PixelGlyphPackId.FUSION_PIXEL_8PX_MONOSPACED_LATIN
                PixelFontStyle.PROP -> PixelGlyphPackId.FUSION_PIXEL_8PX_PROPORTIONAL_LATIN
            }

            PixelFontSize.PX_10 -> when (style) {
                PixelFontStyle.MONO -> PixelGlyphPackId.FUSION_PIXEL_10PX_MONOSPACED_LATIN
                PixelFontStyle.PROP -> PixelGlyphPackId.FUSION_PIXEL_10PX_PROPORTIONAL_LATIN
            }

            PixelFontSize.PX_12 -> when (style) {
                PixelFontStyle.MONO -> PixelGlyphPackId.FUSION_PIXEL_12PX_MONOSPACED_LATIN
                PixelFontStyle.PROP -> PixelGlyphPackId.FUSION_PIXEL_12PX_PROPORTIONAL_LATIN
            }
        }
        val zhHansPackId = when (size) {
            PixelFontSize.PX_8 -> when (style) {
                PixelFontStyle.MONO -> PixelGlyphPackId.FUSION_PIXEL_8PX_MONOSPACED_ZH_HANS
                PixelFontStyle.PROP -> PixelGlyphPackId.FUSION_PIXEL_8PX_PROPORTIONAL_ZH_HANS
            }

            PixelFontSize.PX_10 -> when (style) {
                PixelFontStyle.MONO -> PixelGlyphPackId.FUSION_PIXEL_10PX_MONOSPACED_ZH_HANS
                PixelFontStyle.PROP -> PixelGlyphPackId.FUSION_PIXEL_10PX_PROPORTIONAL_ZH_HANS
            }

            PixelFontSize.PX_12 -> when (style) {
                PixelFontStyle.MONO -> PixelGlyphPackId.FUSION_PIXEL_12PX_MONOSPACED_ZH_HANS
                PixelFontStyle.PROP -> PixelGlyphPackId.FUSION_PIXEL_12PX_PROPORTIONAL_ZH_HANS
            }
        }
        return FusionFontPackDefinition(
            selection = FusionFontSelection(size = size, style = style),
            latinPackId = latinPackId,
            zhHansPackId = zhHansPackId,
        )
    }
}

class PixelFontResolver(
    private val context: Context,
) {

    private val glyphPackLoader = GlyphPackAssetLoader(context)

    fun createFont(
        fontSize: PixelFontSize,
        fontStyle: PixelFontStyle,
    ): ResolvedPixelFont {
        val definition = PixelFontCatalog.definition(fontSize, fontStyle)

        GlyphStyle.configure(fontSize, fontStyle)

        return ResolvedPixelFont(
            engine = PixelFontEngine(
                CompositeGlyphProvider(
                    listOf(
                        BitmapGlyphSource(
                            packs = listOf(
                                glyphPackLoader.load(definition.latinPackId),
                                glyphPackLoader.load(definition.zhHansPackId),
                            ),
                        ),
                    ),
                ),
            ),
            pixelShape = PixelShape.SQUARE,
        )
    }
}
