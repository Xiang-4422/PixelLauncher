package com.purride.pixelcore

/**
 * 轴向定义。
 *
 * 低层运动原语和合成原语都只依赖“沿哪个轴运动”，不依赖具体 UI 语义。
 */
enum class PixelAxis {
    HORIZONTAL,
    VERTICAL,
}
