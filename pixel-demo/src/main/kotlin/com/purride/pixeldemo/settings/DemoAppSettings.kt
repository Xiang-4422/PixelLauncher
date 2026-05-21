package com.purride.pixeldemo.settings

import com.purride.pixelcore.PixelShape

enum class DemoFontStyle {
    PROPORTIONAL,
    MONOSPACED,
}

data class DemoAppSettings(
    val pixelShape: PixelShape = PixelShape.SQUARE,
    val dotSizePx: Int = 12,
    val pixelGapRatio: Float = 0.6f,
    val fontSizePx: Int = 8,
    val fontStyle: DemoFontStyle = DemoFontStyle.PROPORTIONAL,
)
