package com.purride.pixellauncherv2.render

object ScreenProfileFactory {

    const val defaultDotSizePx: Int = 12
    val supportedDotSizePxOptions: List<Int> = listOf(7, 8, 10, 12)

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
