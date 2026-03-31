package com.purride.pixellauncherv2.render

/**
 * 兼容层别名。
 */
typealias HorizontalPageState = com.purride.pixelcore.HorizontalPageState

/**
 * 兼容层别名。
 */
typealias HorizontalPageSnapshot = com.purride.pixelcore.HorizontalPageSnapshot

/**
 * 兼容层别名。
 */
typealias HorizontalPageController = com.purride.pixelcore.HorizontalPageController

/**
 * 兼容层代理。
 *
 * `HorizontalPageRenderer` 的真实实现已经迁到 `:pixel-core`，
 * 当前保留旧入口，避免第一轮拆分时改动现有调用点。
 */
object HorizontalPageRenderer {
    fun compose(
        currentPage: PixelBuffer,
        adjacentPage: PixelBuffer?,
        dragOffsetPx: Float,
        contentStartY: Int = 0,
    ): PixelBuffer {
        return com.purride.pixelcore.HorizontalPageRenderer.compose(
            currentPage = currentPage,
            adjacentPage = adjacentPage,
            dragOffsetPx = dragOffsetPx,
            contentStartY = contentStartY,
        )
    }
}
