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
        val squareSidePx = minOf(widthPx, heightPx).coerceAtLeast(1)
        val logicalSide = (squareSidePx / safeDotSizePx).coerceAtLeast(1)
        return ScreenProfile(
            logicalWidth = logicalSide,
            logicalHeight = logicalSide,
            dotSizePx = safeDotSizePx,
            pixelShape = pixelShape,
        )
    }

    fun squareResolutionOptions(currentProfile: ScreenProfile?): List<Int> {
        if (currentProfile == null) {
            return supportedDotSizePxOptions
        }
        val widthPx = currentProfile.logicalWidth * currentProfile.dotSizePx
        val heightPx = currentProfile.logicalHeight * currentProfile.dotSizePx
        val filtered = supportedDotSizePxOptions.filter { candidateDotSize ->
            val candidate = create(
                widthPx = widthPx,
                heightPx = heightPx,
                dotSizePx = candidateDotSize,
                pixelShape = currentProfile.pixelShape,
            )
            candidate.logicalWidth == candidate.logicalHeight
        }
        return when {
            filtered.isNotEmpty() -> filtered
            currentProfile.logicalWidth == currentProfile.logicalHeight -> listOf(currentProfile.dotSizePx)
            else -> supportedDotSizePxOptions
        }
    }
}
