package com.purride.pixelui

import java.util.Collections
import java.util.IllformedLocaleException
import java.util.Locale

/**
 * 定义 `PixelLocale` 在 `HostCapabilities` 中承担的数据与行为边界。
 *
 * Platform-neutral locale identifier backed by a canonical BCP-47 language tag.
 *
 * The constructor rejects blank or ill-formed tags instead of silently truncating them. Canonical
 * casing and separator rules come from the runtime BCP-47 parser, so values such as `ZH-hans-cn`
 * are exposed as `zh-Hans-CN` and compare by that canonical value.
 *
 * @param languageTag BCP-47 tag to validate and canonicalize.
 */
public class PixelLocale(
    languageTag: String,
) {
    /** 公开 `HostCapabilities` 的 `languageTag` 配置或运行值。
 *
 * Canonical BCP-47 tag used for value equality and host interop.
 */
    public val languageTag: String = canonicalizeLanguageTag(languageTag)

    /** Returns true when [other] identifies the same canonical language tag. */
    override fun equals(other: Any?): Boolean {
        return this === other || (other is PixelLocale && languageTag == other.languageTag)
    }

    /** Returns a hash derived from the canonical language tag. */
    override fun hashCode(): Int = languageTag.hashCode()

    /** Returns the canonical tag for diagnostics and deterministic snapshots. */
    override fun toString(): String = languageTag

    /** 集中提供 `HostCapabilities` 的 `<companion>` 共享入口。
 *
 * Provides canonical built-in locale fallbacks.
 */
    public companion object {
        /** 公开 `HostCapabilities` 的 `English` 配置或运行值。
 *
 * Built-in English fallback used when no host locale is available.
 */
        public val English: PixelLocale = PixelLocale("en")

        /** 公开 `HostCapabilities` 的 `Default` 配置或运行值。
 *
 * Stable default locale; currently the same immutable value as [English].
 */
        public val Default: PixelLocale = English

        /**
         * Validates [languageTag] strictly and returns its runtime-canonical BCP-47 form.
         *
         * @throws IllegalArgumentException when the tag is blank or not well formed.
         */
        private fun canonicalizeLanguageTag(languageTag: String): String {
            require(languageTag.isNotBlank()) { "PixelLocale.languageTag must not be blank" }
            return try {
                Locale.Builder()
                    .setLanguageTag(languageTag)
                    .build()
                    .toLanguageTag()
            } catch (error: IllformedLocaleException) {
                throw IllegalArgumentException(
                    "PixelLocale.languageTag must be a well-formed BCP-47 tag, got '$languageTag'",
                    error,
                )
            }
        }
    }
}

/**
 * 定义 `PixelLogicalRect` 在 `HostCapabilities` 中承担的数据与行为边界。
 *
 * Immutable rectangle in the engine host's logical coordinate space.
 *
 * Coordinates may be negative for host-provided features outside the current viewport, but every
 * edge and derived span must be finite and the trailing edges must not precede their leading
 * edges. Zero-width or zero-height rectangles are valid because a fold may be represented as a
 * logical line.
 *
 * @property left Logical leading x coordinate.
 * @property top Logical leading y coordinate.
 * @property right Logical trailing x coordinate, greater than or equal to [left].
 * @property bottom Logical trailing y coordinate, greater than or equal to [top].
 * @throws IllegalArgumentException when an edge or derived span is non-finite, or edges reverse.
 */
public data class PixelLogicalRect(
    public val left: Float,
    public val top: Float,
    public val right: Float,
    public val bottom: Float,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
            "PixelLogicalRect edges must be finite, got ($left, $top, $right, $bottom)"
        }
        require(right >= left) {
            "PixelLogicalRect.right must be >= left, got left=$left, right=$right"
        }
        require(bottom >= top) {
            "PixelLogicalRect.bottom must be >= top, got top=$top, bottom=$bottom"
        }
        require((right - left).isFinite()) {
            "PixelLogicalRect width must be finite, got left=$left, right=$right"
        }
        require((bottom - top).isFinite()) {
            "PixelLogicalRect height must be finite, got top=$top, bottom=$bottom"
        }
    }

    /** 定义 `HostCapabilities` 的 `width` 逻辑像素度量。
 *
 * Logical width derived without integer rounding.
 */
    public val width: Float
        get() = right - left

    /** 定义 `HostCapabilities` 的 `height` 逻辑像素度量。
 *
 * Logical height derived without integer rounding.
 */
    public val height: Float
        get() = bottom - top
}

/** 定义 `PixelDisplayFeatureType` 在 `HostCapabilities` 中承担的数据与行为边界。
 *
 * Platform-neutral category of a feature occupying or separating part of a display.
 */
public enum class PixelDisplayFeatureType {
    /** Flexible crease whose bounds may have zero thickness. */
    FOLD,

    /** Physical separator that can occlude content. */
    HINGE,

    /** Non-rectangular or reserved display cutout represented by its logical bounds. */
    CUTOUT,

    /** Forward-compatible fallback when a host reports an unsupported feature category. */
    UNKNOWN,
}

/** 保存 `HostCapabilities` 的 `PixelDisplayFeatureState` 可观察或可恢复状态。
 *
 * Platform-neutral posture state associated with a logical display feature.
 */
public enum class PixelDisplayFeatureState {
    /** Feature is flat relative to the current display surface. */
    FLAT,

    /** Feature separates display regions at a non-flat angle. */
    HALF_OPENED,

    /** State is absent, not applicable, or unsupported by the current host. */
    UNKNOWN,
}

/**
 * 定义 `PixelDisplayFeature` 在 `HostCapabilities` 中承担的数据与行为边界。
 *
 * Immutable platform-neutral display feature supplied by a host capability source.
 *
 * @property bounds Feature bounds in the same logical coordinate space as the host viewport.
 * @property type Feature category, or [PixelDisplayFeatureType.UNKNOWN] when unsupported.
 * @property state Feature posture, or [PixelDisplayFeatureState.UNKNOWN] when absent.
 */
public data class PixelDisplayFeature(
    public val bounds: PixelLogicalRect,
    public val type: PixelDisplayFeatureType = PixelDisplayFeatureType.UNKNOWN,
    public val state: PixelDisplayFeatureState = PixelDisplayFeatureState.UNKNOWN,
)

/**
 * 定义 `PixelWindowSizeClass` 在 `HostCapabilities` 中承担的数据与行为边界。
 *
 * Adaptive size class resolved independently for a window's width or height in density-independent
 * pixels. Width uses 600dp and 840dp boundaries; height uses 480dp and 900dp boundaries. These
 * platform-neutral thresholds intentionally require the caller to convert physical pixels to dp.
 */
public enum class PixelWindowSizeClass {
    /** Space below the first axis-specific breakpoint. */
    COMPACT,

    /** Space from the first breakpoint up to, but excluding, the expanded breakpoint. */
    MEDIUM,

    /** Space at or above the expanded breakpoint. */
    EXPANDED,
    ;

    /** 集中提供 `HostCapabilities` 的 `<companion>` 共享入口。
 *
 * Resolves axis-specific adaptive classes without any platform window dependency.
 */
    public companion object {
        /**
 * 执行 `HostCapabilities` 的 `forWidthDp` 公开行为；具体参数、返回和副作用见下文。
 *
         * Resolves a width in dp using compact `<600`, medium `<840`, otherwise expanded.
         *
         * @throws IllegalArgumentException when [widthDp] is negative or not finite.
         */
        public fun forWidthDp(widthDp: Float): PixelWindowSizeClass {
            requireValidDp(axis = "width", value = widthDp)
            return when {
                widthDp < 600f -> COMPACT
                widthDp < 840f -> MEDIUM
                else -> EXPANDED
            }
        }

        /**
 * 执行 `HostCapabilities` 的 `forHeightDp` 公开行为；具体参数、返回和副作用见下文。
 *
         * Resolves a height in dp using compact `<480`, medium `<900`, otherwise expanded.
         *
         * @throws IllegalArgumentException when [heightDp] is negative or not finite.
         */
        public fun forHeightDp(heightDp: Float): PixelWindowSizeClass {
            requireValidDp(axis = "height", value = heightDp)
            return when {
                heightDp < 480f -> COMPACT
                heightDp < 900f -> MEDIUM
                else -> EXPANDED
            }
        }

        /** Rejects negative, infinite, or NaN axis values before threshold resolution. */
        private fun requireValidDp(axis: String, value: Float) {
            require(value.isFinite() && value >= 0f) {
                "PixelWindowSizeClass $axis must be finite and >= 0dp, got $value"
            }
        }
    }
}

/**
 * 定义 `HostCapabilitiesData` 在 `HostCapabilities` 中承担的数据与行为边界。
 *
 * Immutable snapshot of host capabilities consumed by the retained widget tree.
 *
 * List inputs are defensively copied and exposed as unmodifiable snapshots. Locale preferences
 * must contain at least one entry and cannot repeat a canonical tag, so [locales] always has a
 * deterministic primary locale. Scale and density must be finite and positive. [refreshRateHz]
 * uses `null` as its only unknown value; a present value must be finite and positive. Unknown
 * display feature metadata is represented by the explicit `UNKNOWN` enum values rather than
 * nullable platform objects.
 *
 * This is deliberately a regular immutable class instead of a Kotlin data class: the manual
 * [copy] implementation preserves defensive list ownership without exposing mutable constructor
 * inputs through generated component methods.
 *
 * @param locales Ordered locale preferences, with the first entry being the active locale.
 * @property layoutDirection Logical direction derived by the host from its active configuration.
 * @property textScaleFactor Positive multiplier applied to text layout and rasterization.
 * @property highContrast Whether the host requests a high-contrast presentation.
 * @property motionSettings Host motion and reduced-motion preferences.
 * @property density Positive physical-pixels-per-dp ratio reported by the host.
 * @property refreshRateHz Optional positive display refresh rate; `null` means unknown.
 * @param displayFeatures Logical folds, hinges, cutouts, or forward-compatible unknown features.
 * @throws IllegalArgumentException when a list or numeric value violates the documented contract.
 */
public class HostCapabilitiesData(
    locales: List<PixelLocale> = listOf(PixelLocale.Default),
    public val layoutDirection: TextDirection = TextDirection.LTR,
    public val textScaleFactor: Float = 1f,
    public val highContrast: Boolean = false,
    public val motionSettings: PixelMotionSettings = PixelMotionSettings.Default,
    public val density: Float = 1f,
    public val refreshRateHz: Float? = null,
    displayFeatures: List<PixelDisplayFeature> = emptyList(),
) {
    /** Owned, unmodifiable locale preference snapshot. */
    private val localeSnapshot: List<PixelLocale> = immutableListSnapshot(locales)

    /** Owned, unmodifiable logical display-feature snapshot. */
    private val displayFeatureSnapshot: List<PixelDisplayFeature> =
        immutableListSnapshot(displayFeatures)

    init {
        require(localeSnapshot.isNotEmpty()) {
            "HostCapabilitiesData.locales must contain at least one locale"
        }
        require(localeSnapshot.distinct().size == localeSnapshot.size) {
            "HostCapabilitiesData.locales must not contain duplicate locale tags"
        }
        require(textScaleFactor.isFinite() && textScaleFactor > 0f) {
            "HostCapabilitiesData.textScaleFactor must be finite and > 0, got $textScaleFactor"
        }
        require(density.isFinite() && density > 0f) {
            "HostCapabilitiesData.density must be finite and > 0, got $density"
        }
        require(refreshRateHz == null || (refreshRateHz.isFinite() && refreshRateHz > 0f)) {
            "HostCapabilitiesData.refreshRateHz must be null or finite and > 0, got $refreshRateHz"
        }
    }

    /** 公开 `HostCapabilities` 的 `locales` 配置或运行值。
 *
 * Ordered immutable locale preferences; index zero is always the active locale.
 */
    public val locales: List<PixelLocale>
        get() = localeSnapshot

    /** 公开 `HostCapabilities` 的 `displayFeatures` 配置或运行值。
 *
 * Immutable logical display features in host-provided order.
 */
    public val displayFeatures: List<PixelDisplayFeature>
        get() = displayFeatureSnapshot

    /**
 * 执行 `HostCapabilities` 的 `copy` 公开行为；具体参数、返回和副作用见下文。
 *
     * Creates an independently owned immutable snapshot with selected values replaced.
     *
     * Both list arguments are copied again, including when their defaults refer to this snapshot.
     *
     * @param locales Ordered locale preferences for the copied snapshot.
     * @param layoutDirection Logical direction for the copied snapshot.
     * @param textScaleFactor Positive text multiplier for the copied snapshot.
     * @param highContrast Whether the copied snapshot requests enhanced contrast.
     * @param motionSettings Motion preferences for the copied snapshot.
     * @param density Positive physical-pixels-per-dp ratio for the copied snapshot.
     * @param refreshRateHz Optional positive refresh rate for the copied snapshot.
     * @param displayFeatures Logical display features for the copied snapshot.
     * @return A new immutable snapshot with defensive list ownership.
     * @throws IllegalArgumentException when any replacement violates the constructor contract.
     */
    public fun copy(
        locales: List<PixelLocale> = this.locales,
        layoutDirection: TextDirection = this.layoutDirection,
        textScaleFactor: Float = this.textScaleFactor,
        highContrast: Boolean = this.highContrast,
        motionSettings: PixelMotionSettings = this.motionSettings,
        density: Float = this.density,
        refreshRateHz: Float? = this.refreshRateHz,
        displayFeatures: List<PixelDisplayFeature> = this.displayFeatures,
    ): HostCapabilitiesData {
        return HostCapabilitiesData(
            locales = locales,
            layoutDirection = layoutDirection,
            textScaleFactor = textScaleFactor,
            highContrast = highContrast,
            motionSettings = motionSettings,
            density = density,
            refreshRateHz = refreshRateHz,
            displayFeatures = displayFeatures,
        )
    }

    /** Compares every scalar and immutable list value in this capability snapshot. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HostCapabilitiesData) return false
        return locales == other.locales &&
            layoutDirection == other.layoutDirection &&
            textScaleFactor == other.textScaleFactor &&
            highContrast == other.highContrast &&
            motionSettings == other.motionSettings &&
            density == other.density &&
            refreshRateHz == other.refreshRateHz &&
            displayFeatures == other.displayFeatures
    }

    /** Returns a hash covering every value observed by [equals]. */
    override fun hashCode(): Int {
        /** Rolling hash accumulator kept in the same field order as [equals]. */
        var result = locales.hashCode()
        result = 31 * result + layoutDirection.hashCode()
        result = 31 * result + textScaleFactor.hashCode()
        result = 31 * result + highContrast.hashCode()
        result = 31 * result + motionSettings.hashCode()
        result = 31 * result + density.hashCode()
        result = 31 * result + (refreshRateHz?.hashCode() ?: 0)
        result = 31 * result + displayFeatures.hashCode()
        return result
    }

    /** Returns every capability value in a deterministic diagnostic representation. */
    override fun toString(): String {
        return "HostCapabilitiesData(" +
            "locales=$locales, " +
            "layoutDirection=$layoutDirection, " +
            "textScaleFactor=$textScaleFactor, " +
            "highContrast=$highContrast, " +
            "motionSettings=$motionSettings, " +
            "density=$density, " +
            "refreshRateHz=$refreshRateHz, " +
            "displayFeatures=$displayFeatures)"
    }

    /** 集中提供 `HostCapabilities` 的 `<companion>` 共享入口。
 *
 * Provides the immutable fallback used outside an explicitly configured host.
 */
    public companion object {
        /** 公开 `HostCapabilities` 的 `Default` 配置或运行值。
 *
 * Stable scope-less fallback with English, LTR, 1x scale/density, and unknown refresh.
 */
        public val Default: HostCapabilitiesData = HostCapabilitiesData()
    }
}

/**
 * 定义 `HostCapabilities` 在 `HostCapabilities` 中承担的数据与行为边界。
 *
 * Provides a [HostCapabilitiesData] snapshot to a retained widget subtree.
 *
 * Equal snapshots do not notify inherited dependents, while any changed capability value does.
 * Components that intentionally support a scope-less legacy path can call [of] and receive
 * [HostCapabilitiesData.Default]; [maybeOf] distinguishes that fallback from explicit injection.
 *
 * @property data Immutable host capability snapshot inherited by descendants.
 * @property child Descendant widget subtree receiving the snapshot.
 * @property key Optional stable retained-tree identity.
 */
public class HostCapabilities(
    public val data: HostCapabilitiesData,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    /** Notifies dependents only when the complete immutable snapshot changes by value. */
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return data != (oldWidget as? HostCapabilities)?.data
    }

    /** 集中提供 `HostCapabilities` 的 `<companion>` 共享入口。
 *
 * Reads the nearest inherited capability snapshot with optional legacy fallback behavior.
 */
    public companion object {
        /** 执行 `HostCapabilities` 的 `maybeOf` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest explicitly inherited capability snapshot, or null when absent.
 */
        public fun maybeOf(context: BuildContext): HostCapabilitiesData? {
            return context.dependOnInheritedWidgetOfExactType<HostCapabilities>()?.data
        }

        /** 执行 `HostCapabilities` 的 `of` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest capability snapshot or the documented scope-less default.
 */
        public fun of(context: BuildContext): HostCapabilitiesData {
            return maybeOf(context) ?: HostCapabilitiesData.Default
        }
    }
}

/** Copies [values] and wraps the owned storage so Java or Kotlin casts cannot mutate it. */
private fun <T> immutableListSnapshot(values: List<T>): List<T> {
    return Collections.unmodifiableList(ArrayList(values))
}
