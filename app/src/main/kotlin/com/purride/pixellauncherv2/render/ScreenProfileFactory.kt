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
        val logicalWidth = (widthPx.coerceAtLeast(1) / safeDotSizePx).coerceAtLeast(1)
        val logicalHeight = (heightPx.coerceAtLeast(1) / safeDotSizePx).coerceAtLeast(1)
        return ScreenProfile(
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
            dotSizePx = safeDotSizePx,
            pixelShape = pixelShape,
        )
    }

    fun resolutionOptions(currentProfile: ScreenProfile?): List<Int> {
        return if (currentProfile == null) {
            supportedDotSizePxOptions
        } else {
            supportedDotSizePxOptions
        }
    }
}
