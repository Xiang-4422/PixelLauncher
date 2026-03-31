package com.purride.pixelcore

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * 逻辑像素网格在真实显示区域中的几何结果。
 */
data class PixelGridGeometry(
    val cellSize: Float,
    val originX: Float,
    val originY: Float,
    val contentWidth: Float,
    val contentHeight: Float,
    val dotInset: Float,
    val dotSize: Float,
)

/**
 * 像素网格几何解析器。
 *
 * 它统一负责渲染几何和触摸坐标映射，
 * 避免 Canvas 路径、GL 路径和命中测试各自维护一套坐标规则。
 */
object PixelGridGeometryResolver {
    private const val dotInsetRatio = 0.16f
    private const val compactDotInsetPx = 0.5f
    private const val compactCellSizeThresholdPx = 8f

    fun resolve(
        viewWidth: Int,
        viewHeight: Int,
        profile: ScreenProfile,
        pixelGapEnabled: Boolean = true,
    ): PixelGridGeometry? {
        if (viewWidth <= 0 || viewHeight <= 0) {
            return null
        }
        val cellSize = when (profile.scaleMode) {
            ScaleMode.FIT_CENTER -> floor(
                min(
                    viewWidth.toFloat() / profile.logicalWidth.toFloat(),
                    viewHeight.toFloat() / profile.logicalHeight.toFloat(),
                ),
            )
        }
        if (cellSize <= 0f) {
            return null
        }
        val contentWidth = cellSize * profile.logicalWidth
        val contentHeight = cellSize * profile.logicalHeight
        val originX = floor((viewWidth - contentWidth) / 2f)
        val originY = floor((viewHeight - contentHeight) / 2f)
        val dotInset = if (!pixelGapEnabled) {
            0f
        } else when {
            cellSize <= compactCellSizeThresholdPx -> compactDotInsetPx
            else -> max(1f, floor(cellSize * dotInsetRatio))
        }
        val dotSize = max(1f, cellSize - (dotInset * 2f))
        return PixelGridGeometry(
            cellSize = cellSize,
            originX = originX,
            originY = originY,
            contentWidth = contentWidth,
            contentHeight = contentHeight,
            dotInset = dotInset,
            dotSize = dotSize,
        )
    }

    fun mapSurfaceToLogical(
        touchX: Float,
        touchY: Float,
        viewWidth: Int,
        viewHeight: Int,
        profile: ScreenProfile,
        pixelGapEnabled: Boolean = true,
    ): Pair<Int, Int>? {
        val geometry = resolve(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            profile = profile,
            pixelGapEnabled = pixelGapEnabled,
        ) ?: return null
        val localX = touchX - geometry.originX
        val localY = touchY - geometry.originY
        if (localX < 0f || localY < 0f || localX >= geometry.contentWidth || localY >= geometry.contentHeight) {
            return null
        }
        val logicalX = (localX / geometry.cellSize).toInt().coerceIn(0, profile.logicalWidth - 1)
        val logicalY = (localY / geometry.cellSize).toInt().coerceIn(0, profile.logicalHeight - 1)
        return logicalX to logicalY
    }
}
