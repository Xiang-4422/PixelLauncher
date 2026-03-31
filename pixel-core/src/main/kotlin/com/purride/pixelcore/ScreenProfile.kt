package com.purride.pixelcore

/**
 * 像素屏幕配置。
 *
 * 这是像素显示内核中最基础的几何描述之一，
 * 用来定义逻辑分辨率、点阵尺寸以及屏幕缩放策略。
 */
data class ScreenProfile(
    val logicalWidth: Int,
    val logicalHeight: Int,
    val dotSizePx: Int,
    val pixelShape: PixelShape = PixelShape.SQUARE,
    val scaleMode: ScaleMode = ScaleMode.FIT_CENTER,
)

/**
 * 单个逻辑像素在真实屏幕上的绘制形状。
 */
enum class PixelShape {
    SQUARE,
    CIRCLE,
    DIAMOND,
}

/**
 * 逻辑画面到物理屏幕的缩放模式。
 *
 * 第一版先只保留当前仓库已经实际使用的策略，
 * 后续再按需要扩展更多模式。
 */
enum class ScaleMode {
    FIT_CENTER,
}
