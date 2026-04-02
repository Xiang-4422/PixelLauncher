package com.purride.pixelui

import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.PixelTextAlign

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

internal fun TextAlign.toPixelTextAlign(): PixelTextAlign {
    return when (this) {
        TextAlign.START -> PixelTextAlign.START
        TextAlign.CENTER -> PixelTextAlign.CENTER
        TextAlign.END -> PixelTextAlign.END
    }
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

internal fun AlignmentDirectional.toPixelAlignment(
    direction: TextDirection,
): PixelAlignment {
    return when (this) {
        AlignmentDirectional.TOP_START -> if (direction == TextDirection.LTR) PixelAlignment.TOP_START else PixelAlignment.TOP_END
        AlignmentDirectional.TOP_CENTER -> PixelAlignment.TOP_CENTER
        AlignmentDirectional.TOP_END -> if (direction == TextDirection.LTR) PixelAlignment.TOP_END else PixelAlignment.TOP_START
        AlignmentDirectional.CENTER_START -> if (direction == TextDirection.LTR) PixelAlignment.CENTER_START else PixelAlignment.CENTER_END
        AlignmentDirectional.CENTER -> PixelAlignment.CENTER
        AlignmentDirectional.CENTER_END -> if (direction == TextDirection.LTR) PixelAlignment.CENTER_END else PixelAlignment.CENTER_START
        AlignmentDirectional.BOTTOM_START -> if (direction == TextDirection.LTR) PixelAlignment.BOTTOM_START else PixelAlignment.BOTTOM_END
        AlignmentDirectional.BOTTOM_CENTER -> PixelAlignment.BOTTOM_CENTER
        AlignmentDirectional.BOTTOM_END -> if (direction == TextDirection.LTR) PixelAlignment.BOTTOM_END else PixelAlignment.BOTTOM_START
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

internal fun MainAxisAlignment.toPixelMainAxisAlignment(
    axis: Axis,
    direction: TextDirection,
): PixelMainAxisAlignment {
    return when {
        axis == Axis.HORIZONTAL && direction == TextDirection.RTL -> when (this) {
            MainAxisAlignment.START -> PixelMainAxisAlignment.END
            MainAxisAlignment.END -> PixelMainAxisAlignment.START
            MainAxisAlignment.CENTER -> PixelMainAxisAlignment.CENTER
            MainAxisAlignment.SPACE_BETWEEN -> PixelMainAxisAlignment.SPACE_BETWEEN
            MainAxisAlignment.SPACE_AROUND -> PixelMainAxisAlignment.SPACE_AROUND
            MainAxisAlignment.SPACE_EVENLY -> PixelMainAxisAlignment.SPACE_EVENLY
        }

        else -> toPixelMainAxisAlignment()
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

internal fun CrossAxisAlignment.toPixelCrossAxisAlignment(
    axis: Axis,
    direction: TextDirection,
): PixelCrossAxisAlignment {
    return when {
        axis == Axis.VERTICAL && direction == TextDirection.RTL -> when (this) {
            CrossAxisAlignment.START -> PixelCrossAxisAlignment.END
            CrossAxisAlignment.END -> PixelCrossAxisAlignment.START
            CrossAxisAlignment.CENTER -> PixelCrossAxisAlignment.CENTER
            CrossAxisAlignment.STRETCH -> PixelCrossAxisAlignment.STRETCH
        }

        else -> toPixelCrossAxisAlignment()
    }
}
