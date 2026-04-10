package com.purride.pixelui.internal.legacy

/**
 * 当前阶段的最小布局对齐方式。
 */
internal enum class PixelAlignment {
    TOP_START,
    TOP_CENTER,
    TOP_END,
    CENTER_START,
    CENTER,
    CENTER_END,
    BOTTOM_START,
    BOTTOM_CENTER,
    BOTTOM_END,
}

/**
 * `Row/Column` 当前阶段只支持交叉轴对齐。
 *
 * 主轴排布还没有进入这一轮实现，所以这里只先提供最常用的
 * `START / CENTER / END` 三档，先把组合布局的基础语义补稳。
 */
internal enum class PixelCrossAxisAlignment {
    START,
    CENTER,
    END,
    STRETCH,
}

/**
 * `Row/Column` 的主轴排布方式。
 *
 * 当前先支持最基础的起始、居中、末尾三种排布，先把常见布局场景补齐。
 */
internal enum class PixelMainAxisAlignment {
    START,
    CENTER,
    END,
    SPACE_BETWEEN,
    SPACE_AROUND,
    SPACE_EVENLY,
}

internal enum class PixelMainAxisSize {
    MIN,
    MAX,
}

internal enum class PixelTextAlign {
    START,
    CENTER,
    END,
}
