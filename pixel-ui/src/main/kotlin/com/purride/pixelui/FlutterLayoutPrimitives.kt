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

internal fun Alignment.toPixelAlignment(): PixelAlignment {
    return when (this) {
        Alignment.TOP_START -> PixelAlignment.TOP_START
        Alignment.TOP_CENTER -> PixelAlignment.TOP_CENTER
        Alignment.TOP_END -> PixelAlignment.TOP_END
        Alignment.CENTER_START -> PixelAlignment.CENTER_START
        Alignment.CENTER -> PixelAlignment.CENTER
        Alignment.CENTER_END -> PixelAlignment.CENTER_END
        Alignment.BOTTOM_START -> PixelAlignment.BOTTOM_START
        Alignment.BOTTOM_CENTER -> PixelAlignment.BOTTOM_CENTER
        Alignment.BOTTOM_END -> PixelAlignment.BOTTOM_END
    }
}

internal fun MainAxisAlignment.toPixelMainAxisAlignment(): PixelMainAxisAlignment {
    return when (this) {
        MainAxisAlignment.START -> PixelMainAxisAlignment.START
        MainAxisAlignment.CENTER -> PixelMainAxisAlignment.CENTER
        MainAxisAlignment.END -> PixelMainAxisAlignment.END
        MainAxisAlignment.SPACE_BETWEEN -> PixelMainAxisAlignment.SPACE_BETWEEN
        MainAxisAlignment.SPACE_AROUND -> PixelMainAxisAlignment.SPACE_AROUND
        MainAxisAlignment.SPACE_EVENLY -> PixelMainAxisAlignment.SPACE_EVENLY
    }
}

internal fun MainAxisSize.toPixelMainAxisSize(): PixelMainAxisSize {
    return when (this) {
        MainAxisSize.MIN -> PixelMainAxisSize.MIN
        MainAxisSize.MAX -> PixelMainAxisSize.MAX
    }
}

internal fun CrossAxisAlignment.toPixelCrossAxisAlignment(): PixelCrossAxisAlignment {
    return when (this) {
        CrossAxisAlignment.START -> PixelCrossAxisAlignment.START
        CrossAxisAlignment.CENTER -> PixelCrossAxisAlignment.CENTER
        CrossAxisAlignment.END -> PixelCrossAxisAlignment.END
        CrossAxisAlignment.STRETCH -> PixelCrossAxisAlignment.STRETCH
    }
}
