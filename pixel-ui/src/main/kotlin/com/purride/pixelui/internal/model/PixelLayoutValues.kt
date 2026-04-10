package com.purride.pixelui.internal

/**
 * 像素 UI 内部布局对齐方式。
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
 * `Row/Column` 的交叉轴对齐方式。
 */
internal enum class PixelCrossAxisAlignment {
    START,
    CENTER,
    END,
    STRETCH,
}

/**
 * `Row/Column` 的主轴排布方式。
 */
internal enum class PixelMainAxisAlignment {
    START,
    CENTER,
    END,
    SPACE_BETWEEN,
    SPACE_AROUND,
    SPACE_EVENLY,
}

/**
 * `Row/Column` 的主轴尺寸策略。
 */
internal enum class PixelMainAxisSize {
    MIN,
    MAX,
}

/**
 * 像素文本对齐方式。
 */
internal enum class PixelTextAlign {
    START,
    CENTER,
    END,
}

/**
 * flex 权重 child 在分配槽位里的尺寸策略。
 */
internal enum class PixelFlexFit {
    TIGHT,
    LOOSE,
}
