package com.purride.pixellauncherv2.render

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class PixelGridGeometry(
    val cellSize: Float,
    val originX: Float,
    val originY: Float,
    val contentWidth: Float,
    val contentHeight: Float,
    val dotInset: Float,
    val dotSize: Float,
)

object PixelGridGeometryResolver {
    private const val dotInsetRatio = 0.16f

    /**
     * 统一计算逻辑像素网格在真实 View/Surface 中的几何信息。
     *
     * Canvas 路径、GL 路径和触摸坐标映射必须共用这一份结果，避免各自维护一套坐标规则。
     */
    fun resolve(
        viewWidth: Int,
        viewHeight: Int,
        profile: ScreenProfile,
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
        val originX = (viewWidth - contentWidth) / 2f
        val originY = (viewHeight - contentHeight) / 2f
        val dotInset = max(1f, floor(cellSize * dotInsetRatio))
        val dotSize = max(1f, floor(cellSize - (dotInset * 2f)))
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

    /**
     * 把屏幕坐标映射回逻辑像素坐标，规则与渲染使用的网格几何完全一致。
     */
    fun mapSurfaceToLogical(
        touchX: Float,
        touchY: Float,
        viewWidth: Int,
        viewHeight: Int,
        profile: ScreenProfile,
    ): Pair<Int, Int>? {
        val geometry = resolve(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            profile = profile,
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
