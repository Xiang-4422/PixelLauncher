package com.purride.pixelcore

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** 定义 `PixelGridGeometry` 在 `PixelGridGeometry` 中承担的数据与行为边界。
 *
 * Immutable mapping from the logical grid into one physical Host viewport.
 */
public data class PixelGridGeometry(
    /** Uniform physical pixels occupied by one logical cell. */
    val cellSize: Float,
    /** Physical x origin of logical column zero; may be negative for cover policies. */
    val originX: Float,
    /** Physical y origin of logical row zero; may be negative for cover policies. */
    val originY: Float,
    /** Physical width occupied by the complete logical grid. */
    val contentWidth: Float,
    /** Physical height occupied by the complete logical grid. */
    val contentHeight: Float,
    /** Physical inset applied to interior pixel-dot edges. */
    val dotInset: Float,
    /** Physical extent of the painted dot inside one cell. */
    val dotSize: Float,
)

/**
 * 逻辑像素网格与真实 Surface 的几何映射器。
 *
 * 同一套结果同时服务绘制和点击映射，避免不同层各自维护一套坐标规则。
 */
public object PixelGridGeometryResolver {
    /** Default fractional inset used by cells larger than the compact threshold. */
    private const val DOT_INSET_RATIO = 0.16f
    /** Stable half-pixel inset used by compact cells. */
    private const val COMPACT_DOT_INSET_PX = 0.5f
    /** Largest cell size that still uses [COMPACT_DOT_INSET_PX]. */
    private const val COMPACT_CELL_SIZE_THRESHOLD_PX = 8f

    /**
     * 按冻结的缩放兼容规则解析 View、逻辑网格和像素点之间的统一几何。
     *
     * Resolves geometry with the frozen [ScreenProfile.scaleMode] compatibility mapping.
     *
     * @param pixelGapRatio  间隙大小比例，0.0 = 无间隙，1.0 = 最大间隙（默认 1.0）。
     *   当 [pixelGapEnabled] 为 false 或本值 ≤ 0 时，dotInset 均为 0。
     */
    public fun resolve(
        viewWidth: Int,
        viewHeight: Int,
        profile: ScreenProfile,
        pixelGapEnabled: Boolean = true,
        pixelGapRatio: Float = 1.0f,
    ): PixelGridGeometry? {
        return resolve(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            profile = profile,
            viewportPolicy = PixelViewportPolicy.fromLegacyScaleMode(profile.scaleMode),
            pixelGapEnabled = pixelGapEnabled,
            pixelGapRatio = pixelGapRatio,
        )
    }

    /**
 * 查询 `PixelGridGeometry` 的 `resolve` 结果，不产生额外状态变更。
 *
     * Resolves one uniform grid transform from an explicit orthogonal viewport policy.
     *
     * Integer contain rounds down and integer cover rounds up so quantization cannot violate the
     * selected fit invariant. Fractional policies retain the exact axis-derived scale. Integer
     * alignment floors a half-pixel remainder to preserve historical `FIT_CENTER` origins.
     */
    public fun resolve(
        /** Physical Host width in pixels. */
        viewWidth: Int,
        /** Physical Host height in pixels. */
        viewHeight: Int,
        /** Logical grid dimensions and pixel shape. */
        profile: ScreenProfile,
        /** Explicit fit, quantization and alignment behavior. */
        viewportPolicy: PixelViewportPolicy,
        /** Whether individual logical dots retain a visible gap. */
        pixelGapEnabled: Boolean = true,
        /** Gap fraction clamped to the inclusive `0..1` range. */
        pixelGapRatio: Float = 1.0f,
    ): PixelGridGeometry? {
        if (viewWidth <= 0 || viewHeight <= 0) {
            return null
        }
        /** Horizontal physical pixels available for one logical column. */
        val horizontalScale = viewWidth.toFloat() / profile.logicalWidth.toFloat()
        /** Vertical physical pixels available for one logical row. */
        val verticalScale = viewHeight.toFloat() / profile.logicalHeight.toFloat()
        /** Exact uniform scale selected before optional integer quantization. */
        val exactScale = when (viewportPolicy.fit) {
            PixelViewportFit.CONTAIN -> min(horizontalScale, verticalScale)
            PixelViewportFit.COVER -> max(horizontalScale, verticalScale)
        }
        /** Final cell scale preserving both quantization and fit invariants. */
        val cellSize = when (viewportPolicy.quantization) {
            PixelViewportQuantization.FRACTIONAL -> exactScale
            PixelViewportQuantization.INTEGER -> when (viewportPolicy.fit) {
                PixelViewportFit.CONTAIN -> floor(exactScale)
                PixelViewportFit.COVER -> ceil(exactScale)
            }
        }
        if (!cellSize.isFinite() || cellSize <= 0f) {
            return null
        }

        /** Physical width of all logical columns at the selected scale. */
        val contentWidth = cellSize * profile.logicalWidth
        /** Physical height of all logical rows at the selected scale. */
        val contentHeight = cellSize * profile.logicalHeight
        /** Physical horizontal origin, including negative cover overflow. */
        val originX = resolveAlignedOrigin(
            freeSpace = viewWidth - contentWidth,
            alignmentFraction = viewportPolicy.alignment.horizontalFraction,
            quantization = viewportPolicy.quantization,
        )
        /** Physical vertical origin, including negative cover overflow. */
        val originY = resolveAlignedOrigin(
            freeSpace = viewHeight - contentHeight,
            alignmentFraction = viewportPolicy.alignment.verticalFraction,
            quantization = viewportPolicy.quantization,
        )
        /** Validated consumer gap fraction. */
        val effectiveRatio = pixelGapRatio.coerceIn(0f, 1f)
        /** Physical inset applied to interior dot edges. */
        val dotInset = if (!pixelGapEnabled || effectiveRatio <= 0f) {
            0f
        } else {
            /** Maximum inset before applying the consumer ratio. */
            val maxInset = when {
                cellSize <= COMPACT_CELL_SIZE_THRESHOLD_PX -> COMPACT_DOT_INSET_PX
                else -> max(1f, floor(cellSize * DOT_INSET_RATIO))
            }
            maxInset * effectiveRatio
        }
        /** Positive painted dot size retained even under extreme custom gap input. */
        val dotSize = max(1f, cellSize - (dotInset * 2f))
        return PixelGridGeometry(
            cellSize = cellSize,
            originX = originX,
            originY = originY,
            contentWidth = contentWidth,
            contentHeight = contentHeight,
            dotInset = dotInset,
            dotSize = dotSize,
        )
    }

    /** 执行 `PixelGridGeometry` 的 `mapSurfaceToLogical` 公开行为；具体参数、返回和副作用见下文。
 *
 * Maps a physical point using the frozen [ScreenProfile.scaleMode] compatibility policy.
 */
    public fun mapSurfaceToLogical(
        touchX: Float,
        touchY: Float,
        viewWidth: Int,
        viewHeight: Int,
        profile: ScreenProfile,
        pixelGapEnabled: Boolean = true,
        pixelGapRatio: Float = 1.0f,
    ): Pair<Int, Int>? {
        return mapSurfaceToLogical(
            touchX = touchX,
            touchY = touchY,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            profile = profile,
            viewportPolicy = PixelViewportPolicy.fromLegacyScaleMode(profile.scaleMode),
            pixelGapEnabled = pixelGapEnabled,
            pixelGapRatio = pixelGapRatio,
        )
    }

    /** 执行 `PixelGridGeometry` 的 `mapSurfaceToLogical` 公开行为；具体参数、返回和副作用见下文。
 *
 * Maps a physical point through the exact transform produced by [resolve].
 */
    public fun mapSurfaceToLogical(
        /** Physical pointer x coordinate. */
        touchX: Float,
        /** Physical pointer y coordinate. */
        touchY: Float,
        /** Physical Host width in pixels. */
        viewWidth: Int,
        /** Physical Host height in pixels. */
        viewHeight: Int,
        /** Logical grid dimensions and pixel shape. */
        profile: ScreenProfile,
        /** Explicit fit, quantization and alignment behavior. */
        viewportPolicy: PixelViewportPolicy,
        /** Whether painted cells retain a visible gap. */
        pixelGapEnabled: Boolean = true,
        /** Gap fraction shared with paint geometry. */
        pixelGapRatio: Float = 1.0f,
    ): Pair<Int, Int>? {
        /** Shared paint/touch geometry for the current viewport. */
        val geometry = resolve(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            profile = profile,
            viewportPolicy = viewportPolicy,
            pixelGapEnabled = pixelGapEnabled,
            pixelGapRatio = pixelGapRatio,
        ) ?: return null

        /** Pointer x relative to logical column zero. */
        val localX = touchX - geometry.originX
        /** Pointer y relative to logical row zero. */
        val localY = touchY - geometry.originY
        if (localX < 0f || localY < 0f || localX >= geometry.contentWidth || localY >= geometry.contentHeight) {
            return null
        }

        /** Logical column selected through the inverse uniform scale. */
        val logicalX = (localX / geometry.cellSize).toInt().coerceIn(0, profile.logicalWidth - 1)
        /** Logical row selected through the inverse uniform scale. */
        val logicalY = (localY / geometry.cellSize).toInt().coerceIn(0, profile.logicalHeight - 1)
        return logicalX to logicalY
    }

    /** Resolves an axis origin while retaining exact fractional placement when requested. */
    private fun resolveAlignedOrigin(
        /** Physical space remaining after scaled content, possibly negative for cover. */
        freeSpace: Float,
        /** Fraction of [freeSpace] placed before the content. */
        alignmentFraction: Float,
        /** Quantization policy controlling historical integer-origin flooring. */
        quantization: PixelViewportQuantization,
    ): Float {
        /** Exact aligned physical origin before compatibility rounding. */
        val exactOrigin = freeSpace * alignmentFraction
        return when (quantization) {
            PixelViewportQuantization.INTEGER -> floor(exactOrigin)
            PixelViewportQuantization.FRACTIONAL -> exactOrigin
        }
    }
}
