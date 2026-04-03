package com.purride.pixelcore

import android.graphics.Color

/**
 * 逻辑像素调色板。
 *
 * 内核层只关心“像素值如何映射到真实颜色”，不关心产品页面该选什么主题。
 */
enum class PixelTheme {
    GREEN_PHOSPHOR,
    AMBER_CRT,
    ICE_LCD,
    MONO_LCD,
    NIGHT_MONO,
}

data class PixelPalette(
    val backgroundColor: Int,
    val pixelOnColor: Int,
    val pixelOffColor: Int,
    val accentColor: Int,
) {
    companion object {
        fun terminalGreen(): PixelPalette = fromTheme(PixelTheme.GREEN_PHOSPHOR)

        fun fromTheme(theme: PixelTheme, isLowBattery: Boolean = false): PixelPalette {
            return when (theme) {
                PixelTheme.GREEN_PHOSPHOR -> PixelPalette(
                    backgroundColor = Color.rgb(0, 0, 0),
                    pixelOnColor = Color.rgb(151, 255, 167),
                    pixelOffColor = Color.rgb(8, 37, 13),
                    accentColor = if (isLowBattery) Color.rgb(118, 178, 120) else Color.rgb(199, 255, 208),
                )

                PixelTheme.AMBER_CRT -> PixelPalette(
                    backgroundColor = Color.rgb(8, 3, 0),
                    pixelOnColor = Color.rgb(255, 191, 92),
                    pixelOffColor = Color.rgb(46, 25, 5),
                    accentColor = if (isLowBattery) Color.rgb(185, 128, 58) else Color.rgb(255, 225, 164),
                )

                PixelTheme.ICE_LCD -> PixelPalette(
                    backgroundColor = Color.rgb(3, 8, 14),
                    pixelOnColor = Color.rgb(184, 237, 255),
                    pixelOffColor = Color.rgb(12, 34, 47),
                    accentColor = if (isLowBattery) Color.rgb(122, 173, 188) else Color.rgb(235, 248, 255),
                )

                PixelTheme.MONO_LCD -> PixelPalette(
                    backgroundColor = Color.rgb(0, 0, 0),
                    pixelOnColor = Color.rgb(255, 255, 255),
                    pixelOffColor = Color.rgb(0, 0, 0),
                    accentColor = Color.rgb(255, 255, 255),
                )

                PixelTheme.NIGHT_MONO -> PixelPalette(
                    backgroundColor = Color.rgb(0, 0, 0),
                    pixelOnColor = Color.rgb(176, 176, 176),
                    pixelOffColor = Color.rgb(20, 20, 20),
                    accentColor = if (isLowBattery) Color.rgb(132, 132, 132) else Color.rgb(156, 156, 156),
                )
            }
        }
    }
}
