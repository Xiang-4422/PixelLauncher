package com.purride.pixelui

import com.purride.pixelui.animation.Curve
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.PixelTickerProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 定义 `PixelMotionRole` 在 `PixelMotionTheme` 中承担的数据与行为边界。
 *
 * Describes how reduce-motion policy should adapt a motion token.
 *
 * [Feedback] and [Selection] communicate a local state change, [Spatial] moves content through
 * space, and [Continuous] represents decorative or indefinite movement.
 */
public enum class PixelMotionRole {
    /** Short-lived response to direct pointer, keyboard, or accessibility input. */
    Feedback,

    /** Transition that communicates a selected, checked, or focused state. */
    Selection,

    /** Movement that explains where a surface or route came from and where it went. */
    Spatial,

    /** Decorative or indefinite motion that has no required completion moment. */
    Continuous,
}

/**
 * 定义 `PixelMotionTransitionPreset` 在 `PixelMotionTheme` 中承担的数据与行为边界。
 *
 * Names the visual family used by a component transition.
 *
 * A preset is descriptive metadata. Components remain responsible for mapping it to their
 * concrete opacity, scale, and translation values.
 */
public enum class PixelMotionTransitionPreset {
    /** No interpolated visual transition. */
    None,

    /** Opacity-only transition. */
    Fade,

    /** Scale-only transition. */
    Scale,

    /** Combined opacity and scale transition. */
    FadeScale,

    /** Horizontal translation transition. */
    SlideHorizontal,

    /** Vertical translation transition. */
    SlideVertical,
}

/**
 * 定义 `PixelSpringSpec` 在 `PixelMotionTheme` 中承担的数据与行为边界。
 *
 * Immutable spring parameters available to components that support physical settling.
 *
 * @property stiffness Restoring force coefficient; must be finite and greater than zero.
 * @property dampingRatio Dimensionless damping ratio; must be finite and non-negative.
 * @property mass Relative moving mass; must be finite and greater than zero.
 */
public data class PixelSpringSpec(
    public val stiffness: Float = 500f,
    public val dampingRatio: Float = 1f,
    public val mass: Float = 1f,
) {
    init {
        require(stiffness.isFinite() && stiffness > 0f) {
            "PixelSpringSpec.stiffness must be finite and > 0, got $stiffness"
        }
        require(dampingRatio.isFinite() && dampingRatio >= 0f) {
            "PixelSpringSpec.dampingRatio must be finite and >= 0, got $dampingRatio"
        }
        require(mass.isFinite() && mass > 0f) {
            "PixelSpringSpec.mass must be finite and > 0, got $mass"
        }
    }
}

/**
 * 定义 `PixelMotionSettings` 在 `PixelMotionTheme` 中承担的数据与行为边界。
 *
 * Host motion preferences applied after component and theme tokens are selected.
 *
 * @property animatorDurationScale Android animator duration scale. Zero requests immediate
 * completion. Positive infinity is accepted and resolves to an infinite duration without
 * numeric overflow; negative values and NaN are rejected.
 * @property reduceMotion Whether non-essential movement should use the role-specific reduced
 * motion policy.
 */
public data class PixelMotionSettings(
    public val animatorDurationScale: Float = 1f,
    public val reduceMotion: Boolean = false,
) {
    init {
        require(!animatorDurationScale.isNaN() && animatorDurationScale >= 0f) {
            "PixelMotionSettings.animatorDurationScale must be >= 0 and not NaN, got $animatorDurationScale"
        }
    }

    /** 集中提供 `PixelMotionTheme` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 公开 `PixelMotionTheme` 的 `Default` 配置或运行值。
 *
 * Default motion settings used when the platform has no explicit preference.
 */
        public val Default: PixelMotionSettings = PixelMotionSettings()
    }
}

/**
 * 定义 `PixelMotionSpec` 在 `PixelMotionTheme` 中承担的数据与行为边界。
 *
 * Theme-level motion token before host duration and accessibility settings are applied.
 *
 * @property duration Active interpolation duration; must not be negative.
 * @property curve Progress curve used while the transition is active.
 * @property delay Delay before interpolation begins; must not be negative.
 * @property transition Visual transition family interpreted by the consuming component.
 * @property spring Optional physical settling parameters for components that support springs.
 * @property role Accessibility role that determines the reduce-motion replacement policy.
 */
public data class PixelMotionSpec(
    public val duration: Duration,
    public val curve: Curve = Curves.EaseInOut,
    public val delay: Duration = Duration.ZERO,
    public val transition: PixelMotionTransitionPreset = PixelMotionTransitionPreset.Fade,
    public val spring: PixelSpringSpec? = null,
    public val role: PixelMotionRole = PixelMotionRole.Spatial,
) {
    init {
        require(!duration.isNegative()) { "PixelMotionSpec.duration must not be negative, got $duration" }
        require(!delay.isNegative()) { "PixelMotionSpec.delay must not be negative, got $delay" }
    }

    /**
 * 查询 `PixelMotionTheme` 的 `resolve` 结果，不产生额外状态变更。
 *
     * Applies [settings] without mutating this theme token.
     *
     * A zero duration scale is always immediate and removes delay. Reduce motion is adapted by
     * [role]: feedback and selection become immediate, spatial motion becomes a short fade, and
     * continuous motion becomes a static state. Duration multiplication saturates instead of
     * overflowing.
     */
    public fun resolve(settings: PixelMotionSettings): PixelResolvedMotion {
        val scale = settings.animatorDurationScale
        require(!scale.isNaN() && scale >= 0f) {
            "PixelMotionSettings.animatorDurationScale must be >= 0 and not NaN, got $scale"
        }
        if (scale == 0f) {
            return immediateResolvedMotion()
        }

        val scaledDuration = duration.scaleWithoutOverflow(scale)
        val scaledDelay = delay.scaleWithoutOverflow(scale)
        if (!settings.reduceMotion) {
            return PixelResolvedMotion(
                duration = scaledDuration,
                curve = curve,
                delay = scaledDelay,
                transition = transition,
                spring = spring,
                role = role,
                isImmediate = scaledDuration == Duration.ZERO && scaledDelay == Duration.ZERO,
            )
        }

        return when (role) {
            PixelMotionRole.Feedback,
            PixelMotionRole.Selection,
            PixelMotionRole.Continuous,
            -> immediateResolvedMotion()

            PixelMotionRole.Spatial -> {
                val reducedDuration = minOf(scaledDuration, ReducedSpatialDuration)
                PixelResolvedMotion(
                    duration = reducedDuration,
                    curve = Curves.Linear,
                    delay = Duration.ZERO,
                    transition = if (transition == PixelMotionTransitionPreset.None) {
                        PixelMotionTransitionPreset.None
                    } else {
                        PixelMotionTransitionPreset.Fade
                    },
                    spring = null,
                    role = role,
                    isImmediate = reducedDuration == Duration.ZERO,
                )
            }
        }
    }

    /** Builds the common synchronous terminal-state replacement. */
    private fun immediateResolvedMotion(): PixelResolvedMotion {
        return PixelResolvedMotion(
            duration = Duration.ZERO,
            curve = Curves.Linear,
            delay = Duration.ZERO,
            transition = PixelMotionTransitionPreset.None,
            spring = null,
            role = role,
            isImmediate = true,
        )
    }

    private companion object {
        /** Maximum retained spatial duration when reduce motion is enabled. */
        val ReducedSpatialDuration: Duration = 80.milliseconds
    }
}

/**
 * 定义 `PixelResolvedMotion` 在 `PixelMotionTheme` 中承担的数据与行为边界。
 *
 * Fully resolved component motion after host settings and accessibility policy are applied.
 *
 * @property duration Effective active interpolation duration.
 * @property curve Effective interpolation curve.
 * @property delay Effective pre-transition delay.
 * @property transition Effective visual transition family.
 * @property spring Effective spring parameters, or null when settling is disabled.
 * @property role Original accessibility role retained for diagnostics.
 * @property isImmediate Whether the consumer must apply the terminal state synchronously.
 */
public data class PixelResolvedMotion(
    public val duration: Duration,
    public val curve: Curve,
    public val delay: Duration,
    public val transition: PixelMotionTransitionPreset,
    public val spring: PixelSpringSpec?,
    public val role: PixelMotionRole,
    public val isImmediate: Boolean,
)

/**
 * 定义 `PixelMotionThemeData` 在 `PixelMotionTheme` 中承担的数据与行为边界。
 *
 * Motion tokens shared by standard Pixel UI components.
 *
 * @property feedback Direct manipulation feedback such as pressed and hover transitions.
 * @property selection Checked, selected, and focus state transitions.
 * @property slidableSettle Slidable release and snap-back settling.
 * @property dialogEnter Dialog entrance transition.
 * @property dialogExit Dialog exit transition.
 * @property popoverEnter Popover entrance transition.
 * @property popoverExit Popover exit transition.
 * @property route Default navigator route transition.
 */
public data class PixelMotionThemeData(
    public val feedback: PixelMotionSpec = PixelMotionSpec(
        duration = 90.milliseconds,
        curve = Curves.EaseOut,
        transition = PixelMotionTransitionPreset.Scale,
        role = PixelMotionRole.Feedback,
    ),
    public val selection: PixelMotionSpec = PixelMotionSpec(
        duration = 150.milliseconds,
        curve = Curves.EaseInOut,
        transition = PixelMotionTransitionPreset.Fade,
        role = PixelMotionRole.Selection,
    ),
    public val slidableSettle: PixelMotionSpec = PixelMotionSpec(
        duration = 220.milliseconds,
        curve = Curves.EaseOut,
        transition = PixelMotionTransitionPreset.SlideHorizontal,
        spring = PixelSpringSpec(stiffness = 480f, dampingRatio = 0.9f),
        role = PixelMotionRole.Spatial,
    ),
    public val dialogEnter: PixelMotionSpec = PixelMotionSpec(
        duration = 200.milliseconds,
        curve = Curves.EaseOut,
        transition = PixelMotionTransitionPreset.Fade,
        role = PixelMotionRole.Spatial,
    ),
    public val dialogExit: PixelMotionSpec = PixelMotionSpec(
        duration = 150.milliseconds,
        curve = Curves.EaseIn,
        transition = PixelMotionTransitionPreset.Fade,
        role = PixelMotionRole.Spatial,
    ),
    public val popoverEnter: PixelMotionSpec = PixelMotionSpec(
        duration = 160.milliseconds,
        curve = Curves.EaseOut,
        transition = PixelMotionTransitionPreset.Fade,
        role = PixelMotionRole.Spatial,
    ),
    public val popoverExit: PixelMotionSpec = PixelMotionSpec(
        duration = 120.milliseconds,
        curve = Curves.EaseIn,
        transition = PixelMotionTransitionPreset.Fade,
        role = PixelMotionRole.Spatial,
    ),
    public val route: PixelMotionSpec = PixelMotionSpec(
        duration = 240.milliseconds,
        curve = Curves.EaseInOut,
        transition = PixelMotionTransitionPreset.Fade,
        role = PixelMotionRole.Spatial,
    ),
) {
    /** 集中提供 `PixelMotionTheme` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 公开 `PixelMotionTheme` 的 `Default` 配置或运行值。
 *
 * Default motion tokens used when no [PixelMotionTheme] is inherited.
 */
        public val Default: PixelMotionThemeData = PixelMotionThemeData()
    }
}

/** 定义 `PixelMotionTheme` 在 `PixelMotionTheme` 中承担的数据与行为边界。
 *
 * Provides [PixelMotionThemeData] to a retained widget subtree.
 */
public class PixelMotionTheme(
    /** 公开 `PixelMotionTheme` 的 `data` 配置或运行值。
 *
 * Motion tokens inherited by descendant widgets.
 */
    public val data: PixelMotionThemeData,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    /** Notifies dependents only when the immutable token set changes. */
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return (oldWidget as? PixelMotionTheme)?.data != data
    }

    /** 集中提供 `PixelMotionTheme` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /**
 * 执行 `PixelMotionTheme` 的 `maybeOf` 公开行为；具体参数、返回和副作用见下文。
 *
         * Returns an explicit inherited motion provider first, then the nearest PixelTheme motion
         * tokens, or null when neither theme source is present.
         */
        public fun maybeOf(context: BuildContext): PixelMotionThemeData? {
            /** 显式 runtime motion provider 始终具有最高优先级。 */
            val explicitTheme = context.dependOnInheritedWidgetOfExactType<PixelMotionTheme>()?.data
            return explicitTheme ?: PixelWidgetArtifactResolver.motionTheme(context)
        }

        /** 执行 `PixelMotionTheme` 的 `of` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns explicit motion, PixelTheme motion, or [PixelMotionThemeData.Default].
 */
        public fun of(context: BuildContext): PixelMotionThemeData {
            return maybeOf(context) ?: PixelMotionThemeData.Default
        }
    }
}

/**
 * 在不让 runtime 反向依赖 widgets 的前提下读取兼容 `PixelTheme.tokens.motion`。
 *
 * 聚合或 pixel-widgets 消费者会提供固定 internal bridge；纯 runtime 消费者没有该类时返回 null，
 * 随后使用 [PixelMotionThemeData.Default]。
 */
private object PixelWidgetArtifactResolver {
    /** widgets sibling bridge 的冻结内部类名。 */
    private const val BridgeClassName: String = "com.purride.pixelui.internal.PixelWidgetArtifactAccess"

    /** 从可选 widgets artifact 解析当前 PixelTheme motion token。 */
    fun motionTheme(context: BuildContext): PixelMotionThemeData? {
        return runCatching {
            /** widgets artifact 的 Kotlin object class。 */
            val bridgeClass = Class.forName(BridgeClassName)
            /** Kotlin object 的唯一实例。 */
            val bridge = bridgeClass.getField("INSTANCE").get(null)
            /** 明确签名的 sibling bridge 方法。 */
            val method = bridgeClass.getMethod("motionTheme", BuildContext::class.java)
            method.invoke(bridge, context) as? PixelMotionThemeData
        }.getOrNull()
    }
}

/**
 * 定义 `PixelMotionScope` 在 `PixelMotionTheme` 中承担的数据与行为边界。
 *
 * Host-owned motion environment inherited by animated widgets.
 *
 * @property vsync Ticker provider bound to the current Host lifecycle.
 * @property settings Current system or explicitly overridden motion preferences.
 */
public class PixelMotionScope(
    public val vsync: PixelTickerProvider,
    public val settings: PixelMotionSettings,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    /** Rebuilds dependents when either the provider identity or settings value changes. */
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        val oldScope = oldWidget as? PixelMotionScope ?: return true
        return oldScope.vsync !== vsync || oldScope.settings != settings
    }

    /** 集中提供 `PixelMotionTheme` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 执行 `PixelMotionTheme` 的 `maybeOf` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest Host motion environment, or null outside a Host.
 */
        public fun maybeOf(context: BuildContext): PixelMotionScope? {
            return context.dependOnInheritedWidgetOfExactType<PixelMotionScope>()
        }

        /**
 * 执行 `PixelMotionTheme` 的 `of` 公开行为；具体参数、返回和副作用见下文。
 *
         * Returns the nearest Host motion environment.
         *
         * Components that intentionally support rendering without a Host should use [maybeOf]
         * and apply their terminal state synchronously when it returns null.
         */
        public fun of(context: BuildContext): PixelMotionScope {
            return checkNotNull(maybeOf(context)) {
                "PixelMotionScope.of() requires a PixelHostView or an explicit PixelMotionScope ancestor"
            }
        }
    }
}

/** Scales a non-negative duration while relying on Duration's saturating infinity semantics. */
private fun Duration.scaleWithoutOverflow(scale: Float): Duration {
    if (this == Duration.ZERO || scale == 0f) return Duration.ZERO
    if (isInfinite() || scale == Float.POSITIVE_INFINITY) return Duration.INFINITE
    return this * scale.toDouble()
}
