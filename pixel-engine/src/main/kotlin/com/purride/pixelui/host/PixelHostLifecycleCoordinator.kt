package com.purride.pixelui

import com.purride.pixelcore.PixelGridGeometryResolver
import com.purride.pixelcore.ScreenProfile
import kotlin.math.ceil

/**
 * 协调 Android 宿主生命周期和配置边界。
 *
 * 绘制、手势和文本输入各自有专门 coordinator；这里只保留宿主生命周期相关的
 * 小型 glue code。
 */
internal class PixelHostLifecycleCoordinator(
    private val disposeRender: () -> Unit,
) {
    fun manualInsets(left: Int, top: Int, right: Int, bottom: Int): PixelWindowInsets {
        return PixelWindowInsets(left = left, top = top, right = right, bottom = bottom)
    }

    fun platformInsetsToLogical(
        leftPx: Int,
        topPx: Int,
        rightPx: Int,
        bottomPx: Int,
        viewWidth: Int,
        viewHeight: Int,
        screenProfile: ScreenProfile,
        pixelGapEnabled: Boolean,
        pixelGapRatio: Float,
    ): PixelWindowInsets {
        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            profile = screenProfile,
            pixelGapEnabled = pixelGapEnabled,
            pixelGapRatio = pixelGapRatio,
        ) ?: return PixelWindowInsets.Zero
        val cellSize = geometry.cellSize.coerceAtLeast(1f)
        return PixelWindowInsets(
            left = leftPx.toLogicalInset(cellSize),
            top = topPx.toLogicalInset(cellSize),
            right = rightPx.toLogicalInset(cellSize),
            bottom = bottomPx.toLogicalInset(cellSize),
        )
    }

    fun onDetachedFromWindow() {
        disposeRender()
    }

    private fun Int.toLogicalInset(cellSize: Float): Int {
        if (this <= 0) return 0
        return ceil(this / cellSize).toInt()
    }
}
