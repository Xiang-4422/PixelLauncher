package com.purride.pixelcore

/**
 * 逻辑屏幕配置。
 *
 * 这层只描述“逻辑像素世界”如何映射到真实 Surface，
 * 不负责任何页面排版语义。
 */
public data class ScreenProfile(
    /** Number of logical pixel columns rendered by the engine. */
    val logicalWidth: Int,
    /** Number of logical pixel rows rendered by the engine. */
    val logicalHeight: Int,
    /** Preferred physical dot size used by adaptive profile factories. */
    val dotSizePx: Int,
    /** Shape used when the Host paints each logical pixel. */
    val pixelShape: PixelShape = PixelShape.SQUARE,
    /** Frozen legacy scale selector; new Hosts use [PixelViewportPolicy] additively. */
    val scaleMode: ScaleMode = ScaleMode.FIT_CENTER,
)

/** 定义 `PixelShape` 在 `ScreenProfile` 中承担的数据与行为边界。
 *
 * Physical shape used to paint one logical pixel cell.
 */
public enum class PixelShape {
    /** Axis-aligned rectangular pixel. */
    SQUARE,

    /** Circular pixel inscribed in its cell. */
    CIRCLE,

    /** Forty-five-degree rotated square inscribed in its cell. */
    DIAMOND,
}

/**
 * 定义 `ScaleMode` 在 `ScreenProfile` 中承担的数据与行为边界。
 *
 * Frozen pre-1.0 viewport mode retained in [ScreenProfile]'s constructor and copy ABI.
 *
 * [FIT_CENTER] maps exactly to contain + integer quantization + centered alignment. New code that
 * needs an orthogonal policy should use [PixelViewportPolicy] instead of extending this enum with
 * every possible combination.
 */
public enum class ScaleMode {
    /** Preserves the historical centered integer contain behavior. */
    FIT_CENTER,
}

/** 定义 `PixelViewportFit` 在 `ScreenProfile` 中承担的数据与行为边界。
 *
 * Determines whether the complete logical grid is contained or allowed to crop at the viewport.
 */
public enum class PixelViewportFit {
    /** Selects the smaller axis scale so the complete logical grid remains visible. */
    CONTAIN,

    /** Selects the larger axis scale so the viewport is filled and excess grid content is cropped. */
    COVER,
}

/** 定义 `PixelViewportQuantization` 在 `ScreenProfile` 中承担的数据与行为边界。
 *
 * Controls whether the physical cell scale may retain a fractional component.
 */
public enum class PixelViewportQuantization {
    /** Uses an integer cell size while preserving the selected contain or cover invariant. */
    INTEGER,

    /** Uses the exact positive floating-point scale resolved from the viewport axes. */
    FRACTIONAL,
}

/** 定义 `PixelViewportAlignment` 在 `ScreenProfile` 中承担的数据与行为边界。
 *
 * Positions scaled grid content inside, or around, the physical viewport.
 */
public enum class PixelViewportAlignment {
    /** Aligns the content to the physical top-left corner. */
    TOP_LEFT,

    /** Centers the content horizontally and aligns it to the physical top edge. */
    TOP_CENTER,

    /** Aligns the content to the physical top-right corner. */
    TOP_RIGHT,

    /** Aligns the content to the physical left edge and centers it vertically. */
    CENTER_LEFT,

    /** Centers the content on both physical axes. */
    CENTER,

    /** Aligns the content to the physical right edge and centers it vertically. */
    CENTER_RIGHT,

    /** Aligns the content to the physical bottom-left corner. */
    BOTTOM_LEFT,

    /** Centers the content horizontally and aligns it to the physical bottom edge. */
    BOTTOM_CENTER,

    /** Aligns the content to the physical bottom-right corner. */
    BOTTOM_RIGHT,
    ;

    /** Horizontal free-space fraction placed before the content. */
    internal val horizontalFraction: Float
        get() = when (this) {
            TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> 0f
            TOP_CENTER, CENTER, BOTTOM_CENTER -> 0.5f
            TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> 1f
        }

    /** Vertical free-space fraction placed before the content. */
    internal val verticalFraction: Float
        get() = when (this) {
            TOP_LEFT, TOP_CENTER, TOP_RIGHT -> 0f
            CENTER_LEFT, CENTER, CENTER_RIGHT -> 0.5f
            BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> 1f
        }
}

/**
 * 定义 `PixelViewportPolicy` 在 `ScreenProfile` 中承担的数据与行为边界。
 *
 * Orthogonal physical-to-logical viewport policy.
 *
 * The three axes intentionally remain independent so consumers do not need a combinatorial enum.
 * Stretch is absent by design: every policy uses one uniform cell scale and therefore preserves
 * square logical-pixel geometry.
 *
 * @property fit Whether the complete grid is contained or excess content is cropped.
 * @property quantization Whether the cell scale is integer or fractional.
 * @property alignment Placement of free or cropped space around the scaled grid.
 */
public data class PixelViewportPolicy(
    public val fit: PixelViewportFit = PixelViewportFit.CONTAIN,
    public val quantization: PixelViewportQuantization = PixelViewportQuantization.INTEGER,
    public val alignment: PixelViewportAlignment = PixelViewportAlignment.CENTER,
) {
    /** 集中提供 `ScreenProfile` 的 `<companion>` 共享入口。
 *
 * Stable policies and the frozen [ScaleMode] compatibility mapping.
 */
    public companion object {
        /** 公开 `ScreenProfile` 的 `LegacyFitCenter` 配置或运行值。
 *
 * Exact policy represented by the historical [ScaleMode.FIT_CENTER] value.
 */
        public val LegacyFitCenter: PixelViewportPolicy = PixelViewportPolicy()

        /** 创建或解析 `ScreenProfile` 的 `fromLegacyScaleMode` 结果，并在返回前校验输入。
 *
 * Maps a frozen legacy mode to its additive orthogonal policy.
 */
        public fun fromLegacyScaleMode(scaleMode: ScaleMode): PixelViewportPolicy {
            return when (scaleMode) {
                ScaleMode.FIT_CENTER -> LegacyFitCenter
            }
        }
    }
}
