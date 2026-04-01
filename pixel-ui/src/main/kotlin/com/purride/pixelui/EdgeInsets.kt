package com.purride.pixelui

/**
 * Flutter 风格边距对象。
 *
 * 当前阶段先承接最常用的 `all / symmetric / only` 三种表达，
 * 让页面层少直接暴露 `PixelModifier.padding(...)`。
 */
data class EdgeInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    companion object {
        fun all(value: Int): EdgeInsets {
            return EdgeInsets(left = value, top = value, right = value, bottom = value)
        }

        fun symmetric(horizontal: Int = 0, vertical: Int = 0): EdgeInsets {
            return EdgeInsets(left = horizontal, top = vertical, right = horizontal, bottom = vertical)
        }

        fun only(
            left: Int = 0,
            top: Int = 0,
            right: Int = 0,
            bottom: Int = 0,
        ): EdgeInsets {
            return EdgeInsets(left = left, top = top, right = right, bottom = bottom)
        }
    }
}
