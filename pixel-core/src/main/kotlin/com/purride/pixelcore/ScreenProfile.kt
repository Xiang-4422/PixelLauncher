package com.purride.pixelcore

/**
 * 逻辑屏幕配置。
 *
 * 这层只描述“逻辑像素世界”如何映射到真实 Surface，
 * 不负责任何页面排版语义。
 */
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
