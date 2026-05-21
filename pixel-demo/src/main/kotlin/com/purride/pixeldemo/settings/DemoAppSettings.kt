package com.purride.pixeldemo.settings

import com.purride.pixelcore.PixelColorMode
import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.PixelTheme

enum class DemoFontStyle {
    PROPORTIONAL,
    MONOSPACED,
}

/** Color 模式下使用的颜色主题（对应 PixelThemeTokens 中的预设）。 */
enum class DemoColorTheme {
    DARK,
    LIGHT,
    OCEAN,
    AMBER,
}

data class DemoAppSettings(
    val colorMode: PixelColorMode = PixelColorMode.Mono,
    val monoTheme: PixelTheme = PixelTheme.GREEN_PHOSPHOR,
    val colorTheme: DemoColorTheme = DemoColorTheme.DARK,
    val pixelShape: PixelShape = PixelShape.SQUARE,
    val dotSizePx: Int = 12,
    val pixelGapEnabled: Boolean = true,
    val fontSizePx: Int = 8,
    val fontStyle: DemoFontStyle = DemoFontStyle.PROPORTIONAL,
)
