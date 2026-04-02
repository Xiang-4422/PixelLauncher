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
 * - `CENTER`
 *
 * 后续如果补 `Align`，再继续扩展更多方位。
 */
enum class Alignment {
    TOP_START,
    CENTER,
}

enum class MainAxisAlignment {
    START,
    CENTER,
    END,
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

internal fun Alignment.toPixelAlignment(): PixelAlignment {
    return when (this) {
        Alignment.TOP_START -> PixelAlignment.TOP_START
        Alignment.CENTER -> PixelAlignment.CENTER
    }
}

internal fun MainAxisAlignment.toPixelMainAxisAlignment(): PixelMainAxisAlignment {
    return when (this) {
        MainAxisAlignment.START -> PixelMainAxisAlignment.START
        MainAxisAlignment.CENTER -> PixelMainAxisAlignment.CENTER
        MainAxisAlignment.END -> PixelMainAxisAlignment.END
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
