package com.purride.pixellauncherv2.render

object ScreenProfileFactory {

    const val defaultDotSizePx: Int = 12
    val supportedDotSizePxOptions: List<Int> = listOf(7, 8, 10, 12, 14, 16)

    fun create(
        widthPx: Int,
        heightPx: Int,
        dotSizePx: Int = defaultDotSizePx,
        pixelShape: PixelShape = PixelShape.SQUARE,
        statusBarHeightPx: Int = 0,
    ): ScreenProfile {
        val safeDotSizePx = dotSizePx.coerceAtLeast(1)
        val logicalWidth = (widthPx.coerceAtLeast(1) / safeDotSizePx).coerceAtLeast(1)
        val logicalHeight = (heightPx.coerceAtLeast(1) / safeDotSizePx).coerceAtLeast(1)
        val statusBarHeight = ceilToLogicalPixels(statusBarHeightPx, safeDotSizePx)
        return ScreenProfile(
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
            dotSizePx = safeDotSizePx,
            pixelShape = pixelShape,
            statusBarHeight = statusBarHeight,
        )
    }

    fun resolutionOptions(currentProfile: ScreenProfile?): List<Int> {
        return supportedDotSizePxOptions
    }

    private fun ceilToLogicalPixels(px: Int, dotSizePx: Int): Int {
        val safePx = px.coerceAtLeast(0)
        return if (safePx == 0) {
            0
        } else {
            ((safePx + dotSizePx - 1) / dotSizePx).coerceAtLeast(1)
        }
    }
}
