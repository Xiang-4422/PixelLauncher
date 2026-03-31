package com.purride.pixellauncherv2.render

/**
 * 兼容层代理。
 *
 * `ScreenProfileFactory` 的真实实现已经迁到 `:pixel-core`，
 * 当前保留旧包名入口，避免第一轮拆分就改动全仓调用点。
 */
object ScreenProfileFactory {

    const val defaultDotSizePx: Int = com.purride.pixelcore.ScreenProfileFactory.defaultDotSizePx
    val supportedDotSizePxOptions: List<Int> = com.purride.pixelcore.ScreenProfileFactory.supportedDotSizePxOptions

    fun create(
        widthPx: Int,
        heightPx: Int,
        dotSizePx: Int = defaultDotSizePx,
        pixelShape: PixelShape = PixelShape.SQUARE,
    ): ScreenProfile {
        return com.purride.pixelcore.ScreenProfileFactory.create(
            widthPx = widthPx,
            heightPx = heightPx,
            dotSizePx = dotSizePx,
            pixelShape = pixelShape,
        )
    }

    fun resolutionOptions(currentProfile: ScreenProfile?): List<Int> {
        return com.purride.pixelcore.ScreenProfileFactory.resolutionOptions(currentProfile)
    }
}
