package com.purride.pixellauncherv2.render

data class ScreenProfile(
    val logicalWidth: Int,
    val logicalHeight: Int,
    val dotSizePx: Int,
    val pixelShape: PixelShape = PixelShape.SQUARE,
    val scaleMode: ScaleMode = ScaleMode.FIT_CENTER,
)

enum class PixelShape {
    SQUARE,
    CIRCLE,
    DIAMOND,
}

enum class ScaleMode {
    FIT_CENTER,
}
