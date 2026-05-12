package com.purride.pixelui

import com.purride.pixelcore.PixelShape

/**
 * 宿主希望使用的点阵显示偏好。
 *
 * 这一层只表达“点大小和像素形状偏好”，真正的逻辑分辨率仍然交给
 * `PixelHostView` 根据当前可用尺寸自动推导。
 */
data class PixelHostProfilePreference(
    val dotSizePx: Int,
    val pixelShape: PixelShape = PixelShape.SQUARE,
)
