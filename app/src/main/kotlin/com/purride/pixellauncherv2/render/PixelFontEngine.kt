package com.purride.pixellauncherv2.render

data class GlyphStyle(
    val cellHeight: Int,
    val baseline: Int,
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
        /** 所有布局尺寸计算使用固定 10px。 */
        private const val FIXED_FONT_PX = 10
        private const val NARROW_ADVANCE_PX = 6

        val APP_LABEL_16: GlyphStyle
            get() = styleFor(FIXED_FONT_PX)

        val UI_SMALL_10: GlyphStyle
            get() = styleFor(FIXED_FONT_PX)

        private fun styleFor(cellHeight: Int): GlyphStyle {
            return GlyphStyle(
                cellHeight = cellHeight,
                baseline = (cellHeight - 1).coerceAtLeast(0),
                narrowAdvanceWidth = NARROW_ADVANCE_PX,
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
