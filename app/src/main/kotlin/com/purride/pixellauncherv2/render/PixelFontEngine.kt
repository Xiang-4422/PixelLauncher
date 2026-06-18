package com.purride.pixellauncherv2.render

import com.purride.pixellauncherv2.launcher.PixelFontStyle

data class GlyphStyle(
    val cellHeight: Int,
    val narrowAdvanceWidth: Int,
    val wideAdvanceWidth: Int,
    val oversampleFactor: Int,
    val narrowMinimumSampleRatio: Float,
    val wideMinimumSampleRatio: Float,
    val narrowTextSizeRatio: Float,
    val wideTextSizeRatio: Float,
    val narrowFontWeight: PixelFontWeight,
    val wideFontWeight: PixelFontWeight,
    val narrowFontFamily: PixelFontFamily,
    val wideFontFamily: PixelFontFamily,
) {
    companion object {
        @Volatile
        private var configuredFontStyle: PixelFontStyle = PixelFontStyle.PROP

        /** 所有布局尺寸计算使用固定 10px。 */
        private const val FIXED_FONT_PX = 10

        val APP_LABEL_16: GlyphStyle
            get() = styleFor(FIXED_FONT_PX, configuredFontStyle)

        val UI_SMALL_10: GlyphStyle
            get() = styleFor(FIXED_FONT_PX, configuredFontStyle)

        fun configure(fontStyle: PixelFontStyle) {
            configuredFontStyle = fontStyle
        }

        private fun styleFor(cellHeight: Int, style: PixelFontStyle): GlyphStyle {
            return GlyphStyle(
                cellHeight = cellHeight,
                narrowAdvanceWidth = narrowAdvanceWidth(cellHeight, style),
                wideAdvanceWidth = cellHeight,
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

        private fun narrowAdvanceWidth(cellHeight: Int, style: PixelFontStyle): Int {
            return when (style) {
                PixelFontStyle.MONO -> cellHeight
                PixelFontStyle.PROP -> 6
            }
        }
    }
}

enum class PixelFontWeight {
    NORMAL,
    BOLD,
}

enum class PixelFontFamily {
    DEFAULT,
    MONOSPACE,
}
