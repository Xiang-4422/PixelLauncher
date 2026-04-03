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

/**
 * 方向性感知的 Flutter 风格边距对象。
 *
 * 它不直接保存 `left/right`，而是保存 `start/end`，
 * 具体映射到哪一侧，由当前 `Directionality` 决定。
 */
data class EdgeInsetsDirectional(
    val start: Int,
    val top: Int,
    val end: Int,
    val bottom: Int,
) {
    companion object {
        fun all(value: Int): EdgeInsetsDirectional {
            return EdgeInsetsDirectional(start = value, top = value, end = value, bottom = value)
        }

        fun symmetric(horizontal: Int = 0, vertical: Int = 0): EdgeInsetsDirectional {
            return EdgeInsetsDirectional(start = horizontal, top = vertical, end = horizontal, bottom = vertical)
        }

        fun only(
            start: Int = 0,
            top: Int = 0,
            end: Int = 0,
            bottom: Int = 0,
        ): EdgeInsetsDirectional {
            return EdgeInsetsDirectional(start = start, top = top, end = end, bottom = bottom)
        }
    }
}

internal fun EdgeInsetsDirectional.resolve(
    direction: TextDirection,
): EdgeInsets {
    return when (direction) {
        TextDirection.LTR -> EdgeInsets(
            left = start,
            top = top,
            right = end,
            bottom = bottom,
        )
        TextDirection.RTL -> EdgeInsets(
            left = end,
            top = top,
            right = start,
            bottom = bottom,
        )
    }
}
