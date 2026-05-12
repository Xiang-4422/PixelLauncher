package com.purride.pixelui

import com.purride.pixelcore.PixelAxis

/**
 * Flutter 风格的主公开轴向类型。
 *
 * 当前阶段底层仍然直接复用 `PixelAxis`，
 * 所以这里先用类型别名稳定公开 API。
 */
typealias Axis = PixelAxis

/**
 * Flutter 风格的对齐枚举。
 *
 * 当前 runtime 只支持最小集合：
 * - `TOP_START`
 * - `TOP_CENTER`
 * - `TOP_END`
 * - `CENTER_START`
 * - `CENTER`
 * - `CENTER_END`
 * - `BOTTOM_START`
 * - `BOTTOM_CENTER`
 * - `BOTTOM_END`
 *
 * 当前直接对齐到最常用的 Flutter 方位集合。
 */
enum class Alignment {
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

enum class AlignmentDirectional {
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

enum class MainAxisAlignment {
    START,
    CENTER,
    END,
    SPACE_BETWEEN,
    SPACE_AROUND,
    SPACE_EVENLY,
}

enum class MainAxisSize {
    MIN,
    MAX,
}

enum class CrossAxisAlignment {
    START,
    CENTER,
    END,
    STRETCH,
}

enum class FlexFit {
    TIGHT,
    LOOSE,
}

enum class TextAlign {
    START,
    CENTER,
    END,
}
