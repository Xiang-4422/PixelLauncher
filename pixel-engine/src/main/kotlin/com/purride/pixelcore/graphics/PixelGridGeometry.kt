package com.purride.pixelcore

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

public data class PixelGridGeometry(
    val cellSize: Float,
    val originX: Float,
    val originY: Float,
    val contentWidth: Float,
    val contentHeight: Float,
    val dotInset: Float,
    val dotSize: Float,
)

/**
 * 逻辑像素网格与真实 Surface 的几何映射器。
 *
 * 同一套结果同时服务绘制和点击映射，避免不同层各自维护一套坐标规则。
 */
public object PixelGridGeometryResolver {
    private const val DOT_INSET_RATIO = 0.16f
    private const val COMPACT_DOT_INSET_PX = 0.5f
    private const val COMPACT_CELL_SIZE_THRESHOLD_PX = 8f

    /**
     * @param pixelGapRatio  间隙大小比例，0.0 = 无间隙，1.0 = 最大间隙（默认 1.0）。
     *   当 [pixelGapEnabled] 为 false 或本值 ≤ 0 时，dotInset 均为 0。
     */
    public fun resolve(
        viewWidth: Int,
        viewHeight: Int,
        profile: ScreenProfile,
        pixelGapEnabled: Boolean = true,
        pixelGapRatio: Float = 1.0f,
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
        val effectiveRatio = pixelGapRatio.coerceIn(0f, 1f)
        val dotInset = if (!pixelGapEnabled || effectiveRatio <= 0f) {
            0f
        } else {
            val maxInset = when {
                cellSize <= COMPACT_CELL_SIZE_THRESHOLD_PX -> COMPACT_DOT_INSET_PX
                else -> max(1f, floor(cellSize * DOT_INSET_RATIO))
            }
            maxInset * effectiveRatio
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

    public fun mapSurfaceToLogical(
        touchX: Float,
        touchY: Float,
        viewWidth: Int,
        viewHeight: Int,
        profile: ScreenProfile,
        pixelGapEnabled: Boolean = true,
        pixelGapRatio: Float = 1.0f,
    ): Pair<Int, Int>? {
        val geometry = resolve(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            profile = profile,
            pixelGapEnabled = pixelGapEnabled,
            pixelGapRatio = pixelGapRatio,
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
