package com.purride.pixellauncherv2.render

import com.purride.pixelcore.PixelShape

data class ScreenProfile(
    val logicalWidth: Int,
    val logicalHeight: Int,
    val dotSizePx: Int,
    // 直接使用 Engine 的 PixelShape，不再维护 Launcher 自己的桥接枚举。
    val pixelShape: PixelShape = PixelShape.SQUARE,
    val scaleMode: ScaleMode = ScaleMode.FIT_CENTER,
    val statusBarHeight: Int = 0,
)

enum class ScaleMode {
    FIT_CENTER,
}
