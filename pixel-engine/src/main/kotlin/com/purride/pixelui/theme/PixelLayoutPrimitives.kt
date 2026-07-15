package com.purride.pixelui

import com.purride.pixelcore.PixelAxis

/**
 * Flutter 风格的主公开轴向类型。
 *
 * 当前阶段底层仍然直接复用 `PixelAxis`，
 * 所以这里先用类型别名稳定公开 API。
 */
public typealias Axis = PixelAxis

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
public enum class Alignment {
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

/** 枚举 `PixelLayoutPrimitives` 支持的 `AlignmentDirectional` 策略，序号不作为持久化协议。 */
public enum class AlignmentDirectional {
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

/** 枚举 `PixelLayoutPrimitives` 支持的 `MainAxisAlignment` 策略，序号不作为持久化协议。 */
public enum class MainAxisAlignment {
    START,
    CENTER,
    END,
    SPACE_BETWEEN,
    SPACE_AROUND,
    SPACE_EVENLY,
}

/** 枚举 `PixelLayoutPrimitives` 支持的 `MainAxisSize` 策略，序号不作为持久化协议。 */
public enum class MainAxisSize {
    MIN,
    MAX,
}

/** 枚举 `PixelLayoutPrimitives` 支持的 `CrossAxisAlignment` 策略，序号不作为持久化协议。 */
public enum class CrossAxisAlignment {
    START,
    CENTER,
    END,
    STRETCH,
}

/** 枚举 `PixelLayoutPrimitives` 支持的 `FlexFit` 策略，序号不作为持久化协议。 */
public enum class FlexFit {
    TIGHT,
    LOOSE,
}

/** 枚举 `PixelLayoutPrimitives` 支持的 `TextAlign` 策略，序号不作为持久化协议。 */
public enum class TextAlign {
    START,
    CENTER,
    END,
}
