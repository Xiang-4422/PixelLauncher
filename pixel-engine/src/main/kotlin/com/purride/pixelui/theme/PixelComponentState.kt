package com.purride.pixelui

/**
 * 保存 `PixelComponentState` 的 `PixelControlState` 可观察或可恢复状态。
 *
 * Standard visual states understood by Pixel UI component tokens.
 *
 * [Normal] is the implicit state represented by [PixelControlStateSet.Empty]. It is never stored
 * alongside another state, which keeps combined-state equality deterministic.
 */
public enum class PixelControlState {
    /** The control is enabled and has no active interaction or semantic state. */
    Normal,

    /** A pointing device is currently hovering over the control. */
    Hovered,

    /** The control is being actively pressed. */
    Pressed,

    /** The control owns keyboard or accessibility focus. */
    Focused,

    /** The control represents a selected or checked value. */
    Selected,

    /** The control cannot currently be interacted with. */
    Disabled,

    /** The control is presenting an error state. */
    Error,

    /** The control is waiting for an asynchronous operation to finish. */
    Loading,
}

/**
 * 定义 `PixelControlStateSet` 在 `PixelComponentState` 中承担的数据与行为边界。
 *
 * Immutable, value-equal set of active [PixelControlState] values.
 *
 * An empty bit set means [PixelControlState.Normal]. Adding [PixelControlState.Normal] to a
 * non-normal set is a no-op, so Normal never coexists with another state.
 */
@JvmInline
public value class PixelControlStateSet private constructor(
    /** Bit mask containing only non-normal control states. */
    private val bits: Int,
) {
    /** 表示 `PixelComponentState` 当前是否满足 `isNormal` 对应条件。
 *
 * Whether this set represents the implicit Normal state.
 */
    public val isNormal: Boolean
        get() = bits == 0

    /** 判断 `PixelComponentState` 是否满足 `contains` 条件，不修改现有状态。
 *
 * Returns whether [state] is active in this immutable set.
 */
    public operator fun contains(state: PixelControlState): Boolean {
        return if (state == PixelControlState.Normal) {
            isNormal
        } else {
            bits and state.mask() != 0
        }
    }

    /** 执行 `PixelComponentState` 的 `plus` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns a new set containing [state].
 */
    public operator fun plus(state: PixelControlState): PixelControlStateSet {
        if (state == PixelControlState.Normal) return this
        return PixelControlStateSet(bits or state.mask())
    }

    /** 执行 `PixelComponentState` 的 `minus` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns a new set without [state].
 */
    public operator fun minus(state: PixelControlState): PixelControlStateSet {
        if (state == PixelControlState.Normal) return this
        return PixelControlStateSet(bits and state.mask().inv())
    }

    /**
 * 执行 `PixelComponentState` 的 `toSet` 公开行为；具体参数、返回和副作用见下文。
 *
     * Returns the active states as an immutable snapshot.
     *
     * The returned set contains only Normal when this value is [Empty].
     */
    public fun toSet(): Set<PixelControlState> {
        if (isNormal) return setOf(PixelControlState.Normal)
        return PixelControlState.entries.filterTo(linkedSetOf()) { state ->
            state != PixelControlState.Normal && state in this
        }
    }

    /**
 * 执行 `PixelComponentState` 的 `highestPriority` 公开行为；具体参数、返回和副作用见下文。
 *
     * Returns the highest-priority active state.
     *
     * Resolution order is Disabled, Loading, Error, Pressed, Focused, Hovered, Selected, Normal.
     */
    public fun highestPriority(): PixelControlState {
        return PixelControlStateResolutionPriority.firstOrNull { state -> state in this }
            ?: PixelControlState.Normal
    }

    /** 集中提供 `PixelComponentState` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 公开 `PixelComponentState` 的 `Empty` 配置或运行值。
 *
 * Immutable Normal state with no stored state bits.
 */
        public val Empty: PixelControlStateSet = PixelControlStateSet(bits = 0)

        /** 公开 `PixelComponentState` 的 `Normal` 配置或运行值。
 *
 * Alias that makes the implicit Normal representation explicit at call sites.
 */
        public val Normal: PixelControlStateSet = Empty

        /**
 * 执行 `PixelComponentState` 的 `of` 公开行为；具体参数、返回和副作用见下文。
 *
         * Creates an immutable state set from [states].
         *
         * Normal is ignored when any non-normal state is supplied.
         */
        public fun of(vararg states: PixelControlState): PixelControlStateSet {
            /** Accumulated non-normal state bits. */
            var resultBits = 0
            states.forEach { state ->
                if (state != PixelControlState.Normal) resultBits = resultBits or state.mask()
            }
            return PixelControlStateSet(resultBits)
        }
    }
}

/** 定义 `PixelStateProperty` 在 `PixelComponentState` 中的可替换调用契约。
 *
 * Resolves an immutable value for a combined component state.
 */
public fun interface PixelStateProperty<T> {
    /** 查询 `PixelComponentState` 的 `resolve` 结果，不产生额外状态变更。
 *
 * Resolves the value applicable to [states].
 */
    public fun resolve(states: PixelControlStateSet): T

    /** 集中提供 `PixelComponentState` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 执行 `PixelComponentState` 的 `constant` 公开行为；具体参数、返回和副作用见下文。
 *
 * Creates a property that returns [value] for every component state.
 */
        public fun <T> constant(value: T): PixelStateProperty<T> = PixelStateMap(normal = value)
    }
}

/**
 * 定义 `PixelStateMap` 在 `PixelComponentState` 中承担的数据与行为边界。
 *
 * Immutable state property backed by Normal plus explicit state overrides.
 *
 * Overrides are resolved in this fixed order: Disabled, Loading, Error, Pressed, Focused,
 * Hovered, Selected, Normal. Duplicate overrides and a Normal override are rejected because
 * [normal] is the single canonical fallback.
 *
 * @property normal Value returned when no active state has an override.
 * @param overrides Explicit non-normal state overrides.
 */
public class PixelStateMap<T>(
    public val normal: T,
    vararg overrides: Pair<PixelControlState, T>,
) : PixelStateProperty<T> {
    /** Defensive immutable copy of validated state overrides. */
    private val overrideValues: Map<PixelControlState, T> = buildMap {
        overrides.forEach { (state, value) ->
            require(state != PixelControlState.Normal) {
                "PixelStateMap.Normal must be supplied through the normal parameter"
            }
            require(!containsKey(state)) { "PixelStateMap contains duplicate override for $state" }
            put(state, value)
        }
    }

    /** 公开 `PixelComponentState` 当前的 `overriddenStates` 状态维度。
 *
 * Immutable snapshot of states that have explicit overrides.
 */
    public val overriddenStates: Set<PixelControlState>
        get() = overrideValues.keys.toSet()

    /** Resolves [states] using the documented global state priority. */
    override fun resolve(states: PixelControlStateSet): T {
        PixelControlStateResolutionPriority.forEach { state ->
            if (state in states && overrideValues.containsKey(state)) {
                return overrideValues.getValue(state)
            }
        }
        return normal
    }

    /** Uses the Normal value and override map for stable value equality. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PixelStateMap<*>) return false
        return normal == other.normal && overrideValues == other.overrideValues
    }

    /** Uses the same immutable values as [equals] for a stable hash code. */
    override fun hashCode(): Int = 31 * (normal?.hashCode() ?: 0) + overrideValues.hashCode()

    /** Returns a diagnostic representation without exposing a mutable map. */
    override fun toString(): String = "PixelStateMap(normal=$normal, overrides=$overrideValues)"
}

/** Global state resolution order shared by every [PixelStateMap]. */
private val PixelControlStateResolutionPriority: List<PixelControlState> = listOf(
    PixelControlState.Disabled,
    PixelControlState.Loading,
    PixelControlState.Error,
    PixelControlState.Pressed,
    PixelControlState.Focused,
    PixelControlState.Hovered,
    PixelControlState.Selected,
)

/** Returns the unique bit assigned to this non-normal state. */
private fun PixelControlState.mask(): Int = 1 shl ordinal
