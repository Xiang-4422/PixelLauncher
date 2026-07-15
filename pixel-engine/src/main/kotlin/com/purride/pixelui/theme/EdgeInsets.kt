package com.purride.pixelui

import com.purride.pixelui.internal.PixelArtifactInternalApi

/**
 * Flutter 风格边距对象。
 *
 * 提供 `all / symmetric / only` 三种工厂方法，与公开 widget API 的
 * Padding/Container/Decoration 等组件直接对接，无需任何链式 modifier 包装。
 */
public data class EdgeInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    /** 集中提供 `EdgeInsets` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 按 `all` 参数创建 `EdgeInsets` 的规范化几何或曲线值。 */
        public fun all(value: Int): EdgeInsets {
            return EdgeInsets(left = value, top = value, right = value, bottom = value)
        }

        /** 按 `symmetric` 参数创建 `EdgeInsets` 的规范化几何或曲线值。 */
        public fun symmetric(horizontal: Int = 0, vertical: Int = 0): EdgeInsets {
            return EdgeInsets(left = horizontal, top = vertical, right = horizontal, bottom = vertical)
        }

        /** 处理 `EdgeInsets` 的 `only` 输入或事件，并按消费结果决定后续传播。 */
        public fun only(
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
public data class EdgeInsetsDirectional(
    val start: Int,
    val top: Int,
    val end: Int,
    val bottom: Int,
) {
    /** 集中提供 `EdgeInsets` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 按 `all` 参数创建 `EdgeInsets` 的规范化几何或曲线值。 */
        public fun all(value: Int): EdgeInsetsDirectional {
            return EdgeInsetsDirectional(start = value, top = value, end = value, bottom = value)
        }

        /** 按 `symmetric` 参数创建 `EdgeInsets` 的规范化几何或曲线值。 */
        public fun symmetric(horizontal: Int = 0, vertical: Int = 0): EdgeInsetsDirectional {
            return EdgeInsetsDirectional(start = horizontal, top = vertical, end = horizontal, bottom = vertical)
        }

        /** 处理 `EdgeInsets` 的 `only` 输入或事件，并按消费结果决定后续传播。 */
        public fun only(
            start: Int = 0,
            top: Int = 0,
            end: Int = 0,
            bottom: Int = 0,
        ): EdgeInsetsDirectional {
            return EdgeInsetsDirectional(start = start, top = top, end = end, bottom = bottom)
        }
    }
}

/** 将逻辑方向边距解析为 runtime 布局使用的物理方向边距。 */
@PixelArtifactInternalApi
public fun EdgeInsetsDirectional.resolve(
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
