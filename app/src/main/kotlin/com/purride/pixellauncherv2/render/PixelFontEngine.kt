package com.purride.pixellauncherv2.render

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
        private var configuredFontSize: PixelFontSize = PixelFontCatalog.defaultFontSize

        @Volatile
        private var configuredFontStyle: PixelFontStyle = PixelFontCatalog.defaultFontStyle

        val APP_LABEL_16: GlyphStyle
            get() = styleForAppLabel(configuredFontSize, configuredFontStyle)

        val UI_SMALL_10: GlyphStyle
            get() = styleForUiSmall(configuredFontSize, configuredFontStyle)

        fun configure(fontSize: PixelFontSize, fontStyle: PixelFontStyle) {
            configuredFontSize = fontSize
            configuredFontStyle = fontStyle
        }

        private fun styleForAppLabel(size: PixelFontSize, style: PixelFontStyle): GlyphStyle {
            val cellHeight = size.px
            return GlyphStyle(
                cellHeight = cellHeight,
                narrowAdvanceWidth = narrowAdvanceWidth(size, style),
                wideAdvanceWidth = wideAdvanceWidth(size, style),
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

        private fun styleForUiSmall(size: PixelFontSize, style: PixelFontStyle): GlyphStyle {
            val cellHeight = size.px
            return GlyphStyle(
                cellHeight = cellHeight,
                narrowAdvanceWidth = narrowAdvanceWidth(size, style),
                wideAdvanceWidth = wideAdvanceWidth(size, style),
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

        private fun narrowAdvanceWidth(size: PixelFontSize, style: PixelFontStyle): Int {
            return when (style) {
                PixelFontStyle.MONO -> size.px
                PixelFontStyle.PROP -> when (size) {
                    PixelFontSize.PX_8 -> 4
                    PixelFontSize.PX_10 -> 6
                    PixelFontSize.PX_12 -> 8
                }
            }
        }

        private fun wideAdvanceWidth(size: PixelFontSize, style: PixelFontStyle): Int {
            return when (style) {
                PixelFontStyle.MONO -> size.px
                PixelFontStyle.PROP -> size.px
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
