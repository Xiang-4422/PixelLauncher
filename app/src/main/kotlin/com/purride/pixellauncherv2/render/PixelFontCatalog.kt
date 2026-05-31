package com.purride.pixellauncherv2.render

enum class PixelFontSize(val px: Int) {
    PX_8(8),
    PX_10(10),
    PX_12(12),
}

enum class PixelFontStyle {
    MONO,
    PROP,
}

object PixelFontCatalog {

    val defaultFontSize: PixelFontSize = PixelFontSize.PX_10
    val defaultFontStyle: PixelFontStyle = PixelFontStyle.PROP

    fun fontSizeOptions(): List<PixelFontSize> = PixelFontSize.entries

    fun fontStyleOptions(): List<PixelFontStyle> = PixelFontStyle.entries

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
}
