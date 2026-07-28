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
 * 逻辑网格投影到物理视口的唯一策略模型；三个轴彼此正交，调用方不需要组合枚举。
 * 默认构造值（Contain + Integer + Center）就是引擎的 canonical 默认策略。
 *
 * Orthogonal physical-to-logical viewport policy.
 *
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
)
