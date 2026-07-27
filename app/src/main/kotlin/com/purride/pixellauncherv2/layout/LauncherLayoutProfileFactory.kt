package com.purride.pixellauncherv2.layout

import com.purride.pixelcore.PixelShape

/** 根据物理像素尺寸与点距计算 [LauncherLayoutProfile]，并提供可选的点距挡位。 */
object LauncherLayoutProfileFactory {

    /** 未选择点距挡位时使用的默认物理像素点大小。 */
    const val defaultDotSizePx: Int = 12

    /** 设置页“像素大小”可切换的全部点距挡位（单位：物理像素）。 */
    val supportedDotSizePxOptions: List<Int> = listOf(7, 8, 10, 12, 14, 16)

    /**
     * 依据屏幕物理像素尺寸与点距，换算出 [LauncherLayoutProfile]。
     *
     * @param widthPx 屏幕可用宽度（物理像素）
     * @param heightPx 屏幕可用高度（物理像素）
     * @param dotSizePx 单个逻辑像素对应的物理像素边长，小于 1 会被钳制为 1
     * @param pixelShape 绘制逻辑像素点时使用的形状
     * @param statusBarHeightPx 状态栏高度（物理像素），用于换算出逻辑像素下的状态栏高度
     */
    fun create(
        widthPx: Int,
        heightPx: Int,
        dotSizePx: Int = defaultDotSizePx,
        pixelShape: PixelShape = PixelShape.SQUARE,
        statusBarHeightPx: Int = 0,
    ): LauncherLayoutProfile {
        val safeDotSizePx = dotSizePx.coerceAtLeast(1)
        // 物理像素按点距整除，得到逻辑像素下的宽高，至少保留 1 个逻辑像素。
        val logicalWidth = (widthPx.coerceAtLeast(1) / safeDotSizePx).coerceAtLeast(1)
        val logicalHeight = (heightPx.coerceAtLeast(1) / safeDotSizePx).coerceAtLeast(1)
        val statusBarHeight = ceilToLogicalPixels(statusBarHeightPx, safeDotSizePx)
        return LauncherLayoutProfile(
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
            dotSizePx = safeDotSizePx,
            pixelShape = pixelShape,
            statusBarHeight = statusBarHeight,
        )
    }

    /** 返回可选的点距挡位列表（当前恒为 [supportedDotSizePxOptions]，不依赖具体屏幕）。 */
    fun resolutionOptions(): List<Int> {
        return supportedDotSizePxOptions
    }

    /** 将物理像素长度向上取整为逻辑像素数，0 保持为 0，非 0 时至少为 1。 */
    private fun ceilToLogicalPixels(px: Int, dotSizePx: Int): Int {
        val safePx = px.coerceAtLeast(0)
        return if (safePx == 0) {
            0
        } else {
            ((safePx + dotSizePx - 1) / dotSizePx).coerceAtLeast(1)
        }
    }
}
