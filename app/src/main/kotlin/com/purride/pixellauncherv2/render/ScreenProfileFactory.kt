package com.purride.pixellauncherv2.render

object ScreenProfileFactory {

    const val defaultDotSizePx: Int = 12
    val supportedDotSizePxOptions: List<Int> = listOf(7, 8, 10, 12)
    private const val minRecommendedCellSizePx = 9f

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
        if (currentProfile == null) {
            return supportedDotSizePxOptions
        }
        val widthPx = currentProfile.logicalWidth * currentProfile.dotSizePx
        val heightPx = currentProfile.logicalHeight * currentProfile.dotSizePx
        val filtered = supportedDotSizePxOptions.filter { candidateDotSize ->
            val candidateProfile = create(
                widthPx = widthPx,
                heightPx = heightPx,
                dotSizePx = candidateDotSize,
                pixelShape = currentProfile.pixelShape,
            )
            val geometry = PixelGridGeometryResolver.resolve(
                viewWidth = widthPx,
                viewHeight = heightPx,
                profile = candidateProfile,
            )
            geometry != null && geometry.cellSize >= minRecommendedCellSizePx
        }
        return if (filtered.isNotEmpty()) {
            filtered
        } else {
            supportedDotSizePxOptions
        }
    }
}
