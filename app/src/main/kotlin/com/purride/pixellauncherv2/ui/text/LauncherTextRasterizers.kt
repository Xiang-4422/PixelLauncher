package com.purride.pixellauncherv2.ui.text

import android.content.Context
import com.purride.pixelcore.BitmapGlyphSource
import com.purride.pixelcore.CompositeGlyphProvider
import com.purride.pixelcore.GlyphStyle
import com.purride.pixelcore.PixelFontEngine
import com.purride.pixelcore.PixelFontFamily
import com.purride.pixelcore.PixelFontWeight
import com.purride.pixelcore.PixelGlyphPack
import com.purride.pixelcore.PixelGlyphPackAssetLoader
import com.purride.pixelcore.PixelStyledTextRasterizer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixellauncherv2.launcher.PixelFontSize
import com.purride.pixellauncherv2.launcher.PixelFontStyle

class LauncherTextRasterizers(
    context: Context,
) {

    private val glyphPackLoader = PixelGlyphPackAssetLoader(context)
    private val cache = mutableMapOf<Pair<PixelFontSize, PixelFontStyle>, PixelTextRasterizer>()

    fun getRasterizer(
        size: PixelFontSize,
        style: PixelFontStyle,
    ): PixelTextRasterizer {
        return cache.getOrPut(size to style) {
            val styleName = when (style) {
                PixelFontStyle.MONO -> "monospaced"
                PixelFontStyle.PROP -> "proportional"
            }
            fusionRasterizer(
                latinAssetDirectory = "glyphpacks/fusion_pixel_${size.px}px_${styleName}_latin",
                zhHansAssetDirectory = "glyphpacks/fusion_pixel_${size.px}px_${styleName}_zh_hans",
            )
        }
    }

    private fun fusionRasterizer(
        latinAssetDirectory: String,
        zhHansAssetDirectory: String,
    ): PixelTextRasterizer {
        val latinPack = glyphPackLoader.load(latinAssetDirectory)
        val zhHansPack = glyphPackLoader.load(zhHansAssetDirectory)

        require(latinPack.manifest.cellHeight == zhHansPack.manifest.cellHeight) {
            "Fusion Pixel latin and zh_hans packs must share cellHeight"
        }

        return PixelStyledTextRasterizer(
            engine = PixelFontEngine(
                glyphProvider = CompositeGlyphProvider(
                    listOf(BitmapGlyphSource(listOf(latinPack, zhHansPack))),
                ),
            ),
            style = styleFor(latinPack, zhHansPack),
            lineSpacing = 1,
        )
    }

    private fun styleFor(
        latinPack: PixelGlyphPack,
        zhHansPack: PixelGlyphPack,
    ): GlyphStyle {
        return GlyphStyle(
            cellHeight = latinPack.manifest.cellHeight,
            narrowAdvanceWidth = latinPack.manifest.defaultAdvance,
            wideAdvanceWidth = zhHansPack.manifest.defaultAdvance,
            oversampleFactor = 1,
            narrowMinimumSampleRatio = 1f,
            wideMinimumSampleRatio = 1f,
            narrowTextSizeRatio = 1f,
            wideTextSizeRatio = 1f,
            narrowFontWeight = PixelFontWeight.NORMAL,
            wideFontWeight = PixelFontWeight.NORMAL,
            narrowFontFamily = PixelFontFamily.MONOSPACE,
            wideFontFamily = PixelFontFamily.DEFAULT,
            baseLetterSpacing = 0,
        )
    }
}
