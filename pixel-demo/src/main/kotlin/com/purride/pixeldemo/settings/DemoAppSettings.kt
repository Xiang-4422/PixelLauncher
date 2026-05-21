package com.purride.pixeldemo.settings

import com.purride.pixelcore.PixelColorMode
import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.PixelTheme

enum class DemoFontStyle {
    PROPORTIONAL,
    MONOSPACED,
}

data class DemoAppSettings(
    val colorMode: PixelColorMode = PixelColorMode.Mono,
    val monoTheme: PixelTheme = PixelTheme.GREEN_PHOSPHOR,
    val pixelShape: PixelShape = PixelShape.SQUARE,
    val dotSizePx: Int = 12,
    val pixelGapEnabled: Boolean = true,
    val fontSizePx: Int = 8,
    val fontStyle: DemoFontStyle = DemoFontStyle.PROPORTIONAL,
)
