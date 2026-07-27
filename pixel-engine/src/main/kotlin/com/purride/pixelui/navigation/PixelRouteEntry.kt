package com.purride.pixelui

import com.purride.pixelui.state.PixelListSavedState
import java.util.IdentityHashMap

/**
 * 定义 `PixelRouteEntryId` 在 `PixelRouteEntry` 中承担的数据与行为边界。
 *
 * Stable identity of one concrete route-stack entry.
 *
 * A destination receives a fresh entry ID every time it is pushed, including when the same
 * destination object is pushed more than once. IDs are positive and unique within the owning
 * navigator.
 *
 * @property value Positive numeric identity allocated by the owning navigator.
 */
public data class PixelRouteEntryId(public val value: Long) {
    init {
        require(value > 0L) { "PixelRouteEntryId must be greater than zero" }
    }
}

/** 保存 `PixelRouteEntry` 的 `PixelRouteLifecycleState` 可观察或可恢复状态。
 *
 * Lifecycle state of one [PixelRouteEntry].
 */
public enum class PixelRouteLifecycleState {
    /** The entry has been created but has not become the foreground entry yet. */
    Created,

    /** The entry is currently the foreground entry. */
    Active,

    /** The entry remains in the stack but is not currently in the foreground. */
    Inactive,

    /** The entry has left the stack and is waiting for transition settlement and disposal. */
    Removing,

    /** The entry and its route-local state have been disposed permanently. */
    Disposed,
}

/** 保存 `PixelRouteEntry` 的 `PixelRouteResultState` 可观察或可恢复状态。
 *
 * Resolution state of one typed [PixelRouteResultChannel].
 */
public enum class PixelRouteResultState {
    /** No result or cancellation has been resolved yet. */
    Pending,

    /** A successful typed result has been resolved. */
    Succeeded,

    /** The channel has been resolved by cancellation. */
    Cancelled,
}

/** 定义 `PixelRouteCancellationReason` 在 `PixelRouteEntry` 中承担的数据与行为边界。
 *
 * Reason why a route result completed without a value.
 */
public enum class PixelRouteCancellationReason {
    /** The route or its caller explicitly requested cancellation. */
    Explicit,

    /** A discrete or predictive system-back action removed the entry. */
    Back,

    /** The entry was removed from the stack directly. */
    Removed,

    /** A clear operation removed the entry. */
    Cleared,

    /** A replace operation removed the entry. */
    Replaced,

    /** The owning navigator was disposed before a result was supplied. */
    NavigatorDisposed,

    /** A restore, deep-link, or other stack reset removed the entry. */
    StackReset,
}

/**
 * 定义 `PixelRouteOutcome` 在 `PixelRouteEntry` 中的可替换调用契约。
 *
 * Terminal outcome delivered by a typed route result channel.
 *
 * @param R Successful result value type.
 */
public sealed interface PixelRouteOutcome<out R> {
    /**
 * 定义 `Success` 在 `PixelRouteEntry` 中承担的数据与行为边界。
 *
     * Successful route result.
     *
     * @property value Typed value supplied by the completed entry.
     */
    public data class Success<out R>(public val value: R) : PixelRouteOutcome<R>

    /**
 * 定义 `Cancelled` 在 `PixelRouteEntry` 中承担的数据与行为边界。
 *
     * Route completion without a value.
     *
     * @property reason Operation that cancelled the pending result.
     */
    public data class Cancelled(
        public val reason: PixelRouteCancellationReason,
    ) : PixelRouteOutcome<Nothing>
}

/** 为 `PixelRouteOutcome.Success` 提供可直接构造与匹配的顶层短名。 */
public typealias PixelRouteSuccess<R> = PixelRouteOutcome.Success<R>

/** 为 `PixelRouteOutcome.Cancelled` 提供可直接构造与匹配的顶层短名。 */
public typealias PixelRouteCancelled = PixelRouteOutcome.Cancelled

/**
 * 定义 `PixelRouteStateKey` 在 `PixelRouteEntry` 中承担的数据与行为边界。
 *
 * Identity-based typed key for data stored in a [PixelRouteStateBucket].
 *
 * Keys deliberately use object identity instead of [name] equality. Reusing the same key object
 * preserves type safety, while two independently declared keys with the same diagnostic name do
 * not accidentally share values.
 *
 * @param T Value type associated with this key instance.
 * @property name Human-readable key name used by inspection and diagnostics.
 */
public class PixelRouteStateKey<T>(public val name: String) {
    init {
        require(name.isNotBlank()) { "PixelRouteStateKey name must not be blank" }
    }
}

/**
 * 定义 `PixelRouteStateBucket` 在 `PixelRouteEntry` 中承担的数据与行为边界。
 *
 * Route-local, typed state isolated to one concrete [PixelRouteEntry].
 *
 * The bucket is owned by one entry and is cleared when that entry is disposed. Callers should
 * declare and reuse [PixelRouteStateKey] instances rather than recreating keys for every access.
 */
public class PixelRouteStateBucket private constructor() {
    /** Values indexed by key identity rather than structural equality. */
    private val values: IdentityHashMap<PixelRouteStateKey<*>, Any?> = IdentityHashMap()

    /** Scroll snapshots retained for existing restoration-aware list widgets. */
    private val scrollStates: MutableMap<String, PixelListSavedState> = mutableMapOf()

    /** Whether inactive-route writes are currently eligible for later restoration. */
    private var retentionEnabled: Boolean = true

    /**
 * 公开 `PixelRouteEntry` 的 `keyNames` 配置或运行值。
 *
     * Diagnostic names of typed keys currently present in this bucket.
     *
     * Duplicate names are collapsed in the returned set even though their key instances remain
     * distinct in storage.
     */
    public val keyNames: Set<String>
        get() = values.keys.mapTo(linkedSetOf()) { key -> key.name }

    /**
 * 查询 `PixelRouteEntry` 的 `read` 结果，不产生额外状态变更。
 *
     * Reads the value associated with the exact [key] instance.
     *
     * @return Stored value, or `null` when this key is absent or stores a nullable `null` value.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T> read(key: PixelRouteStateKey<T>): T? {
        if (!retentionEnabled) return null
        return values[key] as T?
    }

    /** 执行 `PixelRouteEntry` 的 `write` 公开行为；具体参数、返回和副作用见下文。
 *
 * Stores [value] under the exact [key] instance, replacing its previous value.
 */
    public fun <T> write(key: PixelRouteStateKey<T>, value: T) {
        if (!retentionEnabled) return
        values[key] = value
    }

    /** 判断 `PixelRouteEntry` 是否满足 `contains` 条件，不修改现有状态。
 *
 * Returns whether the exact [key] instance currently has an entry in this bucket.
 */
    public operator fun <T> contains(key: PixelRouteStateKey<T>): Boolean = values.containsKey(key)

    /**
 * 从 `PixelRouteEntry` 释放 `remove` 内容并收敛相关所有权。
 *
     * Removes and returns the value associated with the exact [key] instance.
     *
     * @return Removed value, or `null` when this key was absent or stored a nullable `null` value.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T> remove(key: PixelRouteStateKey<T>): T? = values.remove(key) as T?

    /** 从 `PixelRouteEntry` 释放 `clear` 内容并收敛相关所有权。
 *
 * Clears typed values and compatibility scroll snapshots owned by this entry.
 */
    public fun clear() {
        values.clear()
        scrollStates.clear()
    }

    /** Reads a compatibility scroll snapshot for [restorationId]. */
    internal fun readScrollState(restorationId: String): PixelListSavedState? {
        if (!retentionEnabled) return null
        return scrollStates[restorationId]
    }

    /** Writes a compatibility scroll snapshot for [restorationId]. */
    internal fun writeScrollState(
        restorationId: String,
        savedState: PixelListSavedState,
    ) {
        if (!retentionEnabled) return
        scrollStates[restorationId] = savedState
    }

    /** Re-enables route-local retention when an entry becomes active again. */
    internal fun resumeRetention() {
        retentionEnabled = true
    }

    /** Clears and rejects late subtree-disposal writes for a non-maintained inactive entry. */
    internal fun suspendAndClearRetention() {
        clear()
        retentionEnabled = false
    }

    /** Permanently clears storage and rejects late writes from an unmounting disposed subtree. */
    internal fun disposeRetention() {
        clear()
        retentionEnabled = false
    }

    /** Internal construction boundary keeps every public bucket owned by a Navigator entry. */
    internal companion object {
        /** Creates one empty bucket for a newly allocated route entry. */
        fun create(): PixelRouteStateBucket = PixelRouteStateBucket()
    }
}

/**
 * 定义 `PixelRouteResultChannel` 在 `PixelRouteEntry` 中承担的数据与行为边界。
 *
 * Read-only typed completion channel owned by one route entry.
 *
 * Resolution and callback delivery are deliberately separate. A navigation operation first
 * resolves an outcome exactly once, then drains the callback after outgoing transition disposal.
 * This prevents callback re-entry from observing a half-settled stack.
 *
 * @param R Successful result value type.
 */
public class PixelRouteResultChannel<R> private constructor(
    onOutcome: ((PixelRouteOutcome<R>) -> Unit)? = null,
) {
    /** Callback retained until the resolved outcome reaches the navigator delivery phase. */
    private var pendingCallback: ((PixelRouteOutcome<R>) -> Unit)? = onOutcome

    /** Whether the terminal outcome has already passed through the delivery phase. */
    private var delivered: Boolean = false

    /** 公开 `PixelRouteEntry` 当前的 `state` 状态维度。
 *
 * Current resolution state of this channel.
 */
    public var state: PixelRouteResultState = PixelRouteResultState.Pending
        private set

    /** 公开 `PixelRouteEntry` 的 `outcome` 配置或运行值。
 *
 * Resolved terminal outcome, or `null` while [state] is [PixelRouteResultState.Pending].
 */
    public var outcome: PixelRouteOutcome<R>? = null
        private set

    /**
     * Resolves this channel if it is still pending without invoking its callback.
     *
     * @return `true` when [newOutcome] won resolution, or `false` after any prior resolution.
     */
    internal fun resolveExactlyOnce(newOutcome: PixelRouteOutcome<R>): Boolean {
        if (state != PixelRouteResultState.Pending) return false
        outcome = newOutcome
        state = when (newOutcome) {
            is PixelRouteOutcome.Success -> PixelRouteResultState.Succeeded
            is PixelRouteOutcome.Cancelled -> PixelRouteResultState.Cancelled
        }
        return true
    }

    /** Resolves a successful [value] exactly once without delivering the callback immediately. */
    internal fun resolveSuccessExactlyOnce(value: R): Boolean {
        return resolveExactlyOnce(PixelRouteOutcome.Success(value))
    }

    /** Resolves [reason] as cancellation exactly once without delivering the callback immediately. */
    internal fun cancelExactlyOnce(reason: PixelRouteCancellationReason): Boolean {
        return resolveExactlyOnce(PixelRouteOutcome.Cancelled(reason))
    }

    /**
     * Delivers a previously resolved outcome at most once.
     *
     * Delivery is marked complete and the callback reference is released before user code runs,
     * so a throwing or re-entrant callback cannot be invoked again.
     *
     * @return `true` when a resolved outcome was newly drained, otherwise `false`.
     */
    internal fun drainAndDeliver(): Boolean {
        val resolvedOutcome = outcome ?: return false
        if (delivered) return false
        delivered = true
        val callback = pendingCallback
        pendingCallback = null
        callback?.invoke(resolvedOutcome)
        return true
    }

    /** Internal construction boundary keeps channel resolution under Navigator ownership. */
    internal companion object {
        /** Creates one pending typed channel for a newly allocated route entry. */
        fun <R> create(
            onOutcome: ((PixelRouteOutcome<R>) -> Unit)?,
        ): PixelRouteResultChannel<R> = PixelRouteResultChannel(onOutcome)
    }
}

/**
 * 定义 `PixelRouteDestination` 在 `PixelRouteEntry` 中承担的数据与行为边界。
 *
 * Reusable, typed route destination definition.
 *
 * A destination describes how entries are built and how they participate in navigation. It does
 * not itself represent a stack position: each request creates an independent [PixelRouteEntry].
 *
 * @param A Non-null argument type accepted by this destination.
 * @param R Successful result type returned by entries of this destination.
 * @property id Stable, non-blank identifier used for diagnostics and future state restoration.
 */
public abstract class PixelRouteDestination<A : Any, R>(public val id: String) {
    init {
        require(id.isNotBlank()) { "PixelRouteDestination id must not be blank" }
    }

    /** 公开 `PixelRouteEntry` 当前的 `maintainState` 状态维度。
 *
 * Whether an inactive entry should retain its built subtree and route-local state.
 */
    public open val maintainState: Boolean = true

    /** 公开 `PixelRouteEntry` 的 `transition` 配置或运行值。
 *
 * Optional transition override used when entering or leaving this destination.
 */
    public open val transition: PixelRouteTransition? = null

    /** 公开 `PixelRouteEntry` 的 `transitionBuilder` 配置或运行值。
 *
 * Optional custom transition override used when entering or leaving this destination.
 */
    public open val transitionBuilder: PixelRouteTransitionBuilder? = null

    /** 判断 `PixelRouteEntry` 是否满足 `canPop` 条件，不修改现有状态。
 *
 * Returns whether [entry] currently permits a pop operation.
 */
    public open fun canPop(entry: PixelRouteEntry<A, R>): Boolean = true

    /** 执行 `PixelRouteEntry` 的 `onEnter` 公开行为；具体参数、返回和副作用见下文。
 *
 * Called after [entry] changes into [PixelRouteLifecycleState.Active].
 */
    public open fun onEnter(entry: PixelRouteEntry<A, R>): Unit = Unit

    /** 执行 `PixelRouteEntry` 的 `onExit` 公开行为；具体参数、返回和副作用见下文。
 *
 * Called after [entry] leaves [PixelRouteLifecycleState.Active].
 */
    public open fun onExit(entry: PixelRouteEntry<A, R>): Unit = Unit

    /** 执行 `PixelRouteEntry` 的 `onDispose` 公开行为；具体参数、返回和副作用见下文。
 *
 * Called once after [entry] reaches [PixelRouteLifecycleState.Disposed].
 */
    public open fun onDispose(entry: PixelRouteEntry<A, R>): Unit = Unit

    /** 创建或解析 `PixelRouteEntry` 的 `build` 结果，并在返回前校验输入。
 *
 * Builds the widget subtree for one concrete [scope] and its typed arguments.
 */
    public abstract fun build(
        context: BuildContext,
        scope: PixelRouteEntryScope<A, R>,
    ): Widget
}

/**
 * 执行 `PixelRouteEntry` 的 `pixelRouteDestination` 公开行为；具体参数、返回和副作用见下文。
 *
 * Creates a typed [PixelRouteDestination] without declaring a named subclass.
 *
 * @param A Non-null argument type accepted by the destination.
 * @param R Successful result type produced by the destination.
 * @param id Stable destination identifier.
 * @param maintainState Whether inactive entries retain their subtree and local state.
 * @param transition Optional built-in transition override.
 * @param transitionBuilder Optional custom transition override.
 * @param canPop Policy invoked before an entry is popped.
 * @param onEnter Callback invoked for each transition into the active lifecycle state.
 * @param onExit Callback invoked for each transition out of the active lifecycle state.
 * @param onDispose Callback invoked exactly once when an entry is disposed.
 * @param builder Widget factory receiving the build context and typed entry scope.
 */
public fun <A : Any, R> pixelRouteDestination(
    id: String,
    maintainState: Boolean = true,
    transition: PixelRouteTransition? = null,
    transitionBuilder: PixelRouteTransitionBuilder? = null,
    canPop: (PixelRouteEntry<A, R>) -> Boolean = { true },
    onEnter: (PixelRouteEntry<A, R>) -> Unit = {},
    onExit: (PixelRouteEntry<A, R>) -> Unit = {},
    onDispose: (PixelRouteEntry<A, R>) -> Unit = {},
    builder: (BuildContext, PixelRouteEntryScope<A, R>) -> Widget,
): PixelRouteDestination<A, R> {
    return object : PixelRouteDestination<A, R>(id) {
        /** Frozen state-retention policy supplied to the factory. */
        override val maintainState: Boolean = maintainState

        /** Frozen built-in transition supplied to the factory. */
        override val transition: PixelRouteTransition? = transition

        /** Frozen custom transition supplied to the factory. */
        override val transitionBuilder: PixelRouteTransitionBuilder? = transitionBuilder

        /** Delegates the pop decision to the factory callback. */
        override fun canPop(entry: PixelRouteEntry<A, R>): Boolean = canPop.invoke(entry)

        /** Delegates entry activation to the factory callback. */
        override fun onEnter(entry: PixelRouteEntry<A, R>) {
            onEnter.invoke(entry)
        }

        /** Delegates entry deactivation to the factory callback. */
        override fun onExit(entry: PixelRouteEntry<A, R>) {
            onExit.invoke(entry)
        }

        /** Delegates terminal disposal to the factory callback. */
        override fun onDispose(entry: PixelRouteEntry<A, R>) {
            onDispose.invoke(entry)
        }

        /** Delegates widget construction to the factory callback. */
        override fun build(
            context: BuildContext,
            scope: PixelRouteEntryScope<A, R>,
        ): Widget = builder.invoke(context, scope)
    }
}

/**
 * 定义 `PixelRouteRequest` 在 `PixelRouteEntry` 中承担的数据与行为边界。
 *
 * Typed request to create a new route entry.
 *
 * @param A Non-null argument type accepted by [destination].
 * @param R Successful result type produced by [destination].
 * @property destination Reusable destination definition to instantiate.
 * @property arguments Arguments captured independently by the new entry.
 */
public data class PixelRouteRequest<A : Any, R>(
    public val destination: PixelRouteDestination<A, R>,
    public val arguments: A,
)

/**
 * Internal operations exposed to [PixelRouteEntryScope] by its owning navigator.
 *
 * Implementations validate that the entry still belongs to the navigator before mutating the
 * stack, which keeps stale scopes from completing or replacing a different entry.
 */
internal interface PixelRouteEntryOwner {
    /** Completes [entry] with [result] and starts its removal when still valid. */
    fun completeEntry(entry: PixelRouteEntry<*, *>, result: Any?): Boolean

    /** Cancels [entry] for [reason] and starts its removal when still valid. */
    fun cancelEntry(
        entry: PixelRouteEntry<*, *>,
        reason: PixelRouteCancellationReason,
    ): Boolean

    /** Replaces [entry] with a newly created entry described by [request]. */
    fun <A : Any, R> replaceEntry(
        entry: PixelRouteEntry<*, *>,
        request: PixelRouteRequest<A, R>,
        onOutcome: ((PixelRouteOutcome<R>) -> Unit)?,
    ): PixelRouteEntry<A, R>?
}

/**
 * 定义 `PixelRouteEntry` 在 `PixelRouteEntry` 中承担的数据与行为边界。
 *
 * One concrete and independently stateful position in a navigator stack.
 *
 * Entries are created only by a navigator. Pushing the same [destination] repeatedly still gives
 * each entry a unique [id], [stateBucket], [resultChannel], and lifecycle.
 *
 * @param A Non-null destination argument type.
 * @param R Successful destination result type.
 * @property id Unique identity of this stack entry.
 * @property destination Reusable typed destination instantiated by this entry.
 * @property arguments Typed arguments captured when the entry was created.
 * @property stateBucket Route-local state owned exclusively by this entry.
 * @property resultChannel Read-only completion channel owned exclusively by this entry.
 * @property maintainState Frozen retention policy captured from [destination] at creation time.
 */
public class PixelRouteEntry<A : Any, R> private constructor(
    public val id: PixelRouteEntryId,
    public val destination: PixelRouteDestination<A, R>,
    public val arguments: A,
    private val owner: PixelRouteEntryOwner,
    onOutcome: ((PixelRouteOutcome<R>) -> Unit)? = null,
    public val stateBucket: PixelRouteStateBucket,
    public val maintainState: Boolean = destination.maintainState,
) {
    /** 公开 `PixelRouteEntry` 的 `resultChannel` 配置或运行值。
 *
 * Typed completion channel resolved and delivered by the owning navigator.
 */
    public val resultChannel: PixelRouteResultChannel<R> = PixelRouteResultChannel.create(onOutcome)

    /** Scope reused for every declarative rebuild of this entry. */
    private val entryScope: PixelRouteEntryScope<A, R> = PixelRouteEntryScope.create(this, owner)

    /** 公开 `PixelRouteEntry` 当前的 `lifecycleState` 状态维度。
 *
 * Current lifecycle state, mutated only through guarded internal transitions.
 */
    public var lifecycleState: PixelRouteLifecycleState = PixelRouteLifecycleState.Created
        private set

    /** Optional built-in transition captured dynamically from [destination]. */
    internal val routeTransition: PixelRouteTransition?
        get() = destination.transition

    /** Optional custom transition captured dynamically from [destination]. */
    internal val routeTransitionBuilder: PixelRouteTransitionBuilder?
        get() = destination.transitionBuilder

    /** Returns whether this non-terminal entry currently permits a pop. */
    internal fun canPop(): Boolean {
        if (
            lifecycleState == PixelRouteLifecycleState.Removing ||
            lifecycleState == PixelRouteLifecycleState.Disposed
        ) {
            return false
        }
        return destination.canPop(this)
    }

    /**
     * Builds this entry's declarative subtree.
     *
     * A transition may settle from an animation listener during the same retained build pass that
     * still owns the outgoing widget. Allowing that final stale pass keeps disposal ordering
     * deterministic; the next navigator rebuild removes the subtree permanently.
     */
    internal fun build(context: BuildContext): Widget {
        return destination.build(context, entryScope)
    }

    /**
     * Activates a created or inactive entry exactly once for the current lifecycle transition.
     *
     * @return `true` when activation and [PixelRouteDestination.onEnter] were performed.
     */
    internal fun enterExactlyOnce(): Boolean {
        if (
            lifecycleState != PixelRouteLifecycleState.Created &&
            lifecycleState != PixelRouteLifecycleState.Inactive
        ) {
            return false
        }
        lifecycleState = PixelRouteLifecycleState.Active
        stateBucket.resumeRetention()
        destination.onEnter(this)
        return true
    }

    /** Initializes a non-foreground restored entry without synthesizing enter/exit callbacks. */
    internal fun initializeInactiveExactlyOnce(): Boolean {
        if (lifecycleState != PixelRouteLifecycleState.Created) return false
        lifecycleState = PixelRouteLifecycleState.Inactive
        if (!maintainState) stateBucket.suspendAndClearRetention()
        return true
    }

    /**
     * Deactivates an active entry exactly once for the current lifecycle transition.
     *
     * @return `true` when deactivation and [PixelRouteDestination.onExit] were performed.
     */
    internal fun exitExactlyOnce(): Boolean {
        if (lifecycleState != PixelRouteLifecycleState.Active) return false
        lifecycleState = PixelRouteLifecycleState.Inactive
        if (!maintainState) stateBucket.suspendAndClearRetention()
        destination.onExit(this)
        return true
    }

    /**
     * Moves a non-terminal entry into the removal phase after deactivating it when necessary.
     *
     * @return `true` when this call newly started removal.
     */
    internal fun beginRemovalExactlyOnce(): Boolean {
        if (
            lifecycleState == PixelRouteLifecycleState.Removing ||
            lifecycleState == PixelRouteLifecycleState.Disposed
        ) {
            return false
        }
        val wasActive = lifecycleState == PixelRouteLifecycleState.Active
        lifecycleState = PixelRouteLifecycleState.Removing
        if (wasActive) {
            destination.onExit(this)
        }
        return true
    }

    /**
     * Disposes this entry, clears local state, and invokes the destination callback at most once.
     *
     * @return `true` when this call performed terminal disposal.
     */
    internal fun disposeExactlyOnce(): Boolean {
        if (lifecycleState == PixelRouteLifecycleState.Disposed) return false
        val wasActive = lifecycleState == PixelRouteLifecycleState.Active
        lifecycleState = PixelRouteLifecycleState.Disposed
        stateBucket.disposeRetention()
        var callbackFailure: Throwable? = null
        if (wasActive) {
            try {
                destination.onExit(this)
            } catch (error: Throwable) {
                callbackFailure = error
            }
        }
        try {
            destination.onDispose(this)
        } catch (error: Throwable) {
            callbackFailure?.addSuppressed(error)
            if (callbackFailure == null) callbackFailure = error
        }
        callbackFailure?.let { error -> throw error }
        return true
    }

    /** Resolves a typed successful [value] at most once without immediate callback delivery. */
    internal fun resolveSuccessExactlyOnce(value: R): Boolean {
        return resultChannel.resolveSuccessExactlyOnce(value)
    }

    /** Resolves a cancellation [reason] at most once without immediate callback delivery. */
    internal fun cancelResultExactlyOnce(reason: PixelRouteCancellationReason): Boolean {
        return resultChannel.cancelExactlyOnce(reason)
    }

    /** Delivers this entry's resolved outcome after stack transition settlement. */
    internal fun drainResultDelivery(): Boolean = resultChannel.drainAndDeliver()

    /** Produces a deterministic, argument-free inspection view of this entry. */
    internal fun inspection(): PixelRouteEntryInspection {
        return PixelRouteEntryInspection(
            id = id,
            destinationId = destination.id,
            lifecycleState = lifecycleState,
            resultState = resultChannel.state,
            maintainState = maintainState,
            stateKeyNames = stateBucket.keyNames,
        )
    }

    /** Internal construction boundary allocates every entry with a fresh state bucket by default. */
    internal companion object {
        /** Creates one Navigator-owned route entry. */
        fun <A : Any, R> create(
            id: PixelRouteEntryId,
            destination: PixelRouteDestination<A, R>,
            arguments: A,
            owner: PixelRouteEntryOwner,
            onOutcome: ((PixelRouteOutcome<R>) -> Unit)? = null,
            stateBucket: PixelRouteStateBucket = PixelRouteStateBucket.create(),
            maintainState: Boolean = destination.maintainState,
        ): PixelRouteEntry<A, R> {
            return PixelRouteEntry(
                id = id,
                destination = destination,
                arguments = arguments,
                owner = owner,
                onOutcome = onOutcome,
                stateBucket = stateBucket,
                maintainState = maintainState,
            )
        }
    }
}

/**
 * 定义 `PixelRouteEntryScope` 在 `PixelRouteEntry` 中承担的数据与行为边界。
 *
 * Typed operations and state exposed while building one [PixelRouteEntry].
 *
 * A retained scope can become stale after its entry is removed. Mutating methods therefore return
 * whether the owning navigator accepted the operation.
 *
 * @param A Non-null argument type of [entry].
 * @param R Successful result type of [entry].
 * @property entry Concrete entry represented by this scope.
 */
public class PixelRouteEntryScope<A : Any, R> private constructor(
    public val entry: PixelRouteEntry<A, R>,
    private val owner: PixelRouteEntryOwner,
) {
    /** 公开 `PixelRouteEntry` 的 `arguments` 配置或运行值。
 *
 * Typed arguments captured by [entry].
 */
    public val arguments: A
        get() = entry.arguments

    /** 公开 `PixelRouteEntry` 当前的 `stateBucket` 状态维度。
 *
 * Route-local typed state bucket owned by [entry].
 */
    public val stateBucket: PixelRouteStateBucket
        get() = entry.stateBucket

    /** 执行 `PixelRouteEntry` 的 `complete` 路由操作并保持结果恰好一次。
 *
 * Completes [entry] with [result] and requests its removal from the owning navigator.
 */
    public fun complete(result: R): Boolean = owner.completeEntry(entry, result)

    /** 判断 `PixelRouteEntry` 是否满足 `cancel` 条件，不修改现有状态。
 *
 * Cancels [entry] for [reason] and requests its removal from the owning navigator.
 */
    public fun cancel(
        reason: PixelRouteCancellationReason = PixelRouteCancellationReason.Explicit,
    ): Boolean = owner.cancelEntry(entry, reason)

    /**
 * 执行 `PixelRouteEntry` 的 `replaceWith` 路由操作并保持结果恰好一次。
 *
     * Replaces [entry] with a new typed request.
     *
     * @param request Destination and arguments for the replacement entry.
     * @param onOutcome Optional callback delivered after the replacement entry settles.
     * @return Newly created replacement entry, or `null` if this scope is stale.
     */
    public fun <NextA : Any> replaceWith(
        request: PixelRouteRequest<NextA, R>,
        onOutcome: ((PixelRouteOutcome<R>) -> Unit)? = null,
    ): PixelRouteEntry<NextA, R>? {
        return owner.replaceEntry(entry, request, onOutcome)
    }

    /** Internal construction boundary keeps scope mutation capabilities Navigator-owned. */
    internal companion object {
        /** Creates one scope paired with its exact entry and owner capability. */
        fun <A : Any, R> create(
            entry: PixelRouteEntry<A, R>,
            owner: PixelRouteEntryOwner,
        ): PixelRouteEntryScope<A, R> = PixelRouteEntryScope(entry, owner)
    }
}

/** 定义 `PixelNavigationAction` 在 `PixelRouteEntry` 中承担的数据与行为边界。
 *
 * Navigation operation reported to observers and failure inspection.
 */
public enum class PixelNavigationAction {
    /** A new entry was appended to the stack. */
    Push,

    /** The active entry was popped. */
    Pop,

    /** An entry was replaced by a newly created entry. */
    Replace,

    /** A specific entry was removed from the stack. */
    Remove,

    /** Multiple entries were cleared from the stack. */
    Clear,

    /** A saved stack was restored. */
    Restore,

    /** A deep link replaced or extended the stack. */
    DeepLink,

    /** The owning navigator and its remaining entries were disposed. */
    Dispose,

    /** An entry completed with a successful result. */
    Result,

    /** An entry completed by cancellation. */
    Cancel,
}

/** 定义 `PixelNavigationFailureReason` 在 `PixelRouteEntry` 中承担的数据与行为边界。
 *
 * Machine-readable reason for a rejected or failed navigation action.
 */
public enum class PixelNavigationFailureReason {
    /** A pop was requested while only the root entry remained. */
    CannotPopRoot,

    /** The active destination rejected the pop request. */
    PopRejected,

    /** The requested entry does not belong to the current stack. */
    EntryNotFound,

    /** The requested entry is no longer in a lifecycle state accepted by the operation. */
    EntryNotActive,

    /** A second terminal outcome was requested for an already resolved result channel. */
    ResultAlreadyResolved,

    /** A restore or reset request described an invalid stack. */
    InvalidStack,

    /** A requested destination could not be resolved by the available registry. */
    UnknownDestination,

    /** The owning navigator had already been disposed. */
    NavigatorDisposed,

    /** A destination lifecycle callback threw after the state transition was committed. */
    LifecycleCallbackFailed,

    /** A route result callback threw after its channel had reached a terminal state. */
    ResultCallbackFailed,

    /** A navigation observer threw while receiving an immutable event. */
    ObserverCallbackFailed,
}

/**
 * 表示 `PixelRouteEntry` 的 `PixelNavigationFailure` 稳定结果或事件分支。
 *
 * Structured diagnostic for a failed navigation action.
 *
 * @property action Action that could not be completed.
 * @property reason Machine-readable failure category.
 * @property message Human-readable diagnostic suitable for logs and tests.
 * @property entryId Related entry identity when one was available.
 * @property destinationId Related destination identifier when one was available.
 */
public data class PixelNavigationFailure(
    public val action: PixelNavigationAction,
    public val reason: PixelNavigationFailureReason,
    public val message: String,
    public val entryId: PixelRouteEntryId? = null,
    public val destinationId: String? = null,
) {
    init {
        require(message.isNotBlank()) { "PixelNavigationFailure message must not be blank" }
    }
}

/** 定义 `PixelNavigationEventType` 在 `PixelRouteEntry` 中承担的数据与行为边界。
 *
 * Phase of a [PixelNavigationEvent] emitted to observers.
 */
public enum class PixelNavigationEventType {
    /** The navigator accepted an action and is about to mutate its stack. */
    Started,

    /** The stack mutation and any synchronous lifecycle work completed successfully. */
    Completed,

    /** The action was rejected or failed and includes a structured failure. */
    Failed,
}

/**
 * 表示 `PixelRouteEntry` 的 `PixelNavigationEvent` 稳定结果或事件分支。
 *
 * Immutable observer event describing one navigation action phase.
 *
 * @property sequence Monotonically increasing positive sequence within one navigator.
 * @property action Action represented by this event.
 * @property type Current phase of the action.
 * @property entryId Primary entry affected by the action, when applicable.
 * @property fromEntryId Active entry before the action, when applicable.
 * @property toEntryId Active entry after the action, when applicable.
 * @property failure Structured failure required for failed events.
 */
public data class PixelNavigationEvent(
    public val sequence: Long,
    public val action: PixelNavigationAction,
    public val type: PixelNavigationEventType,
    public val entryId: PixelRouteEntryId? = null,
    public val fromEntryId: PixelRouteEntryId? = null,
    public val toEntryId: PixelRouteEntryId? = null,
    public val failure: PixelNavigationFailure? = null,
) {
    init {
        require(sequence > 0L) { "PixelNavigationEvent sequence must be greater than zero" }
        require(type == PixelNavigationEventType.Failed || failure == null) {
            "Only failed PixelNavigationEvent instances may contain a failure"
        }
        require(type != PixelNavigationEventType.Failed || failure != null) {
            "Failed PixelNavigationEvent instances must contain a failure"
        }
    }
}

/** 定义 `PixelNavigationObserver` 在 `PixelRouteEntry` 中的可替换调用契约。
 *
 * Receives ordered navigation events from one navigator.
 */
public fun interface PixelNavigationObserver {
    /** 执行 `PixelRouteEntry` 的 `onNavigationEvent` 公开行为；具体参数、返回和副作用见下文。
 *
 * Handles one immutable [event] after it has been recorded by the navigator.
 */
    public fun onNavigationEvent(event: PixelNavigationEvent)
}

/**
 * 定义 `PixelRouteEntryInspection` 在 `PixelRouteEntry` 中承担的数据与行为边界。
 *
 * Deterministic inspection snapshot of one route entry without retaining its arguments or widget.
 *
 * @property id Unique entry identity.
 * @property destinationId Stable destination identifier.
 * @property lifecycleState Lifecycle state captured by this snapshot.
 * @property resultState Result-channel state captured by this snapshot.
 * @property maintainState Frozen retention policy of the entry.
 * @property stateKeyNames Diagnostic typed-state key names currently stored by the entry.
 */
public data class PixelRouteEntryInspection(
    public val id: PixelRouteEntryId,
    public val destinationId: String,
    public val lifecycleState: PixelRouteLifecycleState,
    public val resultState: PixelRouteResultState,
    public val maintainState: Boolean,
    public val stateKeyNames: Set<String>,
)

/**
 * 定义 `PixelRouteTransitionInspection` 在 `PixelRouteEntry` 中承担的数据与行为边界。
 *
 * Deterministic inspection snapshot of an in-flight route transition.
 *
 * @property id Positive transition identity allocated by the navigator.
 * @property operation Stack operation driving the transition.
 * @property outgoingEntryId Entry leaving the foreground.
 * @property incomingEntryId Entry entering the foreground.
 */
public data class PixelRouteTransitionInspection(
    public val id: Long,
    public val operation: PixelNavigatorOperation,
    public val outgoingEntryId: PixelRouteEntryId,
    public val incomingEntryId: PixelRouteEntryId,
) {
    init {
        require(id > 0L) { "PixelRouteTransitionInspection id must be greater than zero" }
    }
}

/**
 * 保存 `PixelRouteEntry` 的 `PixelNavigatorInspectionSnapshot` 可观察或可恢复状态。
 *
 * Testable, immutable inspection snapshot of the current navigator state.
 *
 * @property entries Ordered entry snapshots from root to foreground.
 * @property currentEntryId Foreground entry identity, or `null` after navigator disposal.
 * @property canPop Whether the captured stack had more than one entry.
 * @property transition In-flight transition, if one existed at capture time.
 * @property lastFailure Most recently recorded navigation failure, if any.
 * @property isDisposed Whether the owning navigator had reached terminal disposal.
 */
public data class PixelNavigatorInspectionSnapshot(
    public val entries: List<PixelRouteEntryInspection>,
    public val currentEntryId: PixelRouteEntryId?,
    public val canPop: Boolean,
    public val transition: PixelRouteTransitionInspection? = null,
    public val lastFailure: PixelNavigationFailure? = null,
    public val isDisposed: Boolean = false,
)
