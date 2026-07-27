package com.purride.pixellauncherv2.layout

import com.purride.pixelcore.PixelShape

/**
 * Launcher 页面布局快照：描述当前屏幕在“逻辑像素”坐标系下的尺寸与绘制参数。
 * 注意与 pixelcore.ScreenProfile（Engine 内部的渲染画布配置）无关，命名上做区分以避免混淆。
 */
data class LauncherLayoutProfile(
    /** 屏幕可用宽度，单位为逻辑像素（物理像素 / [dotSizePx]）。 */
    val logicalWidth: Int,
    /** 屏幕可用高度，单位为逻辑像素（物理像素 / [dotSizePx]）。 */
    val logicalHeight: Int,
    /** 单个逻辑像素对应的物理像素边长。 */
    val dotSizePx: Int,
    // 直接使用 Engine 的 PixelShape，不再维护 Launcher 自己的桥接枚举。
    /** 绘制逻辑像素点时使用的形状。 */
    val pixelShape: PixelShape = PixelShape.SQUARE,
    /** 状态栏高度，单位为逻辑像素，用于内容区域避让状态栏。 */
    val statusBarHeight: Int = 0,
)
