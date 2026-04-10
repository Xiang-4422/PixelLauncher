package com.purride.pixelui.internal

import com.purride.pixelui.internal.legacy.PixelAlignment
import com.purride.pixelui.internal.legacy.PixelCrossAxisAlignment
import com.purride.pixelui.internal.legacy.PixelMainAxisAlignment

/**
 * 负责 legacy 布局阶段的对齐与主轴排布计算。
 */
internal class PixelAlignmentLayoutSupport {
    /**
     * 计算 cross axis 上的起始坐标。
     */
    fun crossAxisStart(
        containerStart: Int,
        containerExtent: Int,
        childExtent: Int,
        alignment: PixelCrossAxisAlignment,
    ): Int {
        val remaining = (containerExtent - childExtent).coerceAtLeast(0)
        return when (alignment) {
            PixelCrossAxisAlignment.START -> containerStart
            PixelCrossAxisAlignment.CENTER -> containerStart + (remaining / 2)
            PixelCrossAxisAlignment.END -> containerStart + remaining
            PixelCrossAxisAlignment.STRETCH -> containerStart
        }
    }

    /**
     * 计算主轴上的起始位置和子项间距。
     */
    fun mainAxisArrangement(
        containerStart: Int,
        containerExtent: Int,
        contentExtent: Int,
        spacing: Int,
        childCount: Int,
        alignment: PixelMainAxisAlignment,
    ): MainAxisArrangement {
        val remaining = (containerExtent - contentExtent).coerceAtLeast(0)
        return when (alignment) {
            PixelMainAxisAlignment.START -> MainAxisArrangement(containerStart, spacing)
            PixelMainAxisAlignment.CENTER -> MainAxisArrangement(containerStart + (remaining / 2), spacing)
            PixelMainAxisAlignment.END -> MainAxisArrangement(containerStart + remaining, spacing)
            PixelMainAxisAlignment.SPACE_BETWEEN -> {
                if (childCount <= 1) {
                    MainAxisArrangement(containerStart, spacing)
                } else {
                    MainAxisArrangement(containerStart, spacing + (remaining / (childCount - 1)))
                }
            }
            PixelMainAxisAlignment.SPACE_AROUND -> {
                if (childCount <= 0) {
                    MainAxisArrangement(containerStart, spacing)
                } else {
                    val unit = remaining / childCount
                    MainAxisArrangement(containerStart + (unit / 2), spacing + unit)
                }
            }
            PixelMainAxisAlignment.SPACE_EVENLY -> {
                val slotCount = childCount + 1
                val unit = if (slotCount <= 0) 0 else remaining / slotCount
                MainAxisArrangement(containerStart + unit, spacing + unit)
            }
        }
    }

    /**
     * 根据对齐方式计算子节点在外层 bounds 中的最终位置。
     */
    fun alignedBounds(
        outerBounds: PixelRect,
        childSize: PixelSize,
        alignment: PixelAlignment,
    ): PixelRect {
        val clampedWidth = childSize.width.coerceAtMost(outerBounds.width)
        val clampedHeight = childSize.height.coerceAtMost(outerBounds.height)
        val centeredLeft = outerBounds.left + ((outerBounds.width - childSize.width).coerceAtLeast(0) / 2)
        val endLeft = outerBounds.left + (outerBounds.width - childSize.width).coerceAtLeast(0)
        val centeredTop = outerBounds.top + ((outerBounds.height - childSize.height).coerceAtLeast(0) / 2)
        val endTop = outerBounds.top + (outerBounds.height - childSize.height).coerceAtLeast(0)

        val left = when (alignment) {
            PixelAlignment.TOP_START,
            PixelAlignment.CENTER_START,
            PixelAlignment.BOTTOM_START -> outerBounds.left
            PixelAlignment.TOP_CENTER,
            PixelAlignment.CENTER,
            PixelAlignment.BOTTOM_CENTER -> centeredLeft
            PixelAlignment.TOP_END,
            PixelAlignment.CENTER_END,
            PixelAlignment.BOTTOM_END -> endLeft
        }
        val top = when (alignment) {
            PixelAlignment.TOP_START,
            PixelAlignment.TOP_CENTER,
            PixelAlignment.TOP_END -> outerBounds.top
            PixelAlignment.CENTER_START,
            PixelAlignment.CENTER,
            PixelAlignment.CENTER_END -> centeredTop
            PixelAlignment.BOTTOM_START,
            PixelAlignment.BOTTOM_CENTER,
            PixelAlignment.BOTTOM_END -> endTop
        }

        return PixelRect(
            left = left,
            top = top,
            width = clampedWidth,
            height = clampedHeight,
        )
    }
}

/**
 * 表示主轴排布的起点和子项后续间距。
 */
internal data class MainAxisArrangement(
    val start: Int,
    val spacingAfterChild: Int,
)
