package com.purride.pixelui

import java.util.Collections

/** 定义 `PixelWindowOrientation` 在 `AdaptiveBuilder` 中承担的数据与行为边界。
 *
 * Physical window orientation derived without depending on an Android Configuration object.
 */
public enum class PixelWindowOrientation {
    /** Physical height is greater than physical width. */
    PORTRAIT,

    /** Physical width is greater than physical height. */
    LANDSCAPE,

    /** Physical width and height are equal, including an empty test viewport. */
    SQUARE,
}

/**
 * 定义 `PixelAdaptiveLayoutData` 在 `AdaptiveBuilder` 中承担的数据与行为边界。
 *
 * Immutable adaptive layout snapshot combining physical dp classes and logical engine geometry.
 *
 * Physical pixels and [density] determine dp size classes. Logical dimensions and insets match
 * [MediaQuery], while [displayFeatures] matches the same atomic [HostCapabilitiesData] frame.
 * The feature list is defensively copied so a caller cannot mutate an inherited snapshot.
 *
 * @property physicalWidthPx Current Host width in physical pixels.
 * @property physicalHeightPx Current Host height in physical pixels.
 * @property logicalWidth Current engine logical column count.
 * @property logicalHeight Current engine logical row count.
 * @property density Positive physical-pixels-per-dp ratio.
 * @property viewInsets Transient logical obstruction edges such as IME.
 * @property viewPadding Stable logical safe edges such as bars and cutouts.
 * @property padding Stable safe edges after transient overlap is excluded.
 * @param displayFeatures Logical folds, hinges and cutouts in Host-provided order.
 */
public class PixelAdaptiveLayoutData(
    public val physicalWidthPx: Int,
    public val physicalHeightPx: Int,
    public val logicalWidth: Int,
    public val logicalHeight: Int,
    public val density: Float,
    public val viewInsets: PixelWindowInsets = PixelWindowInsets.Zero,
    public val viewPadding: PixelWindowInsets = PixelWindowInsets.Zero,
    public val padding: PixelWindowInsets = viewPadding,
    displayFeatures: List<PixelDisplayFeature> = emptyList(),
) {
    /** Owned immutable display-feature snapshot. */
    private val displayFeatureSnapshot: List<PixelDisplayFeature> =
        Collections.unmodifiableList(displayFeatures.toList())

    /** 定义 `AdaptiveBuilder` 的 `widthDp` 逻辑像素度量。
 *
 * Current physical width converted to density-independent pixels.
 */
    public val widthDp: Float = physicalWidthPx / density

    /** 定义 `AdaptiveBuilder` 的 `heightDp` 逻辑像素度量。
 *
 * Current physical height converted to density-independent pixels.
 */
    public val heightDp: Float = physicalHeightPx / density

    /** 定义 `AdaptiveBuilder` 的 `widthSizeClass` 逻辑像素度量。
 *
 * Material-compatible compact, medium or expanded width classification.
 */
    public val widthSizeClass: PixelWindowSizeClass = PixelWindowSizeClass.forWidthDp(widthDp)

    /** 定义 `AdaptiveBuilder` 的 `heightSizeClass` 逻辑像素度量。
 *
 * Material-compatible compact, medium or expanded height classification.
 */
    public val heightSizeClass: PixelWindowSizeClass = PixelWindowSizeClass.forHeightDp(heightDp)

    /** 公开 `AdaptiveBuilder` 的 `orientation` 配置或运行值。
 *
 * Physical orientation independent from logical profile aspect ratio.
 */
    public val orientation: PixelWindowOrientation = when {
        physicalWidthPx > physicalHeightPx -> PixelWindowOrientation.LANDSCAPE
        physicalWidthPx < physicalHeightPx -> PixelWindowOrientation.PORTRAIT
        else -> PixelWindowOrientation.SQUARE
    }

    /** 公开 `AdaptiveBuilder` 的 `displayFeatures` 配置或运行值。
 *
 * Immutable folds, hinges and cutouts in Host-provided order.
 */
    public val displayFeatures: List<PixelDisplayFeature>
        get() = displayFeatureSnapshot

    /** Rejects invalid geometry before it reaches adaptive builders or size-class thresholds. */
    init {
        require(physicalWidthPx >= 0) { "physicalWidthPx must be >= 0" }
        require(physicalHeightPx >= 0) { "physicalHeightPx must be >= 0" }
        require(logicalWidth > 0) { "logicalWidth must be greater than zero" }
        require(logicalHeight > 0) { "logicalHeight must be greater than zero" }
        require(density.isFinite() && density > 0f) {
            "density must be finite and greater than zero"
        }
    }

    /** Compares every physical, logical, inset and display-feature value. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PixelAdaptiveLayoutData) return false
        return physicalWidthPx == other.physicalWidthPx &&
            physicalHeightPx == other.physicalHeightPx &&
            logicalWidth == other.logicalWidth &&
            logicalHeight == other.logicalHeight &&
            density == other.density &&
            viewInsets == other.viewInsets &&
            viewPadding == other.viewPadding &&
            padding == other.padding &&
            displayFeatures == other.displayFeatures
    }

    /** Returns a hash covering the same immutable values used by [equals]. */
    override fun hashCode(): Int {
        /** Rolling hash accumulator in constructor field order. */
        var result = physicalWidthPx
        result = 31 * result + physicalHeightPx
        result = 31 * result + logicalWidth
        result = 31 * result + logicalHeight
        result = 31 * result + density.hashCode()
        result = 31 * result + viewInsets.hashCode()
        result = 31 * result + viewPadding.hashCode()
        result = 31 * result + padding.hashCode()
        result = 31 * result + displayFeatures.hashCode()
        return result
    }

    /** Returns a deterministic diagnostic summary of the complete adaptive snapshot. */
    override fun toString(): String {
        return "PixelAdaptiveLayoutData(" +
            "physicalWidthPx=$physicalWidthPx, " +
            "physicalHeightPx=$physicalHeightPx, " +
            "logicalWidth=$logicalWidth, " +
            "logicalHeight=$logicalHeight, " +
            "density=$density, " +
            "viewInsets=$viewInsets, " +
            "viewPadding=$viewPadding, " +
            "padding=$padding, " +
            "displayFeatures=$displayFeatures)"
    }
}

/** 定义 `PixelAdaptiveEnvironment` 在 `AdaptiveBuilder` 中承担的数据与行为边界。
 *
 * Inherited boundary exposing one atomic [PixelAdaptiveLayoutData] snapshot.
 */
public class PixelAdaptiveEnvironment(
    /** 公开 `AdaptiveBuilder` 的 `data` 配置或运行值。
 *
 * Immutable adaptive snapshot supplied to descendants.
 */
    public val data: PixelAdaptiveLayoutData,
    /** Descendant subtree receiving adaptive metrics. */
    override val child: Widget,
    /** Optional retained identity for the inherited boundary. */
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    /** Notifies dependents only when an adaptive input value changes. */
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return data != (oldWidget as? PixelAdaptiveEnvironment)?.data
    }

    /** 集中提供 `AdaptiveBuilder` 的 `<companion>` 共享入口。
 *
 * Adaptive environment lookup helpers.
 */
    public companion object {
        /** 执行 `AdaptiveBuilder` 的 `maybeOf` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest adaptive snapshot, or null outside a Host/environment boundary.
 */
        public fun maybeOf(context: BuildContext): PixelAdaptiveLayoutData? {
            return context.dependOnInheritedWidgetOfExactType<PixelAdaptiveEnvironment>()?.data
        }

        /** 执行 `AdaptiveBuilder` 的 `of` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest adaptive snapshot or fails with an actionable Host message.
 */
        public fun of(context: BuildContext): PixelAdaptiveLayoutData {
            return maybeOf(context)
                ?: error("AdaptiveBuilder requires PixelHostView or PixelAdaptiveEnvironment")
        }
    }
}

/** 定义 `AdaptiveBuilder` 在 `AdaptiveBuilder` 中承担的数据与行为边界。
 *
 * Rebuilds [builder] when physical size, logical size, insets, density or features change.
 */
public class AdaptiveBuilder(
    /** 公开 `AdaptiveBuilder` 的 `builder` 配置或运行值。
 *
 * Builder receiving the current context and one atomic adaptive layout snapshot.
 */
    public val builder: (BuildContext, PixelAdaptiveLayoutData) -> Widget,
    /** Optional retained identity for the builder element. */
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    /** Subscribes to [PixelAdaptiveEnvironment] and builds the current adaptive subtree. */
    override fun build(context: BuildContext): Widget {
        return builder(context, PixelAdaptiveEnvironment.of(context))
    }
}
