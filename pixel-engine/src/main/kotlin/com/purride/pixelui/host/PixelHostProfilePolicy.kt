package com.purride.pixelui

import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.PixelViewportPolicy
import com.purride.pixelcore.PixelGridGeometryResolver
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelcore.ScreenProfileFactory
import kotlin.math.roundToInt

/**
 * 定义 `PixelHostProfilePolicy` 的可替换调用契约。
 *
 * Host 解析逻辑 [ScreenProfile] 的唯一配置入口：调用方声明“要什么”，Host 负责在
 * 视口尺寸、密度或视口策略变化后重新解析出“是什么”。
 *
 * Declares how a Host resolves its logical [ScreenProfile] from physical viewport information.
 *
 * [Fixed] pins one caller-owned grid; the adaptive variants are re-evaluated after viewport size,
 * density or viewport strategy changes.
 */
public sealed interface PixelHostProfilePolicy {
    /**
 * 定义 `Fixed` 在 `PixelHostProfilePolicy` 中承担的数据与行为边界。
 *
     * Keeps one caller-owned profile unchanged across viewport and density changes.
     *
     * @property profile Exact profile retained by the Host.
     */
    public data class Fixed(
        public val profile: ScreenProfile,
    ) : PixelHostProfilePolicy

    /**
 * 定义 `AdaptivePixels` 在 `PixelHostProfilePolicy` 中承担的数据与行为边界。
 *
     * Derives logical dimensions from a physical logical-pixel dot size.
     *
     * @property dotSizePx Positive physical pixels allocated to one logical pixel.
     * @property pixelShape Shape painted inside each resolved logical pixel cell.
     */
    public data class AdaptivePixels(
        public val dotSizePx: Int,
        public val pixelShape: PixelShape = PixelShape.SQUARE,
    ) : PixelHostProfilePolicy {
        /** Rejects invalid dot sizes before they can silently collapse a logical viewport. */
        init {
            require(dotSizePx > 0) { "dotSizePx must be greater than zero" }
        }
    }

    /**
 * 定义 `AdaptiveDp` 在 `PixelHostProfilePolicy` 中承担的数据与行为边界。
 *
     * Derives physical dot size from density-independent pixels, then resolves logical dimensions.
     *
     * @property dotSizeDp Positive density-independent size of one logical pixel.
     * @property pixelShape Shape painted inside each resolved logical pixel cell.
     */
    public data class AdaptiveDp(
        public val dotSizeDp: Float,
        public val pixelShape: PixelShape = PixelShape.SQUARE,
    ) : PixelHostProfilePolicy {
        /** Rejects non-finite or non-positive dp sizes at the public boundary. */
        init {
            require(dotSizeDp.isFinite() && dotSizeDp > 0f) {
                "dotSizeDp must be finite and greater than zero"
            }
        }
    }

    /**
 * 定义 `AdaptiveLogicalSize` 在 `PixelHostProfilePolicy` 中承担的数据与行为边界。
 *
     * Keeps logical dimensions stable while recomputing the diagnostic physical dot size.
     *
     * Actual paint geometry still comes from [PixelViewportPolicy], so fractional scale remains
     * exact even though [ScreenProfile.dotSizePx] can store only a positive integer.
     *
     * @property logicalWidth Positive fixed logical column count.
     * @property logicalHeight Positive fixed logical row count.
     * @property pixelShape Shape painted inside each resolved logical pixel cell.
     */
    public data class AdaptiveLogicalSize(
        public val logicalWidth: Int,
        public val logicalHeight: Int,
        public val pixelShape: PixelShape = PixelShape.SQUARE,
    ) : PixelHostProfilePolicy {
        /** Rejects empty logical viewports before geometry or allocation begins. */
        init {
            require(logicalWidth > 0) { "logicalWidth must be greater than zero" }
            require(logicalHeight > 0) { "logicalHeight must be greater than zero" }
        }
    }
}

/** Pure resolver shared by the Android Host and deterministic JVM policy tests. */
internal object PixelHostProfileResolver {
    /** Resolves [policy] against one immutable viewport and environment snapshot. */
    fun resolve(
        /** Configured fixed or adaptive policy. */
        policy: PixelHostProfilePolicy,
        /** Current physical Host width in pixels. */
        widthPx: Int,
        /** Current physical Host height in pixels. */
        heightPx: Int,
        /** Current positive Android density ratio. */
        density: Float,
        /** Current physical-to-logical viewport strategy. */
        viewportPolicy: PixelViewportPolicy,
    ): ScreenProfile {
        return when (policy) {
            is PixelHostProfilePolicy.Fixed -> policy.profile
            is PixelHostProfilePolicy.AdaptivePixels -> ScreenProfileFactory.create(
                widthPx = widthPx,
                heightPx = heightPx,
                dotSizePx = policy.dotSizePx,
                pixelShape = policy.pixelShape,
            )
            is PixelHostProfilePolicy.AdaptiveDp -> {
                /** Validated density fallback prevents malformed OEM values from collapsing dots. */
                val safeDensity = density.takeIf { value -> value.isFinite() && value > 0f } ?: 1f
                /** Rounded physical dot size corresponding to the requested dp value. */
                val dotSizePx = (policy.dotSizeDp * safeDensity).roundToInt().coerceAtLeast(1)
                ScreenProfileFactory.create(
                    widthPx = widthPx,
                    heightPx = heightPx,
                    dotSizePx = dotSizePx,
                    pixelShape = policy.pixelShape,
                )
            }
            is PixelHostProfilePolicy.AdaptiveLogicalSize -> {
                /** Provisional profile used to resolve the exact shared viewport cell geometry. */
                val provisional = ScreenProfile(
                    logicalWidth = policy.logicalWidth,
                    logicalHeight = policy.logicalHeight,
                    dotSizePx = 1,
                    pixelShape = policy.pixelShape,
                )
                /** Exact paint cell size, including contain/cover and quantization behavior. */
                val cellSize = PixelGridGeometryResolver.resolve(
                    viewWidth = widthPx,
                    viewHeight = heightPx,
                    profile = provisional,
                    viewportPolicy = viewportPolicy,
                )?.cellSize ?: 1f
                provisional.copy(dotSizePx = cellSize.roundToInt().coerceAtLeast(1))
            }
        }
    }
}
