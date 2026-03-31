package com.purride.pixeldemo.app

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

/**
 * Demo 文本栅格器仓库。
 *
 * 这里直接复用 Launcher 已经产出的 Fusion Pixel 字形包资产，但不依赖 `:app` 代码。
 * 这样 `pixel-demo` 可以独立验证中文与中英混排，而不会再维护第二套字体实现。
 */
class DemoTextRasterizers(
    context: Context,
) {

    private val glyphPackLoader = PixelGlyphPackAssetLoader(context)

    /**
     * Demo 默认文本样式。
     *
     * 选 8px proportional 是为了尽量贴近当前 demo 的页面密度，同时把中文能力一起接进来。
     */
    val default: PixelTextRasterizer by lazy {
        fusionRasterizer(
            latinAssetDirectory = "glyphpacks/fusion_pixel_8px_proportional_latin",
            zhHansAssetDirectory = "glyphpacks/fusion_pixel_8px_proportional_zh_hans",
            lineSpacing = 1,
        )
    }

    /**
     * 文本展示页用的强调样式。
     *
     * 10px proportional 能更明显地展示中文字形和中英混排效果。
     */
    val emphasis: PixelTextRasterizer by lazy {
        fusionRasterizer(
            latinAssetDirectory = "glyphpacks/fusion_pixel_10px_proportional_latin",
            zhHansAssetDirectory = "glyphpacks/fusion_pixel_10px_proportional_zh_hans",
            lineSpacing = 1,
        )
    }

    private fun fusionRasterizer(
        latinAssetDirectory: String,
        zhHansAssetDirectory: String,
        lineSpacing: Int,
    ): PixelTextRasterizer {
        val latinPack = glyphPackLoader.load(latinAssetDirectory)
        val zhHansPack = glyphPackLoader.load(zhHansAssetDirectory)

        require(latinPack.manifest.cellHeight == zhHansPack.manifest.cellHeight) {
            "Fusion Pixel 拉丁包和中文包的 cellHeight 不一致，无法组成统一文本样式"
        }

        return PixelStyledTextRasterizer(
            engine = PixelFontEngine(
                glyphProvider = CompositeGlyphProvider(
                    listOf(
                        /**
                         * 拉丁包放前面，确保 ASCII 优先走窄字宽；
                         * 中文包放后面，专门兜住 CJK 和全角字符。
                         */
                        BitmapGlyphSource(listOf(latinPack, zhHansPack)),
                    ),
                ),
            ),
            style = styleFor(latinPack, zhHansPack),
            lineSpacing = lineSpacing,
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
