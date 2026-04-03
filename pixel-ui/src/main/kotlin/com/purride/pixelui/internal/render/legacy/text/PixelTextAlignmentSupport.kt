package com.purride.pixelui.internal

import com.purride.pixelui.TextDirection
import com.purride.pixelui.internal.legacy.PixelTextAlign

/**
 * 负责 legacy 文本在指定宽度中的水平对齐计算。
 */
internal class PixelTextAlignmentSupport {
    /**
     * 计算当前行文本的起始横坐标。
     */
    fun lineStartX(
        left: Int,
        width: Int,
        lineWidth: Int,
        textAlign: PixelTextAlign,
        textDirection: TextDirection,
    ): Int {
        return when (textAlign) {
            PixelTextAlign.START -> when (textDirection) {
                TextDirection.LTR -> left
                TextDirection.RTL -> left + (width - lineWidth).coerceAtLeast(0)
            }

            PixelTextAlign.CENTER -> left + ((width - lineWidth).coerceAtLeast(0) / 2)
            PixelTextAlign.END -> when (textDirection) {
                TextDirection.LTR -> left + (width - lineWidth).coerceAtLeast(0)
                TextDirection.RTL -> left
            }
        }
    }

    /**
     * 判断指定对齐方式是否需要占满整行宽度。
     */
    fun needsFullWidth(
        textAlign: PixelTextAlign,
        textDirection: TextDirection,
    ): Boolean {
        return when (textAlign) {
            PixelTextAlign.CENTER -> true
            PixelTextAlign.START -> textDirection == TextDirection.RTL
            PixelTextAlign.END -> textDirection == TextDirection.LTR
        }
    }
}
