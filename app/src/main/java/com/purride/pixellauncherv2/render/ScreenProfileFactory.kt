package com.purride.pixellauncherv2.render

object ScreenProfileFactory {

    const val defaultDotSizePx: Int = 15
    val supportedDotSizePxOptions: List<Int> = listOf(10, 12, 15, 18, 21)

    fun create(
        widthPx: Int,
        heightPx: Int,
        dotSizePx: Int = defaultDotSizePx,
        pixelShape: PixelShape = PixelShape.SQUARE,
    ): ScreenProfile {
        val safeDotSizePx = dotSizePx.coerceAtLeast(1)
        return ScreenProfile(
            logicalWidth = (widthPx / safeDotSizePx).coerceAtLeast(1),
            logicalHeight = (heightPx / safeDotSizePx).coerceAtLeast(1),
            dotSizePx = safeDotSizePx,
            pixelShape = pixelShape,
        )
    }
}
