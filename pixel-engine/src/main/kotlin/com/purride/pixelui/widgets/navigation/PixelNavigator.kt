package com.purride.pixelui.widgets.navigation

import android.os.Bundle
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Builder
import com.purride.pixelui.ChangeNotifier
import com.purride.pixelui.InheritedWidget
import com.purride.pixelui.Opacity
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelMotionTheme
import com.purride.pixelui.PixelMotionTransitionPreset
import com.purride.pixelui.PixelResolvedMotion
import com.purride.pixelui.PixelPredictiveBackCallback
import com.purride.pixelui.PixelPredictiveBackEvent
import com.purride.pixelui.PixelPredictiveBackHandler
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Stack
import com.purride.pixelui.Widget
import com.purride.pixelui.dependOnInheritedWidgetOfExactType
import com.purride.pixelui.internal.HitTestResult
import com.purride.pixelui.internal.ElementSubtreeVisibility
import com.purride.pixelui.internal.LeafRenderObjectWidget
import com.purride.pixelui.internal.PaintContext
import com.purride.pixelui.internal.PixelClickTarget
import com.purride.pixelui.internal.PixelListTarget
import com.purride.pixelui.internal.PixelPagerTarget
import com.purride.pixelui.internal.PixelRefreshTarget
import com.purride.pixelui.internal.PixelScrollbarTarget
import com.purride.pixelui.internal.PixelSemanticsTarget
import com.purride.pixelui.internal.PixelSliderTarget
import com.purride.pixelui.internal.PixelTextInputTarget
import com.purride.pixelui.internal.RenderBox
import com.purride.pixelui.internal.RenderConstraints
import com.purride.pixelui.internal.RenderObject
import com.purride.pixelui.internal.RenderSize
import com.purride.pixelui.internal.SingleChildRenderObject
import com.purride.pixelui.internal.SingleChildRenderObjectWidget
import com.purride.pixelui.animation.Curve
import com.purride.pixelui.animation.CurvedAnimation
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelAnimationStatus
import com.purride.pixelui.animation.PixelTickerProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import java.util.IdentityHashMap
import kotlin.math.roundToInt

/**
 * 公开 `PixelNavigator` 的 `PixelNavigatorBundleKey` 配置或运行值。
 *
 * Default [Bundle] key used by [PixelNavigatorState.saveToBundle] and
 * [PixelNavigatorState.restoreFromBundle].
 */
public const val PixelNavigatorBundleKey: String = "com.purride.pixelui.navigator.routeNames"

/** 定义 `PixelNavigator` 的路由栈操作与生命周期边界，失败不会留下部分提交状态。 */
public data class PixelRoute(
    val name: String,
    val builder: (BuildContext) -> Widget,
    val transition: PixelRouteTransition? = null,
    val canPop: (() -> Boolean)? = null,
    val onEnter: (() -> Unit)? = null,
    val onExit: (() -> Unit)? = null,
    val onDispose: (() -> Unit)? = null,
    val transitionBuilder: PixelRouteTransitionBuilder? = null,
)

/** 保存 `PixelNavigator` 的可观察或可恢复状态；字段变更必须维持类型声明的不变量。 */
public data class PixelNavigatorSnapshot(
    val routeNames: List<String>,
)

/**
 * 执行 `PixelNavigator` 的 `saveToBundle` 公开行为；具体参数、返回和副作用见下文。
 *
 * Saves this route-name snapshot into an Android [Bundle].
 */
public fun PixelNavigatorSnapshot.saveToBundle(
    outState: Bundle,
    key: String = PixelNavigatorBundleKey,
) {
    require(key.isNotBlank()) { "PixelNavigator snapshot Bundle key must not be blank" }
    outState.putStringArrayList(key, ArrayList(routeNames))
}

/**
 * 查询 `PixelNavigator` 的 `getPixelNavigatorSnapshot` 结果，不产生额外状态变更。
 *
 * Reads a [PixelNavigatorSnapshot] previously saved into this Android [Bundle].
 */
public fun Bundle.getPixelNavigatorSnapshot(
    key: String = PixelNavigatorBundleKey,
): PixelNavigatorSnapshot? {
    require(key.isNotBlank()) { "PixelNavigator snapshot Bundle key must not be blank" }
    val names = getStringArrayList(key) ?: return null
    if (names.isEmpty()) return null
    return PixelNavigatorSnapshot(routeNames = names.toList())
}

/** 定义 `PixelNavigator` 的路由栈操作与生命周期边界，失败不会留下部分提交状态。 */
public enum class PixelRouteTransition {
    None,
    Fade,
    SlideHorizontal,
    SlideVertical,
}

/** 定义 `PixelNavigator` 的路由栈操作与生命周期边界，失败不会留下部分提交状态。 */
public enum class PixelNavigatorOperation {
    Push,
    Pop,
    Replace,
}

internal fun resolvePixelRouteTransition(
    operation: PixelNavigatorOperation,
    outgoingTransition: PixelRouteTransition?,
    incomingTransition: PixelRouteTransition?,
    defaultTransition: PixelRouteTransition,
): PixelRouteTransition {
    val routeTransition = when (operation) {
        PixelNavigatorOperation.Pop -> outgoingTransition
        PixelNavigatorOperation.Push,
        PixelNavigatorOperation.Replace,
        -> incomingTransition
    }
    return routeTransition ?: defaultTransition
}

/**
 * 定义 `PixelRouteTransitionBuilder` 在 `PixelNavigator` 中的可替换调用契约。
 *
 * Builds one frame of a custom route transition.
 *
 * [progress] advances from 0f to 1f. Navigator owns the ticker and completes route disposal
 * only after the custom transition settles.
 */
public fun interface PixelRouteTransitionBuilder {
    /** 创建 `PixelNavigator` 所需的新对象，并在返回前建立其初始不变量。 */
    public fun build(
        progress: Float,
        operation: PixelNavigatorOperation,
        outgoing: Widget,
        incoming: Widget,
    ): Widget
}

/**
 * 保存 `PixelNavigator` 的 `PixelNavigatorState` 可观察或可恢复状态。
 *
 * Mutable navigation controller backed by independent [PixelRouteEntry] instances.
 *
 * The legacy [PixelRoute] API remains available as a compatibility projection. Every push still
 * creates a fresh entry, so repeated route objects never share state buckets or result channels.
 */
public class PixelNavigatorState private constructor(
    /** Legacy root used by the source- and binary-compatible constructor. */
    initialRoute: PixelRoute?,
    /** Typed root used by [PixelNavigator.typed]. */
    initialRequest: PixelRouteRequest<*, *>?,
) : ChangeNotifier(), PixelPredictiveBackCallback {
    /** Preserves the existing JVM constructor used by legacy Navigator widgets and consumers. */
    internal constructor(initialRoute: PixelRoute) : this(
        initialRoute = initialRoute,
        initialRequest = null,
    )

    /** Creates a controller whose root is persistable through a typed destination adapter. */
    internal constructor(initialRequest: PixelRouteRequest<*, *>) : this(
        initialRoute = null,
        initialRequest = initialRequest,
    )
    /** Ordered stack entries from root to foreground. */
    private val routeEntries: MutableList<PixelRouteEntry<*, *>> = mutableListOf()

    /** Legacy destination adapters cached by route object identity. */
    private val legacyDestinations: IdentityHashMap<PixelRoute, LegacyPixelRouteDestination> =
        IdentityHashMap()

    /** Legacy route views synthesized for typed entries. */
    private val typedRouteViews: MutableMap<PixelRouteEntryId, PixelRoute> = mutableMapOf()

    /** Entries awaiting transition settlement, disposal, and result delivery. */
    private val pendingFinalizations:
        LinkedHashMap<PixelRouteEntryId, PendingRouteEntryFinalization> = linkedMapOf()

    /** Navigation observers registered by SDK consumers. */
    private val navigationObservers: LinkedHashSet<PixelNavigationObserver> = linkedSetOf()

    /** Internal capability object supplied to typed entry scopes without exposing erased methods. */
    private val entryOwner: PixelRouteEntryOwner = object : PixelRouteEntryOwner {
        /** Delegates a type-erased scope completion back into the owning Navigator. */
        override fun completeEntry(entry: PixelRouteEntry<*, *>, result: Any?): Boolean {
            return completeOwnedEntry(entry, result)
        }

        /** Delegates explicit scope cancellation back into the owning Navigator. */
        override fun cancelEntry(
            entry: PixelRouteEntry<*, *>,
            reason: PixelRouteCancellationReason,
        ): Boolean {
            return cancelOwnedEntry(entry, reason)
        }

        /** Delegates typed scope replacement back into the owning Navigator. */
        override fun <A : Any, R> replaceEntry(
            entry: PixelRouteEntry<*, *>,
            request: PixelRouteRequest<A, R>,
            onOutcome: ((PixelRouteOutcome<R>) -> Unit)?,
        ): PixelRouteEntry<A, R>? {
            return replaceOwnedEntry(entry, request, onOutcome)
        }
    }

    /** Monotonic entry identity source owned by this navigator. */
    private var nextEntryIdValue: Long = 0L

    /** Monotonic transition identity source owned by this navigator. */
    private var nextTransitionIdValue: Long = 0L

    /** Monotonic observer event sequence owned by this navigator. */
    private var nextEventSequenceValue: Long = 0L

    /** Whether terminal navigator disposal has already run. */
    private var navigatorDisposed: Boolean = false

    /** Most recent structured failure retained for inspection. */
    private var latestFailure: PixelNavigationFailure? = null

    /** Transition currently presented by the navigator widget. */
    internal var activeTransition: PixelNavigatorTransitionRecord? = null
        private set

    /** Gesture-controlled pop preview that has not mutated the route stack yet. */
    internal var predictiveBackTransition: PixelNavigatorPredictiveBackRecord? = null
        private set

    /** Whether a stack mutation invalidated a started gesture before its platform terminal event. */
    private var predictiveBackInterrupted: Boolean = false

    /** 表示 `PixelNavigator` 当前是否满足 `canPop` 对应条件。
 *
 * Whether the current stack contains an entry below the foreground entry.
 */
    public val canPop: Boolean
        get() = routeEntries.size > 1

    /** 公开 `PixelNavigator` 的 `currentEntry` 配置或运行值。
 *
 * Foreground entry with its typed destination erased for heterogeneous stack access.
 */
    public val currentEntry: PixelRouteEntry<*, *>
        get() = checkNotNull(routeEntries.lastOrNull()) { "PixelNavigatorState is disposed" }

    /** 公开 `PixelNavigator` 的 `entries` 配置或运行值。
 *
 * Immutable root-to-foreground entry list.
 */
    public val entries: List<PixelRouteEntry<*, *>>
        get() = routeEntries.toList()

    /** 公开 `PixelNavigator` 的 `currentRoute` 配置或运行值。
 *
 * Legacy foreground route projection retained for source and binary compatibility.
 */
    public val currentRoute: PixelRoute
        get() = routeView(currentEntry)

    /** 公开 `PixelNavigator` 的 `stack` 配置或运行值。
 *
 * Legacy route-stack projection retained for source and binary compatibility.
 */
    public val stack: List<PixelRoute>
        get() = routeEntries.map(::routeView)

    /** 公开 `PixelNavigator` 的 `lastFailure` 配置或运行值。
 *
 * Most recently recorded navigation failure, if any.
 */
    public val lastFailure: PixelNavigationFailure?
        get() = latestFailure

    /** 公开 `PixelNavigator` 的 `predictiveBackProgress` 配置或运行值。
 *
 * Current gesture-controlled back progress, or `null` outside a predictive-back session.
 */
    public val predictiveBackProgress: Float?
        get() = predictiveBackTransition?.progress

    init {
        require((initialRoute == null) != (initialRequest == null)) {
            "PixelNavigatorState requires exactly one legacy route or typed request"
        }
        val rootEntry = if (initialRequest != null) {
            createTypedInitialEntry(initialRequest)
        } else {
            createLegacyEntry(checkNotNull(initialRoute), onOutcome = null)
        }
        routeEntries += rootEntry
        invokeLifecycle(PixelNavigationAction.Push, rootEntry) {
            rootEntry.enterExactlyOnce()
        }
    }

    /** 向 `PixelNavigator` 注册 `addObserver` 内容并绑定对应生命周期。
 *
 * Adds [observer] if it is not already registered.
 */
    public fun addObserver(observer: PixelNavigationObserver) {
        navigationObservers += observer
    }

    /** 从 `PixelNavigator` 释放 `removeObserver` 内容并收敛相关所有权。
 *
 * Removes [observer] from this navigator.
 */
    public fun removeObserver(observer: PixelNavigationObserver) {
        navigationObservers -= observer
    }

    /** 执行 `PixelNavigator` 的 `inspectionSnapshot` 公开行为；具体参数、返回和副作用见下文。
 *
 * Captures an immutable, non-persistable runtime inspection snapshot.
 */
    public fun inspectionSnapshot(): PixelNavigatorInspectionSnapshot {
        val presentedTransition = predictiveBackTransition?.transition ?: activeTransition
        val transition = presentedTransition?.let { record ->
            PixelRouteTransitionInspection(
                id = record.id,
                operation = record.operation,
                outgoingEntryId = record.outgoingEntry.id,
                incomingEntryId = record.incomingEntry.id,
            )
        }
        return PixelNavigatorInspectionSnapshot(
            entries = routeEntries.map(PixelRouteEntry<*, *>::inspection),
            currentEntryId = routeEntries.lastOrNull()?.id,
            canPop = canPop,
            transition = transition,
            lastFailure = latestFailure,
            isDisposed = navigatorDisposed,
        )
    }

    /** 执行 `PixelNavigator` 的 `push` 路由操作并保持结果恰好一次。
 *
 * Pushes one legacy string route and ignores its eventual result.
 */
    public fun push(route: PixelRoute) {
        pushLegacyInternal(route, onResult = null)
    }

    /** 执行 `PixelNavigator` 的 `push` 路由操作并保持结果恰好一次。
 *
 * Pushes one legacy string route and delivers its value, or `null` on cancellation.
 */
    public fun push(
        route: PixelRoute,
        onResult: (Any?) -> Unit,
    ) {
        pushLegacyInternal(route, onResult)
    }

    /** 执行 `PixelNavigator` 的 `push` 路由操作并保持结果恰好一次。
 *
 * Pushes a typed [request] and returns its newly allocated independent entry.
 */
    public fun <A : Any, R> push(request: PixelRouteRequest<A, R>): PixelRouteEntry<A, R> {
        return pushTypedInternal(request, onOutcome = null)
    }

    /** 执行 `PixelNavigator` 的 `push` 路由操作并保持结果恰好一次。
 *
 * Pushes a typed [request] and delivers its explicit success or cancellation outcome.
 */
    public fun <A : Any, R> push(
        request: PixelRouteRequest<A, R>,
        onOutcome: (PixelRouteOutcome<R>) -> Unit,
    ): PixelRouteEntry<A, R> {
        return pushTypedInternal(request, onOutcome)
    }

    /** 执行 `PixelNavigator` 的 `complete` 路由操作并保持结果恰好一次。
 *
 * Completes a typed [entry] with [result] when it is still the active stack entry.
 */
    public fun <R> complete(entry: PixelRouteEntry<*, R>, result: R): Boolean {
        return completeOwnedEntry(entry, result)
    }

    /** 判断 `PixelNavigator` 是否满足 `cancel` 条件，不修改现有状态。
 *
 * Cancels and removes [entry] with an explicit typed [reason].
 */
    public fun cancel(
        entry: PixelRouteEntry<*, *>,
        reason: PixelRouteCancellationReason = PixelRouteCancellationReason.Explicit,
        animated: Boolean = true,
    ): Boolean {
        return removeEntryInternal(
            entry = entry,
            reason = reason,
            action = PixelNavigationAction.Cancel,
            animated = animated,
        )
    }

    /** 从 `PixelNavigator` 释放 `remove` 内容并收敛相关所有权。
 *
 * Removes the entry identified by [entryId], cancelling only its pending result channel.
 */
    public fun remove(entryId: PixelRouteEntryId, animated: Boolean = true): Boolean {
        val entry = routeEntries.firstOrNull { candidate -> candidate.id == entryId }
            ?: return reject(
                action = PixelNavigationAction.Remove,
                reason = PixelNavigationFailureReason.EntryNotFound,
                message = "PixelNavigator.remove() could not find entry $entryId",
                entryId = entryId,
            )
        return removeEntryInternal(
            entry = entry,
            reason = PixelRouteCancellationReason.Removed,
            action = PixelNavigationAction.Remove,
            animated = animated,
        )
    }

    /** 从 `PixelNavigator` 释放 `remove` 内容并收敛相关所有权。
 *
 * Removes [entry], cancelling only its pending result channel.
 */
    public fun remove(entry: PixelRouteEntry<*, *>, animated: Boolean = true): Boolean {
        return remove(entry.id, animated)
    }

    /** 执行 `PixelNavigator` 的 `pop` 路由操作并保持结果恰好一次。
 *
 * Pops the foreground entry with the legacy successful `null` result.
 */
    public fun pop(): Boolean = pop(result = null)

    /** 执行 `PixelNavigator` 的 `pop` 路由操作并保持结果恰好一次。
 *
 * Pops the foreground entry with a legacy untyped successful [result].
 */
    public fun pop(result: Any?): Boolean {
        return popInternal(
            completion = PendingRouteCompletion.Success(result),
            expectedEntry = null,
        )
    }

    /** 执行 `PixelNavigator` 的 `maybePop` 公开行为；具体参数、返回和副作用见下文。
 *
 * Attempts the same cancellation behavior as [pop].
 */
    public fun maybePop(): Boolean = pop()

    /** 执行 `PixelNavigator` 的 `maybePop` 公开行为；具体参数、返回和副作用见下文。
 *
 * Attempts the same legacy successful completion behavior as [pop].
 */
    public fun maybePop(result: Any?): Boolean = pop(result)

    /**
     * Starts a non-mutating pop preview controlled by Android predictive-back progress.
     *
     * The current entry remains active and its result stays pending until
     * [onBackCommitted]. Returning `false` lets the enclosing dispatcher try a lower-priority
     * handler or the application fallback.
     */
    override fun onBackStarted(event: PixelPredictiveBackEvent): Boolean {
        if (!ensureUsable(PixelNavigationAction.Pop, throwOnFailure = false)) return false
        interruptPredictiveBackPreview()
        settleInterruptedTransition()
        predictiveBackInterrupted = false
        if (!canPop) return false
        val outgoing = currentEntry
        val permitsPop = try {
            outgoing.canPop()
        } catch (error: Throwable) {
            recordCallbackFailure(PixelNavigationAction.Pop, outgoing, error)
            return false
        }
        if (!permitsPop) return false
        val incoming = routeEntries[routeEntries.lastIndex - 1]
        predictiveBackTransition = PixelNavigatorPredictiveBackRecord(
            transition = PixelNavigatorTransitionRecord(
                id = nextTransitionId(),
                outgoingEntry = outgoing,
                incomingEntry = incoming,
                operation = PixelNavigatorOperation.Pop,
            ),
            progress = event.progress,
        )
        notifyListeners()
        return true
    }

    /** Updates only the visual pop preview; route lifecycle and results remain unchanged. */
    override fun onBackProgressed(event: PixelPredictiveBackEvent) {
        val preview = predictiveBackTransition ?: return
        if (preview.progress == event.progress) return
        predictiveBackTransition = preview.copy(progress = event.progress)
        notifyListeners()
    }

    /** Cancels the visual preview without changing the stack, lifecycle, or result channel. */
    override fun onBackCancelled() {
        val hadPreview = predictiveBackTransition != null
        predictiveBackTransition = null
        predictiveBackInterrupted = false
        if (hadPreview && !navigatorDisposed) notifyListeners()
    }

    /**
     * Commits a started gesture as typed cancellation, or handles an API 33 discrete callback.
     *
     * A gesture-controlled commit settles synchronously at its current presentation endpoint so
     * a second time-based pop animation cannot replay after the platform gesture completes.
     */
    override fun onBackCommitted(): Boolean {
        val preview = predictiveBackTransition
        predictiveBackTransition = null
        if (preview == null && predictiveBackInterrupted) {
            predictiveBackInterrupted = false
            return false
        }
        predictiveBackInterrupted = false
        return popInternal(
            completion = PendingRouteCompletion.Cancel(PixelRouteCancellationReason.Back),
            expectedEntry = preview?.transition?.outgoingEntry,
            animated = preview == null,
        )
    }

    /** Handles a discrete system-back event with the same typed cancellation semantics. */
    override fun onBackInvoked(): Boolean = onBackCommitted()

    /**
 * 从 `PixelNavigator` 释放 `clear` 内容并收敛相关所有权。
 *
     * Clears every entry above root while retaining the root entry and its result channel.
     *
     * Legacy callbacks are delivered as `null` in existing bottom-to-top stack order.
     */
    public fun clear(animated: Boolean = true): Boolean {
        if (routeEntries.size <= 1) return false
        settleInterruptedTransition()
        val outgoing = currentEntry
        val root = routeEntries.first()
        val removed = routeEntries.drop(1)
        emitEvent(
            action = PixelNavigationAction.Clear,
            type = PixelNavigationEventType.Started,
            entryId = outgoing.id,
            fromEntryId = outgoing.id,
            toEntryId = root.id,
        )
        routeEntries.clear()
        routeEntries += root
        removed.forEach { entry ->
            invokeLifecycle(PixelNavigationAction.Clear, entry) {
                entry.beginRemovalExactlyOnce()
            }
            enqueueFinalization(
                entry,
                PendingRouteCompletion.Cancel(PixelRouteCancellationReason.Cleared),
            )
        }
        invokeLifecycle(PixelNavigationAction.Clear, root) {
            root.enterExactlyOnce()
        }
        activeTransition = if (animated) {
            startTransition(outgoing, root, PixelNavigatorOperation.Pop)
        } else {
            finalizePendingEntries()
            null
        }
        emitEvent(
            action = PixelNavigationAction.Clear,
            type = PixelNavigationEventType.Completed,
            entryId = outgoing.id,
            fromEntryId = outgoing.id,
            toEntryId = root.id,
        )
        notifyListeners()
        return true
    }

    /** 执行 `PixelNavigator` 的 `popToRoot` 路由操作并保持结果恰好一次。
 *
 * Legacy alias for [clear] retained with its established `Unit` return type.
 */
    public fun popToRoot(animated: Boolean = true) {
        clear(animated)
    }

    /**
 * 执行 `PixelNavigator` 的 `replace` 路由操作并保持结果恰好一次。
 *
     * Replaces the active legacy route while transferring its legacy stack-slot callback.
     *
     * Typed entries replaced through this compatibility overload are cancelled instead.
     */
    public fun replace(route: PixelRoute, animated: Boolean = true) {
        ensureUsable(PixelNavigationAction.Replace)
        settleInterruptedTransition()
        val outgoing = currentEntry
        val transferredCallback = takeLegacyCallback(outgoing)
        val incoming = createLegacyEntry(route, transferredCallback)
        replaceEntryInStack(
            outgoing = outgoing,
            incoming = incoming,
            outgoingCompletion = if (transferredCallback == null && outgoing.legacyRoute == null) {
                PendingRouteCompletion.Cancel(PixelRouteCancellationReason.Replaced)
            } else {
                PendingRouteCompletion.None
            },
            animated = animated,
        )
    }

    /** 执行 `PixelNavigator` 的 `replace` 路由操作并保持结果恰好一次。
 *
 * Replaces the active entry with an independent typed [request].
 */
    public fun <A : Any, R> replace(
        request: PixelRouteRequest<A, R>,
        animated: Boolean = true,
    ): PixelRouteEntry<A, R> {
        ensureUsable(PixelNavigationAction.Replace)
        return checkNotNull(
            replaceTypedInternal(currentEntry, request, onOutcome = null, animated = animated),
        )
    }

    /** 执行 `PixelNavigator` 的 `replace` 路由操作并保持结果恰好一次。
 *
 * Replaces the active entry and observes the replacement entry's typed outcome.
 */
    public fun <A : Any, R> replace(
        request: PixelRouteRequest<A, R>,
        onOutcome: (PixelRouteOutcome<R>) -> Unit,
        animated: Boolean = true,
    ): PixelRouteEntry<A, R> {
        ensureUsable(PixelNavigationAction.Replace)
        return checkNotNull(replaceTypedInternal(currentEntry, request, onOutcome, animated))
    }

    /** 执行 `PixelNavigator` 的 `persistentSnapshot` 公开行为；具体参数、返回和副作用见下文。
 *
 * Encodes the complete typed entry stack through the explicit [registry] allowlist.
 */
    public fun persistentSnapshot(
        registry: PixelRouteSnapshotRegistry,
        codec: PixelNavigatorSnapshotCodec = PixelNavigatorSnapshotCodec(),
    ): PixelNavigatorSnapshotEncodeResult {
        if (!ensureUsable(PixelNavigationAction.Restore, throwOnFailure = false)) {
            return PixelNavigatorSnapshotEncodeResult.Rejected(
                PixelNavigatorSnapshotFailure(
                    reason = PixelNavigatorSnapshotFailureReason.InvalidStack,
                    message = "PixelNavigatorState is already disposed",
                ),
            )
        }
        return codec.encode(routeEntries, registry)
    }

    /**
 * 执行 `PixelNavigator` 的 `savePersistentSnapshotToBundle` 公开行为；具体参数、返回和副作用见下文。
 *
     * Encodes and writes the complete typed entry stack to Android [outState].
     *
     * A rejected encode removes any stale value already stored under [key], preventing a previous
     * valid snapshot from silently replacing a newer but non-persistable stack after recreation.
     */
    public fun savePersistentSnapshotToBundle(
        outState: Bundle,
        registry: PixelRouteSnapshotRegistry,
        codec: PixelNavigatorSnapshotCodec = PixelNavigatorSnapshotCodec(),
        key: String = PixelNavigatorPersistentSnapshotBundleKey,
    ): PixelNavigatorSnapshotEncodeResult {
        require(key.isNotBlank()) { "Persistent Navigator snapshot Bundle key must not be blank" }
        val result = persistentSnapshot(registry = registry, codec = codec)
        when (result) {
            is PixelNavigatorSnapshotEncodeResult.Encoded -> result.saveToBundle(outState, key)
            is PixelNavigatorSnapshotEncodeResult.Rejected -> outState.remove(key)
        }
        return result
    }

    /**
 * 执行 `PixelNavigator` 的 `restore` 公开行为；具体参数、返回和副作用见下文。
 *
     * Atomically installs one decoded [plan], preserving entry IDs and local state.
     *
     * A plan is one-shot because its detached state buckets must never be mounted by two
     * Navigators. Reusing a consumed plan returns `false` without mutating this stack.
     */
    public fun restore(plan: PixelNavigatorRestorePlan): Boolean {
        if (!ensureUsable(PixelNavigationAction.Restore, throwOnFailure = false)) return false
        val restoredEntries = plan.claimEntries(entryOwner)
            ?: return reject(
                action = PixelNavigationAction.Restore,
                reason = PixelNavigationFailureReason.InvalidStack,
                message = "PixelNavigatorRestorePlan has already been consumed",
            )
        if (
            restoredEntries.isEmpty() ||
            restoredEntries.last().id != plan.currentEntryId ||
            restoredEntries.map { entry -> entry.id }.toSet().size != restoredEntries.size
        ) {
            restoredEntries.forEach { entry -> entry.disposeExactlyOnce() }
            return reject(
                action = PixelNavigationAction.Restore,
                reason = PixelNavigationFailureReason.InvalidStack,
                message = "PixelNavigatorRestorePlan does not describe one valid foreground stack",
            )
        }
        replacePersistentRouteStack(restoredEntries)
        return true
    }

    /** 执行 `PixelNavigator` 的 `restorePersistentSnapshot` 公开行为；具体参数、返回和副作用见下文。
 *
 * Decodes [bytes] and installs the stack only when the complete snapshot is valid.
 */
    public fun restorePersistentSnapshot(
        bytes: ByteArray,
        registry: PixelRouteSnapshotRegistry,
        codec: PixelNavigatorSnapshotCodec = PixelNavigatorSnapshotCodec(),
    ): PixelNavigatorSnapshotDecodeResult {
        val decoded = codec.decode(bytes = bytes, registry = registry)
        if (decoded is PixelNavigatorSnapshotDecodeResult.Decoded && !restore(decoded.plan)) {
            return PixelNavigatorSnapshotDecodeResult.Rejected(
                PixelNavigatorSnapshotFailure(
                    reason = PixelNavigatorSnapshotFailureReason.InvalidStack,
                    message = "Decoded Navigator restoration plan could not be installed",
                ),
            )
        }
        return decoded
    }

    /**
 * 执行 `PixelNavigator` 的 `restorePersistentSnapshotFromBundle` 公开行为；具体参数、返回和副作用见下文。
 *
     * Restores versioned bytes from [savedInstanceState] when present.
     *
     * `null` means no snapshot was stored. A non-null rejected result leaves the current typed
     * root and stack unchanged.
     */
    public fun restorePersistentSnapshotFromBundle(
        savedInstanceState: Bundle?,
        registry: PixelRouteSnapshotRegistry,
        codec: PixelNavigatorSnapshotCodec = PixelNavigatorSnapshotCodec(),
        key: String = PixelNavigatorPersistentSnapshotBundleKey,
    ): PixelNavigatorSnapshotDecodeResult? {
        val bytes = savedInstanceState?.getPixelNavigatorPersistentSnapshotBytes(key) ?: return null
        return restorePersistentSnapshot(bytes = bytes, registry = registry, codec = codec)
    }

    /** 执行 `PixelNavigator` 的 `snapshot` 公开行为；具体参数、返回和副作用见下文。
 *
 * Captures the legacy route-name snapshot format used until M2-2.
 */
    public fun snapshot(): PixelNavigatorSnapshot {
        return PixelNavigatorSnapshot(routeNames = routeEntries.map { entry -> routeView(entry).name })
    }

    /** 执行 `PixelNavigator` 的 `saveToBundle` 公开行为；具体参数、返回和副作用见下文。
 *
 * Saves the legacy route-name snapshot into [outState].
 */
    public fun saveToBundle(
        outState: Bundle,
        key: String = PixelNavigatorBundleKey,
    ) {
        snapshot().saveToBundle(outState, key)
    }

    /** 执行 `PixelNavigator` 的 `restore` 公开行为；具体参数、返回和副作用见下文。
 *
 * Restores a legacy route-name snapshot, allocating a fresh entry for every occurrence.
 */
    public fun restore(
        snapshot: PixelNavigatorSnapshot,
        routeRegistry: Map<String, PixelRoute>,
    ) {
        if (snapshot.routeNames.isEmpty()) return
        val restored = snapshot.routeNames.map { name ->
            routeRegistry[name] ?: run {
                reject(
                    action = PixelNavigationAction.Restore,
                    reason = PixelNavigationFailureReason.UnknownDestination,
                    message = "PixelNavigator.restore() missing route '$name' in routeRegistry",
                    destinationId = name,
                )
                error("PixelNavigator.restore() missing route '$name' in routeRegistry")
            }
        }
        replaceRouteStack(restored, animated = false, action = PixelNavigationAction.Restore)
    }

    /** 执行 `PixelNavigator` 的 `restoreFromBundle` 公开行为；具体参数、返回和副作用见下文。
 *
 * Restores a legacy snapshot from [savedInstanceState] when one exists.
 */
    public fun restoreFromBundle(
        savedInstanceState: Bundle?,
        routeRegistry: Map<String, PixelRoute>,
        key: String = PixelNavigatorBundleKey,
    ): Boolean {
        val snapshot = savedInstanceState?.getPixelNavigatorSnapshot(key) ?: return false
        restore(snapshot, routeRegistry)
        return true
    }

    /** 执行 `PixelNavigator` 的 `handleDeepLink` 公开行为；具体参数、返回和副作用见下文。
 *
 * Parses [uri] and applies the legacy deep-link resolver.
 */
    public fun handleDeepLink(
        uri: String,
        resolver: PixelDeepLinkResolver,
        animated: Boolean = true,
    ): Boolean {
        return handleDeepLink(PixelDeepLink.parse(uri), resolver, animated)
    }

    /** 执行 `PixelNavigator` 的 `handleDeepLink` 公开行为；具体参数、返回和副作用见下文。
 *
 * Resolves [link] to a fresh legacy entry stack.
 */
    public fun handleDeepLink(
        link: PixelDeepLink,
        resolver: PixelDeepLinkResolver,
        animated: Boolean = true,
    ): Boolean {
        val resolved = resolver.resolve(link) ?: return reject(
            action = PixelNavigationAction.DeepLink,
            reason = PixelNavigationFailureReason.UnknownDestination,
            message = "No PixelDeepLinkResolver destination matched '${link.rawUri}'",
        )
        if (resolved.isEmpty()) {
            reject(
                action = PixelNavigationAction.DeepLink,
                reason = PixelNavigationFailureReason.InvalidStack,
                message = "PixelDeepLinkResolver returned an empty route stack for '${link.rawUri}'",
            )
            throw IllegalArgumentException(
                "PixelDeepLinkResolver returned an empty route stack for '${link.rawUri}'",
            )
        }
        replaceRouteStack(resolved, animated, PixelNavigationAction.DeepLink)
        return true
    }

    /** Applies one already validated typed deep-link request under the DeepLink observer action. */
    internal fun <A : Any, R> navigateTypedDeepLink(
        request: PixelRouteRequest<A, R>,
        mode: PixelTypedDeepLinkNavigationMode,
    ): PixelRouteEntry<A, R> {
        return when (mode) {
            PixelTypedDeepLinkNavigationMode.Push -> pushTypedInternal(
                request = request,
                onOutcome = null,
                action = PixelNavigationAction.DeepLink,
            )
            PixelTypedDeepLinkNavigationMode.Replace -> checkNotNull(
                replaceTypedInternal(
                    outgoing = currentEntry,
                    request = request,
                    onOutcome = null,
                    animated = true,
                    action = PixelNavigationAction.DeepLink,
                ),
            )
        }
    }

    /** Completes the active transition and finalizes every entry removed by it. */
    internal fun completeTransition(id: Long) {
        if (activeTransition?.id != id) return
        activeTransition = null
        finalizePendingEntries()
        if (!navigatorDisposed) notifyListeners()
    }

    /**
     * Returns entry subtrees that must remain mounted for state retention or transition cleanup.
     *
     * Inactive entries with `maintainState=false` are deliberately omitted after their transition
     * settles; pending outgoing entries remain mounted until their terminal disposal phase.
     */
    internal fun presentationEntries(): List<PixelRouteEntry<*, *>> {
        val predictiveTransition = predictiveBackTransition?.transition
        val transitionEntries = setOfNotNull(
            activeTransition?.outgoingEntry,
            activeTransition?.incomingEntry,
            predictiveTransition?.outgoingEntry,
            predictiveTransition?.incomingEntry,
        )
        val retainedStackEntries = routeEntries.filter { entry ->
            entry.maintainState || entry === routeEntries.lastOrNull() || entry in transitionEntries
        }
        return (retainedStackEntries + pendingFinalizations.values.map { pending -> pending.entry })
            .distinctBy { entry -> entry.id }
    }

    /** Disposes all stack and transition entries exactly once when the widget host unmounts. */
    internal fun disposeNavigator() {
        if (navigatorDisposed) return
        val fromEntryId = routeEntries.lastOrNull()?.id
        emitEvent(
            action = PixelNavigationAction.Dispose,
            type = PixelNavigationEventType.Started,
            fromEntryId = fromEntryId,
        )
        navigatorDisposed = true
        activeTransition = null
        predictiveBackTransition = null
        predictiveBackInterrupted = false
        routeEntries.asReversed().forEach { entry ->
            invokeLifecycle(PixelNavigationAction.Dispose, entry) {
                entry.beginRemovalExactlyOnce()
            }
            enqueueFinalization(
                entry,
                PendingRouteCompletion.Cancel(PixelRouteCancellationReason.NavigatorDisposed),
            )
        }
        routeEntries.clear()
        finalizePendingEntries()
        emitEvent(
            action = PixelNavigationAction.Dispose,
            type = PixelNavigationEventType.Completed,
            fromEntryId = fromEntryId,
        )
        navigationObservers.clear()
        typedRouteViews.clear()
        legacyDestinations.clear()
    }

    /** Completes a typed scope only while its exact entry is foreground and poppable. */
    private fun completeOwnedEntry(entry: PixelRouteEntry<*, *>, result: Any?): Boolean {
        return popInternal(
            completion = PendingRouteCompletion.Success(result),
            expectedEntry = entry,
        )
    }

    /** Cancels a typed scope without routing cancellation through a nullable value. */
    private fun cancelOwnedEntry(
        entry: PixelRouteEntry<*, *>,
        reason: PixelRouteCancellationReason,
    ): Boolean {
        return cancel(entry, reason)
    }

    /** Replaces a non-stale typed scope with a new independently typed entry. */
    private fun <A : Any, R> replaceOwnedEntry(
        entry: PixelRouteEntry<*, *>,
        request: PixelRouteRequest<A, R>,
        onOutcome: ((PixelRouteOutcome<R>) -> Unit)?,
    ): PixelRouteEntry<A, R>? {
        return replaceTypedInternal(entry, request, onOutcome, animated = true)
    }

    /** Pushes one legacy route after adapting nullable cancellation semantics. */
    private fun pushLegacyInternal(
        route: PixelRoute,
        onResult: ((Any?) -> Unit)?,
    ) {
        val onOutcome = onResult?.let { callback ->
            { outcome: PixelRouteOutcome<Any?> ->
                callback(
                    when (outcome) {
                        is PixelRouteOutcome.Success -> outcome.value
                        is PixelRouteOutcome.Cancelled -> null
                    },
                )
            }
        }
        pushEntry(createLegacyEntry(route, onOutcome))
    }

    /** Pushes one typed request after validating navigator liveness. */
    private fun <A : Any, R> pushTypedInternal(
        request: PixelRouteRequest<A, R>,
        onOutcome: ((PixelRouteOutcome<R>) -> Unit)?,
        action: PixelNavigationAction = PixelNavigationAction.Push,
    ): PixelRouteEntry<A, R> {
        ensureUsable(action)
        val entry = PixelRouteEntry.create(
            id = nextEntryId(),
            destination = request.destination,
            arguments = request.arguments,
            owner = entryOwner,
            onOutcome = onOutcome,
        )
        pushEntry(entry, action)
        return entry
    }

    /** Commits one freshly created entry as the foreground stack entry. */
    private fun pushEntry(
        entry: PixelRouteEntry<*, *>,
        action: PixelNavigationAction = PixelNavigationAction.Push,
    ) {
        ensureUsable(action)
        settleInterruptedTransition()
        val outgoing = currentEntry
        emitEvent(
            action = action,
            type = PixelNavigationEventType.Started,
            entryId = entry.id,
            fromEntryId = outgoing.id,
            toEntryId = entry.id,
        )
        routeEntries += entry
        invokeLifecycle(action, outgoing) {
            outgoing.exitExactlyOnce()
        }
        invokeLifecycle(action, entry) {
            entry.enterExactlyOnce()
        }
        activeTransition = startTransition(outgoing, entry, PixelNavigatorOperation.Push)
        emitEvent(
            action = action,
            type = PixelNavigationEventType.Completed,
            entryId = entry.id,
            fromEntryId = outgoing.id,
            toEntryId = entry.id,
        )
        notifyListeners()
    }

    /** Pops or completes the foreground entry using [completion]. */
    private fun popInternal(
        completion: PendingRouteCompletion,
        expectedEntry: PixelRouteEntry<*, *>?,
        animated: Boolean = true,
    ): Boolean {
        if (!ensureUsable(PixelNavigationAction.Pop, throwOnFailure = false)) return false
        settleInterruptedTransition()
        if (!canPop) {
            return reject(
                action = PixelNavigationAction.Pop,
                reason = PixelNavigationFailureReason.CannotPopRoot,
                message = "PixelNavigator.pop() cannot remove the root entry",
                entryId = routeEntries.lastOrNull()?.id,
            )
        }
        val outgoing = currentEntry
        if (expectedEntry != null && outgoing !== expectedEntry) {
            return reject(
                action = PixelNavigationAction.Pop,
                reason = PixelNavigationFailureReason.EntryNotActive,
                message = "Typed route completion requires the active entry",
                entryId = expectedEntry.id,
                destinationId = expectedEntry.destination.id,
            )
        }
        val permitsPop = try {
            outgoing.canPop()
        } catch (error: Throwable) {
            recordCallbackFailure(PixelNavigationAction.Pop, outgoing, error)
            return false
        }
        if (!permitsPop) {
            return reject(
                action = PixelNavigationAction.Pop,
                reason = PixelNavigationFailureReason.PopRejected,
                message = "Destination '${outgoing.destination.id}' rejected the pop request",
                entryId = outgoing.id,
                destinationId = outgoing.destination.id,
            )
        }
        val incoming = routeEntries[routeEntries.lastIndex - 1]
        emitEvent(
            action = PixelNavigationAction.Pop,
            type = PixelNavigationEventType.Started,
            entryId = outgoing.id,
            fromEntryId = outgoing.id,
            toEntryId = incoming.id,
        )
        routeEntries.removeAt(routeEntries.lastIndex)
        invokeLifecycle(PixelNavigationAction.Pop, outgoing) {
            outgoing.beginRemovalExactlyOnce()
        }
        invokeLifecycle(PixelNavigationAction.Pop, incoming) {
            incoming.enterExactlyOnce()
        }
        enqueueFinalization(outgoing, completion)
        activeTransition = if (animated) {
            startTransition(outgoing, incoming, PixelNavigatorOperation.Pop)
        } else {
            finalizePendingEntries()
            null
        }
        emitEvent(
            action = PixelNavigationAction.Pop,
            type = PixelNavigationEventType.Completed,
            entryId = outgoing.id,
            fromEntryId = outgoing.id,
            toEntryId = incoming.id,
        )
        notifyListeners()
        return true
    }

    /** Removes one active or inactive entry while preserving a non-empty stack. */
    private fun removeEntryInternal(
        entry: PixelRouteEntry<*, *>,
        reason: PixelRouteCancellationReason,
        action: PixelNavigationAction,
        animated: Boolean,
    ): Boolean {
        if (!ensureUsable(action, throwOnFailure = false)) return false
        settleInterruptedTransition()
        val index = routeEntries.indexOfFirst { candidate -> candidate === entry }
        if (index < 0) {
            return reject(
                action = action,
                reason = PixelNavigationFailureReason.EntryNotFound,
                message = "Entry ${entry.id} no longer belongs to this navigator",
                entryId = entry.id,
                destinationId = entry.destination.id,
            )
        }
        if (routeEntries.size == 1) {
            return reject(
                action = action,
                reason = PixelNavigationFailureReason.CannotPopRoot,
                message = "PixelNavigator cannot remove its final root entry",
                entryId = entry.id,
                destinationId = entry.destination.id,
            )
        }
        val wasCurrent = index == routeEntries.lastIndex
        val previousCurrent = currentEntry
        routeEntries.removeAt(index)
        val nextCurrent = currentEntry
        emitEvent(
            action = action,
            type = PixelNavigationEventType.Started,
            entryId = entry.id,
            fromEntryId = previousCurrent.id,
            toEntryId = nextCurrent.id,
        )
        invokeLifecycle(action, entry) {
            entry.beginRemovalExactlyOnce()
        }
        if (wasCurrent) {
            invokeLifecycle(action, nextCurrent) {
                nextCurrent.enterExactlyOnce()
            }
        }
        enqueueFinalization(entry, PendingRouteCompletion.Cancel(reason))
        activeTransition = if (wasCurrent && animated) {
            startTransition(entry, nextCurrent, PixelNavigatorOperation.Pop)
        } else {
            finalizePendingEntries()
            null
        }
        emitEvent(
            action = action,
            type = PixelNavigationEventType.Completed,
            entryId = entry.id,
            fromEntryId = previousCurrent.id,
            toEntryId = nextCurrent.id,
        )
        notifyListeners()
        return true
    }

    /** Replaces a current typed entry with an independent typed result channel. */
    private fun <A : Any, R> replaceTypedInternal(
        outgoing: PixelRouteEntry<*, *>,
        request: PixelRouteRequest<A, R>,
        onOutcome: ((PixelRouteOutcome<R>) -> Unit)?,
        animated: Boolean,
        action: PixelNavigationAction = PixelNavigationAction.Replace,
    ): PixelRouteEntry<A, R>? {
        if (!ensureUsable(action, throwOnFailure = false)) return null
        settleInterruptedTransition()
        if (routeEntries.lastOrNull() !== outgoing) {
            reject(
                action = action,
                reason = PixelNavigationFailureReason.EntryNotActive,
                message = "Typed replacement requires the active entry",
                entryId = outgoing.id,
                destinationId = outgoing.destination.id,
            )
            return null
        }
        val incoming = PixelRouteEntry.create(
            id = nextEntryId(),
            destination = request.destination,
            arguments = request.arguments,
            owner = entryOwner,
            onOutcome = onOutcome,
        )
        replaceEntryInStack(
            outgoing = outgoing,
            incoming = incoming,
            outgoingCompletion = PendingRouteCompletion.Cancel(PixelRouteCancellationReason.Replaced),
            animated = animated,
            action = action,
        )
        return incoming
    }

    /** Commits one replacement and defers old-entry disposal until transition settlement. */
    private fun replaceEntryInStack(
        outgoing: PixelRouteEntry<*, *>,
        incoming: PixelRouteEntry<*, *>,
        outgoingCompletion: PendingRouteCompletion,
        animated: Boolean,
        action: PixelNavigationAction = PixelNavigationAction.Replace,
    ) {
        emitEvent(
            action = action,
            type = PixelNavigationEventType.Started,
            entryId = incoming.id,
            fromEntryId = outgoing.id,
            toEntryId = incoming.id,
        )
        routeEntries[routeEntries.lastIndex] = incoming
        invokeLifecycle(action, outgoing) {
            outgoing.beginRemovalExactlyOnce()
        }
        invokeLifecycle(action, incoming) {
            incoming.enterExactlyOnce()
        }
        enqueueFinalization(outgoing, outgoingCompletion)
        activeTransition = if (animated) {
            startTransition(outgoing, incoming, PixelNavigatorOperation.Replace)
        } else {
            finalizePendingEntries()
            null
        }
        emitEvent(
            action = action,
            type = PixelNavigationEventType.Completed,
            entryId = incoming.id,
            fromEntryId = outgoing.id,
            toEntryId = incoming.id,
        )
        notifyListeners()
    }

    /** Replaces the whole legacy stack with fresh entries and isolated state buckets. */
    private fun replaceRouteStack(
        restored: List<PixelRoute>,
        animated: Boolean,
        action: PixelNavigationAction,
    ) {
        require(restored.isNotEmpty()) { "PixelNavigator route stack must not be empty" }
        ensureUsable(action)
        settleInterruptedTransition()
        val oldEntries = routeEntries.toList()
        val outgoing = oldEntries.last()
        val newEntries = restored.map { route -> createLegacyEntry(route, onOutcome = null) }
        val incoming = newEntries.last()
        emitEvent(
            action = action,
            type = PixelNavigationEventType.Started,
            fromEntryId = outgoing.id,
            toEntryId = incoming.id,
        )
        routeEntries.clear()
        routeEntries += newEntries
        newEntries.dropLast(1).forEach { entry -> entry.initializeInactiveExactlyOnce() }
        oldEntries.forEach { entry ->
            invokeLifecycle(action, entry) {
                entry.beginRemovalExactlyOnce()
            }
            enqueueFinalization(
                entry,
                PendingRouteCompletion.Cancel(PixelRouteCancellationReason.StackReset),
            )
        }
        invokeLifecycle(action, incoming) {
            incoming.enterExactlyOnce()
        }
        activeTransition = if (animated) {
            startTransition(outgoing, incoming, PixelNavigatorOperation.Replace)
        } else {
            finalizePendingEntries()
            null
        }
        emitEvent(
            action = action,
            type = PixelNavigationEventType.Completed,
            fromEntryId = outgoing.id,
            toEntryId = incoming.id,
        )
        notifyListeners()
    }

    /**
     * Replaces the whole stack with prevalidated persistent entries without allocating new IDs.
     *
     * Snapshot decoding and state restoration happen before this method is entered. The live
     * stack therefore changes only after every destination, argument, and local-state payload has
     * been accepted. Restored IDs also advance the monotonic allocator before future pushes.
     */
    private fun replacePersistentRouteStack(restored: List<PixelRouteEntry<*, *>>) {
        require(restored.isNotEmpty()) { "PixelNavigator persistent route stack must not be empty" }
        settleInterruptedTransition()
        val oldEntries = routeEntries.toList()
        val outgoing = oldEntries.last()
        val incoming = restored.last()
        emitEvent(
            action = PixelNavigationAction.Restore,
            type = PixelNavigationEventType.Started,
            fromEntryId = outgoing.id,
            toEntryId = incoming.id,
        )
        routeEntries.clear()
        routeEntries += restored
        nextEntryIdValue = maxOf(
            nextEntryIdValue,
            restored.maxOf { entry -> entry.id.value },
        )
        restored.dropLast(1).forEach { entry -> entry.initializeInactiveExactlyOnce() }
        oldEntries.forEach { entry ->
            invokeLifecycle(PixelNavigationAction.Restore, entry) {
                entry.beginRemovalExactlyOnce()
            }
            enqueueFinalization(
                entry,
                PendingRouteCompletion.Cancel(PixelRouteCancellationReason.StackReset),
            )
        }
        invokeLifecycle(PixelNavigationAction.Restore, incoming) {
            incoming.enterExactlyOnce()
        }
        activeTransition = null
        finalizePendingEntries()
        emitEvent(
            action = PixelNavigationAction.Restore,
            type = PixelNavigationEventType.Completed,
            fromEntryId = outgoing.id,
            toEntryId = incoming.id,
        )
        notifyListeners()
    }

    /** Creates one typed root entry while preserving its destination and argument types. */
    @Suppress("UNCHECKED_CAST")
    private fun createTypedInitialEntry(
        request: PixelRouteRequest<*, *>,
    ): PixelRouteEntry<*, *> {
        return createTypedInitialEntry(
            request = request as PixelRouteRequest<Any, Any?>,
            destination = request.destination as PixelRouteDestination<Any, Any?>,
        )
    }

    /** Creates the concrete typed root after the request/destination pair has been erased safely. */
    private fun <A : Any, R> createTypedInitialEntry(
        request: PixelRouteRequest<A, R>,
        destination: PixelRouteDestination<A, R>,
    ): PixelRouteEntry<A, R> {
        return PixelRouteEntry.create(
            id = nextEntryId(),
            destination = destination,
            arguments = request.arguments,
            owner = entryOwner,
        )
    }

    /** Creates one independent compatibility entry for [route]. */
    private fun createLegacyEntry(
        route: PixelRoute,
        onOutcome: ((PixelRouteOutcome<Any?>) -> Unit)?,
    ): PixelRouteEntry<Unit, Any?> {
        val destination = legacyDestinations.getOrPut(route) {
            LegacyPixelRouteDestination(route)
        }
        return PixelRouteEntry.create(
            id = nextEntryId(),
            destination = destination,
            arguments = Unit,
            owner = entryOwner,
            onOutcome = onOutcome,
            legacyRoute = route,
        )
    }

    /** Returns the original legacy route or a stable projection for one typed entry. */
    private fun routeView(entry: PixelRouteEntry<*, *>): PixelRoute {
        entry.legacyRoute?.let { route -> return route }
        return typedRouteViews.getOrPut(entry.id) {
            PixelRoute(
                name = entry.destination.id,
                builder = { context -> entry.build(context) },
                transition = entry.routeTransition,
                canPop = { entry.canPop() },
                transitionBuilder = entry.routeTransitionBuilder,
            )
        }
    }

    /** Transfers only legacy result callbacks; typed channels remain isolated. */
    @Suppress("UNCHECKED_CAST")
    private fun takeLegacyCallback(
        entry: PixelRouteEntry<*, *>,
    ): ((PixelRouteOutcome<Any?>) -> Unit)? {
        if (entry.legacyRoute == null) return null
        return (entry as PixelRouteEntry<Unit, Any?>).takeLegacyResultCallback()
    }

    /** Starts one transition identified entirely by entry IDs. */
    private fun startTransition(
        outgoingEntry: PixelRouteEntry<*, *>,
        incomingEntry: PixelRouteEntry<*, *>,
        operation: PixelNavigatorOperation,
    ): PixelNavigatorTransitionRecord {
        return PixelNavigatorTransitionRecord(
            id = nextTransitionId(),
            outgoingEntry = outgoingEntry,
            incomingEntry = incomingEntry,
            operation = operation,
        ).also { transition -> activeTransition = transition }
    }

    /** Settles any superseded transition before another stack mutation starts. */
    private fun settleInterruptedTransition() {
        interruptPredictiveBackPreview()
        if (activeTransition == null) return
        activeTransition = null
        finalizePendingEntries()
    }

    /** Invalidates a gesture preview while remembering that its eventual commit is stale. */
    private fun interruptPredictiveBackPreview() {
        if (predictiveBackTransition == null) return
        predictiveBackTransition = null
        predictiveBackInterrupted = true
    }

    /** Adds one terminal action for an entry, preserving deterministic insertion order. */
    private fun enqueueFinalization(
        entry: PixelRouteEntry<*, *>,
        completion: PendingRouteCompletion,
    ) {
        pendingFinalizations.putIfAbsent(
            entry.id,
            PendingRouteEntryFinalization(entry, completion),
        )
    }

    /** Disposes all pending entries before resolving and delivering their outcomes. */
    private fun finalizePendingEntries() {
        if (pendingFinalizations.isEmpty()) return
        val finalizations = pendingFinalizations.values.toList()
        pendingFinalizations.clear()
        finalizations.forEach { pending ->
            invokeLifecycle(PixelNavigationAction.Dispose, pending.entry) {
                pending.entry.disposeExactlyOnce()
            }
            typedRouteViews.remove(pending.entry.id)
        }
        finalizations.forEach { pending ->
            resolveAndDeliver(pending)
        }
    }

    /** Resolves and drains one erased result channel exactly once. */
    @Suppress("UNCHECKED_CAST")
    private fun resolveAndDeliver(pending: PendingRouteEntryFinalization) {
        val entry = pending.entry as PixelRouteEntry<Any, Any?>
        val action = when (val completion = pending.completion) {
            is PendingRouteCompletion.Success -> {
                entry.resolveSuccessExactlyOnce(completion.value)
                PixelNavigationAction.Result
            }
            is PendingRouteCompletion.Cancel -> {
                entry.cancelResultExactlyOnce(completion.reason)
                PixelNavigationAction.Cancel
            }
            PendingRouteCompletion.None -> return
        }
        try {
            entry.drainResultDelivery()
        } catch (error: Throwable) {
            val failure = PixelNavigationFailure(
                action = action,
                reason = PixelNavigationFailureReason.ResultCallbackFailed,
                message = "Result callback for entry ${entry.id} failed: ${error.message.orEmpty()}",
                entryId = entry.id,
                destinationId = entry.destination.id,
            )
            latestFailure = failure
            emitEvent(action, PixelNavigationEventType.Failed, entryId = entry.id, failure = failure)
        }
        emitEvent(action, PixelNavigationEventType.Completed, entryId = entry.id)
    }

    /** Runs a lifecycle mutation without letting user callback failures break cleanup. */
    private inline fun invokeLifecycle(
        action: PixelNavigationAction,
        entry: PixelRouteEntry<*, *>,
        callback: () -> Unit,
    ) {
        try {
            callback()
        } catch (error: Throwable) {
            recordCallbackFailure(action, entry, error)
        }
    }

    /** Records a lifecycle callback failure after its state transition was committed. */
    private fun recordCallbackFailure(
        action: PixelNavigationAction,
        entry: PixelRouteEntry<*, *>,
        error: Throwable,
    ) {
        val failure = PixelNavigationFailure(
            action = action,
            reason = PixelNavigationFailureReason.LifecycleCallbackFailed,
            message = "Lifecycle callback for entry ${entry.id} failed: ${error.message.orEmpty()}",
            entryId = entry.id,
            destinationId = entry.destination.id,
        )
        latestFailure = failure
        emitEvent(action, PixelNavigationEventType.Failed, entryId = entry.id, failure = failure)
    }

    /** Rejects one action, stores the failure, and notifies observers without mutating the stack. */
    private fun reject(
        action: PixelNavigationAction,
        reason: PixelNavigationFailureReason,
        message: String,
        entryId: PixelRouteEntryId? = null,
        destinationId: String? = null,
    ): Boolean {
        val failure = PixelNavigationFailure(
            action = action,
            reason = reason,
            message = message,
            entryId = entryId,
            destinationId = destinationId,
        )
        latestFailure = failure
        emitEvent(
            action = action,
            type = PixelNavigationEventType.Failed,
            entryId = entryId,
            failure = failure,
        )
        return false
    }

    /** Validates navigator liveness for an action that may choose to throw. */
    private fun ensureUsable(
        action: PixelNavigationAction,
        throwOnFailure: Boolean = true,
    ): Boolean {
        if (!navigatorDisposed) return true
        reject(
            action = action,
            reason = PixelNavigationFailureReason.NavigatorDisposed,
            message = "PixelNavigatorState is already disposed",
        )
        if (throwOnFailure) error("PixelNavigatorState is already disposed")
        return false
    }

    /** Emits one ordered event while isolating every observer exception. */
    private fun emitEvent(
        action: PixelNavigationAction,
        type: PixelNavigationEventType,
        entryId: PixelRouteEntryId? = null,
        fromEntryId: PixelRouteEntryId? = null,
        toEntryId: PixelRouteEntryId? = null,
        failure: PixelNavigationFailure? = null,
    ) {
        val event = PixelNavigationEvent(
            sequence = ++nextEventSequenceValue,
            action = action,
            type = type,
            entryId = entryId,
            fromEntryId = fromEntryId,
            toEntryId = toEntryId,
            failure = failure,
        )
        navigationObservers.toList().forEach { observer ->
            try {
                observer.onNavigationEvent(event)
            } catch (error: Throwable) {
                latestFailure = PixelNavigationFailure(
                    action = action,
                    reason = PixelNavigationFailureReason.ObserverCallbackFailed,
                    message = "Navigation observer failed: ${error.message.orEmpty()}",
                    entryId = entryId,
                )
            }
        }
    }

    /** Allocates the next positive route-entry identity. */
    private fun nextEntryId(): PixelRouteEntryId = PixelRouteEntryId(++nextEntryIdValue)

    /** Allocates the next positive transition identity. */
    private fun nextTransitionId(): Long = ++nextTransitionIdValue
}

/** Compatibility destination wrapper for one reusable legacy [PixelRoute]. */
private class LegacyPixelRouteDestination(
    /** Legacy route definition adapted by this destination. */
    private val route: PixelRoute,
) : PixelRouteDestination<Unit, Any?>(route.name) {
    /** Legacy entries retain their route subtree and local bucket by default. */
    override val maintainState: Boolean = true

    /** Legacy built-in transition override. */
    override val transition: PixelRouteTransition? = route.transition

    /** Legacy custom transition override. */
    override val transitionBuilder: PixelRouteTransitionBuilder? = route.transitionBuilder

    /** Delegates the legacy pop guard for this concrete entry. */
    override fun canPop(entry: PixelRouteEntry<Unit, Any?>): Boolean {
        return route.canPop?.invoke() ?: true
    }

    /** Delegates legacy route activation. */
    override fun onEnter(entry: PixelRouteEntry<Unit, Any?>) {
        route.onEnter?.invoke()
    }

    /** Delegates legacy route deactivation. */
    override fun onExit(entry: PixelRouteEntry<Unit, Any?>) {
        route.onExit?.invoke()
    }

    /** Delegates legacy route terminal disposal. */
    override fun onDispose(entry: PixelRouteEntry<Unit, Any?>) {
        route.onDispose?.invoke()
    }

    /** Builds the legacy route with its original builder. */
    override fun build(
        context: BuildContext,
        scope: PixelRouteEntryScope<Unit, Any?>,
    ): Widget = route.builder(context)
}

/**
 * Marker builder that carries a typed root request through the unchanged legacy widget ABI.
 *
 * [PixelNavigatorWidgetState] consumes [request] before route building, so [invoke] is only a
 * defensive failure path if a future widget-state implementation forgets the typed-root marker.
 */
private class TypedInitialRouteBuilder(
    /** Typed destination request used to create the real root entry. */
    val request: PixelRouteRequest<*, *>,
) : (BuildContext) -> Widget {
    /** Rejects accidental use as an ordinary legacy route builder. */
    override fun invoke(context: BuildContext): Widget {
        error("TypedInitialRouteBuilder must be consumed by PixelNavigatorWidgetState")
    }
}

/** 定义 `PixelNavigator` 的路由栈操作与生命周期边界，失败不会留下部分提交状态。 */
public class PixelNavigator(
    /** 提供 `PixelNavigator` 当前管理的 `initialRoute` 内容。 */
    public val initialRoute: PixelRoute,
    /** 记录 `PixelNavigator` 的 `vsync` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val vsync: PixelTickerProvider,
    /** 控制 `PixelNavigator` 的 `transitionDuration` 时间参数，单位为声明约定的时间单位。 */
    public val transitionDuration: Duration = 200.milliseconds,
    /** 记录 `PixelNavigator` 的 `defaultTransition` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val defaultTransition: PixelRouteTransition = PixelRouteTransition.SlideHorizontal,
    override val key: Any? = null,
    /** 记录 `PixelNavigator` 的 `transitionBuilder` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val transitionBuilder: PixelRouteTransitionBuilder? = null,
) : StatefulWidget(key = key) {
    /** Internal observer used by composed navigation hosts without wrapping the consumer root. */
    internal var stateObserver: ((PixelNavigatorState) -> Unit)? = null

    /** Installs an internal state observer while preserving the public constructor ABI. */
    internal fun observeState(observer: (PixelNavigatorState) -> Unit): PixelNavigator {
        stateObserver = observer
        return this
    }

    override fun createState(): State<out StatefulWidget> = PixelNavigatorWidgetState()

    /** 集中提供 `PixelNavigator` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /**
 * 执行 `PixelNavigator` 的 `typed` 公开行为；具体参数、返回和副作用见下文。
 *
         * Creates a Navigator with a typed, persistable root entry.
         *
         * The returned widget remains the existing [PixelNavigator] JVM type and keeps its legacy
         * constructor ABI intact. The marker route is converted to [initialRequest] before the
         * first route subtree is built.
         */
        public fun <A : Any, R> typed(
            initialRequest: PixelRouteRequest<A, R>,
            vsync: PixelTickerProvider,
            transitionDuration: Duration = 200.milliseconds,
            defaultTransition: PixelRouteTransition = PixelRouteTransition.SlideHorizontal,
            key: Any? = null,
            transitionBuilder: PixelRouteTransitionBuilder? = null,
        ): PixelNavigator {
            val markerRoute = PixelRoute(
                name = initialRequest.destination.id,
                builder = TypedInitialRouteBuilder(initialRequest),
                transition = initialRequest.destination.transition,
                transitionBuilder = initialRequest.destination.transitionBuilder,
            )
            return PixelNavigator(
                initialRoute = markerRoute,
                vsync = vsync,
                transitionDuration = transitionDuration,
                defaultTransition = defaultTransition,
                key = key,
                transitionBuilder = transitionBuilder,
            )
        }

        /** 执行 `PixelNavigator` 的 `maybeOf` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest Navigator controller, or `null` outside a Navigator subtree.
 */
        public fun maybeOf(context: BuildContext): PixelNavigatorState? {
            return context.dependOnInheritedWidgetOfExactType<PixelNavigatorScope>()?.navigatorState
        }

        /** 执行 `PixelNavigator` 的 `of` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest Navigator controller or fails when no Navigator is mounted.
 */
        public fun of(context: BuildContext): PixelNavigatorState {
            return maybeOf(context) ?: error("PixelNavigator.of() called with a context that has no PixelNavigator")
        }
    }
}

private class PixelNavigatorWidgetState : State<PixelNavigator>() {
    /** Navigation controller exposed to descendants through [PixelNavigatorScope]. */
    private lateinit var navigatorState: PixelNavigatorState

    /** Animation controller for the currently active transition, if it is animated. */
    private var transitionController: PixelAnimationController? = null

    /** Curved progress view paired with [transitionController]. */
    private var transitionCurve: CurvedAnimation? = null

    /** Transition ID currently owned by [transitionController]. */
    private var animatedTransitionId: Long? = null

    /** Resolved motion value currently owned by [transitionController] for policy-change detection. */
    private var animatedMotion: NavigatorResolvedMotion? = null

    /** Built-in visual policy currently owned by [transitionController]. */
    private var animatedTransition: PixelRouteTransition? = null

    /** Custom builder currently owned by [transitionController], compared by identity. */
    private var animatedCustomBuilder: PixelRouteTransitionBuilder? = null

    /** Visual progress already completed before the current rebased controller segment. */
    private var animatedVisualStart: Float = 0f

    /** Stable presentation links that let custom builders paint one retained route subtree. */
    private val presentationLinks: MutableMap<PixelRouteEntryId, PixelRoutePresentationLink> =
        mutableMapOf()

    /** Creates the entry-backed state controller for the configured legacy root route. */
    override fun initState() {
        val typedInitialRequest =
            (widget.initialRoute.builder as? TypedInitialRouteBuilder)?.request
        navigatorState = if (typedInitialRequest != null) {
            PixelNavigatorState(typedInitialRequest)
        } else {
            PixelNavigatorState(widget.initialRoute)
        }
        widget.stateObserver?.invoke(navigatorState)
    }

    /** Notifies a newly supplied composed-host observer of the already retained controller. */
    override fun didUpdateWidget(oldWidget: PixelNavigator) {
        if (oldWidget.stateObserver !== widget.stateObserver) {
            widget.stateObserver?.invoke(navigatorState)
        }
    }

    /** Builds a stable retained entry stack and updates only its visual transition properties. */
    override fun build(context: BuildContext): Widget {
        context.watch(navigatorState)
        val predictiveBack = navigatorState.predictiveBackTransition
        val transitionRecord = predictiveBack?.transition ?: navigatorState.activeTransition
        val motionScope = PixelMotionScope.maybeOf(context)
        val motion = resolveMotion(context = context, scope = motionScope)
        val transition = transitionRecord?.let { record ->
            resolveTransition(transition = record, motion = motion)
        }
        val customBuilder = transitionRecord
            ?.let(::resolveTransitionBuilder)
            ?.takeUnless {
                // Consumer-defined spatial movement cannot be audited, so reduced motion falls
                // back to the built-in fade/none policy selected above.
                motion.settings.reduceMotion ||
                    motion.resolved.isImmediate ||
                    motion.resolved.transition == PixelMotionTransitionPreset.None
            }
        val progress = if (predictiveBack != null) {
            disposeTransitionController()
            predictiveBack.progress
        } else {
            transitionProgress(
                context = context,
                transitionRecord = transitionRecord,
                transition = transition,
                customBuilder = customBuilder,
                scope = motionScope,
                motion = motion,
            )
        }
        val presentationEntries = orderedPresentationEntries(transitionRecord)
        val liveEntryIds = presentationEntries.mapTo(mutableSetOf()) { entry -> entry.id }
        presentationLinks.keys.retainAll(liveEntryIds)
        val retainedChildren = presentationEntries.map { entry ->
            val visual = resolveEntryVisual(
                entry = entry,
                transitionRecord = transitionRecord,
                transition = transition,
                hasCustomBuilder = customBuilder != null,
                progress = progress,
            )
            Opacity(
                opacity = visual.opacity,
                child = PixelRouteFractionalTranslationWidget(
                    fractionX = visual.fractionX,
                    fractionY = visual.fractionY,
                    interactive = entry === navigatorState.currentEntry,
                    layoutEnabled = visual.isLogicallyPresented,
                    exposesSubtreeToWidgetCollection = visual.isLogicallyPresented,
                    presentationLink = presentationLinkFor(entry),
                    child = routeChild(entry, suffix = "retained"),
                    key = "route-entry-translate:${entry.id.value}",
                ),
                key = "route-entry-visual:${entry.id.value}",
            )
        }
        val customPresentation = if (transitionRecord != null && customBuilder != null) {
            val outgoing = PixelRoutePresentationProxyWidget(
                presentationLink = presentationLinkFor(transitionRecord.outgoingEntry),
                interactive = transitionRecord.outgoingEntry === navigatorState.currentEntry,
                key = "route-custom-proxy:${transitionRecord.id}:outgoing",
            )
            val incoming = PixelRoutePresentationProxyWidget(
                presentationLink = presentationLinkFor(transitionRecord.incomingEntry),
                interactive = transitionRecord.incomingEntry === navigatorState.currentEntry,
                key = "route-custom-proxy:${transitionRecord.id}:incoming",
            )
            Opacity(
                opacity = 1f,
                child = customBuilder.build(
                    progress = progress,
                    operation = transitionRecord.operation,
                    outgoing = outgoing,
                    incoming = incoming,
                ),
                key = "route-custom-presentation:${transitionRecord.id}",
            )
        } else {
            null
        }
        val child = Stack(
            children = retainedChildren + listOfNotNull(customPresentation),
            key = "navigator-entry-stack",
        )
        return PixelNavigatorScope(
            navigatorState = navigatorState,
            child = PixelPredictiveBackHandler(
                enabled = navigatorState.canPop,
                callback = navigatorState,
                child = child,
                key = "navigator-back",
            ),
            key = "navigator-scope",
        )
    }

    /** Terminates every remaining entry and pending result when this Navigator unmounts. */
    override fun dispose() {
        disposeTransitionController()
        presentationLinks.clear()
        navigatorState.disposeNavigator()
    }

    /** Resolves the built-in transition policy for one entry transition. */
    private fun resolveTransition(
        transition: PixelNavigatorTransitionRecord,
        motion: NavigatorResolvedMotion,
    ): PixelRouteTransition {
        val configured = resolvePixelRouteTransition(
            operation = transition.operation,
            outgoingTransition = transition.outgoingEntry.routeTransition,
            incomingTransition = transition.incomingEntry.routeTransition,
            defaultTransition = widget.defaultTransition,
        )
        if (
            motion.resolved.isImmediate ||
            motion.resolved.transition == PixelMotionTransitionPreset.None
        ) {
            return PixelRouteTransition.None
        }
        if (!motion.settings.reduceMotion) return configured
        return if (configured == PixelRouteTransition.None) {
            PixelRouteTransition.None
        } else {
            // Spatial slides become a short opacity transition under reduce motion.
            PixelRouteTransition.Fade
        }
    }

    /** Resolves route-level custom transition policy before the Navigator fallback. */
    private fun resolveTransitionBuilder(
        transition: PixelNavigatorTransitionRecord,
    ): PixelRouteTransitionBuilder? {
        val routeBuilder = when (transition.operation) {
            PixelNavigatorOperation.Pop -> transition.outgoingEntry.routeTransitionBuilder
            PixelNavigatorOperation.Push,
            PixelNavigatorOperation.Replace,
            -> transition.incomingEntry.routeTransitionBuilder
        }
        return routeBuilder ?: widget.transitionBuilder
    }

    /**
     * Starts, observes, or releases the controller associated with [transitionRecord].
     *
     * A `None` transition settles synchronously. Animated and custom transitions share one stable
     * controller so their retained entry subtrees do not move between different parent elements.
     */
    private fun transitionProgress(
        context: BuildContext,
        transitionRecord: PixelNavigatorTransitionRecord?,
        transition: PixelRouteTransition?,
        customBuilder: PixelRouteTransitionBuilder?,
        scope: PixelMotionScope?,
        motion: NavigatorResolvedMotion,
    ): Float {
        if (transitionRecord == null) {
            disposeTransitionController()
            return 1f
        }
        if (
            motion.resolved.isImmediate ||
            (customBuilder == null && transition == PixelRouteTransition.None)
        ) {
            disposeTransitionController()
            navigatorState.completeTransition(transitionRecord.id)
            return 1f
        }
        val transitionIdChanged = animatedTransitionId != transitionRecord.id
        val presentationPolicyChanged = !transitionIdChanged && (
            animatedTransition != transition ||
                animatedCustomBuilder !== customBuilder
            )
        if (presentationPolicyChanged) {
            // Switching between spatial, fade, and consumer-defined coordinate systems cannot
            // preserve an exact visual frame. Settling immediately avoids a discontinuous jump.
            disposeTransitionController()
            navigatorState.completeTransition(transitionRecord.id)
            return 1f
        }
        if (transitionIdChanged || animatedMotion != motion) {
            // A replacement transition owns an independent timeline. Motion-only retargeting for
            // the same ID rebases the new curve from the exact currently presented visual value.
            val previousProgress = if (transitionIdChanged) {
                0f
            } else {
                currentTransitionVisualProgress()
            }
            disposeTransitionController()
            val transitionId = transitionRecord.id
            val controller = PixelAnimationController(
                duration = motion.totalDuration,
                // The inherited Host provider wins. The required legacy constructor provider is
                // retained as a compatibility clock for existing direct Navigator consumers.
                vsync = scope?.vsync ?: widget.vsync,
            )
            transitionController = controller
            transitionCurve = CurvedAnimation(
                parent = controller,
                curve = navigatorDelayedCurve(
                    delay = motion.resolved.delay,
                    duration = motion.resolved.duration,
                    curve = motion.resolved.curve,
                ),
            )
            animatedTransitionId = transitionId
            animatedMotion = motion
            animatedTransition = transition
            animatedCustomBuilder = customBuilder
            animatedVisualStart = previousProgress
            controller.addListener {
                if (
                    animatedTransitionId == transitionId &&
                    controller.status == PixelAnimationStatus.Completed
                ) {
                    navigatorState.completeTransition(transitionId)
                }
            }
            controller.forward(from = 0f)
        }
        transitionController?.let(context::watch)
        return currentTransitionVisualProgress()
    }

    /** Returns the rebased visual progress for the currently active controller segment. */
    private fun currentTransitionVisualProgress(): Float {
        val segmentProgress = transitionCurve?.value?.coerceIn(0f, 1f) ?: return 1f
        return (animatedVisualStart + (1f - animatedVisualStart) * segmentProgress)
            .coerceIn(0f, 1f)
    }

    /** Releases the active transition controller and its curved view. */
    private fun disposeTransitionController() {
        transitionController?.dispose()
        transitionController = null
        transitionCurve = null
        animatedTransitionId = null
        animatedMotion = null
        animatedTransition = null
        animatedCustomBuilder = null
        animatedVisualStart = 0f
    }

    /** Resolves route tokens while preserving the existing public transition-duration priority. */
    private fun resolveMotion(
        context: BuildContext,
        scope: PixelMotionScope?,
    ): NavigatorResolvedMotion {
        val settings = scope?.settings ?: PixelMotionSettings.Default
        val themeSpec = PixelMotionTheme.of(context).route
        val resolved = themeSpec.copy(duration = widget.transitionDuration).resolve(settings)
        return NavigatorResolvedMotion(
            resolved = resolved,
            settings = settings,
            totalDuration = resolved.delay + resolved.duration,
        )
    }

    /** Orders hidden entries first, outgoing second, and incoming/current last for correct z-order. */
    private fun orderedPresentationEntries(
        transitionRecord: PixelNavigatorTransitionRecord?,
    ): List<PixelRouteEntry<*, *>> {
        val available = navigatorState.presentationEntries()
        val outgoing = transitionRecord?.outgoingEntry
        val incoming = transitionRecord?.incomingEntry ?: navigatorState.currentEntry
        val hidden = available.filter { entry -> entry !== outgoing && entry !== incoming }
        return (hidden + listOfNotNull(outgoing, incoming)).distinctBy { entry -> entry.id }
    }

    /** Computes opacity and size-relative translation for one stable entry wrapper. */
    private fun resolveEntryVisual(
        entry: PixelRouteEntry<*, *>,
        transitionRecord: PixelNavigatorTransitionRecord?,
        transition: PixelRouteTransition?,
        hasCustomBuilder: Boolean,
        progress: Float,
    ): PixelRouteEntryVisual {
        if (transitionRecord == null) {
            return if (entry === navigatorState.currentEntry) {
                PixelRouteEntryVisual.Visible
            } else {
                PixelRouteEntryVisual.Hidden
            }
        }
        val isOutgoing = entry === transitionRecord.outgoingEntry
        val isIncoming = entry === transitionRecord.incomingEntry
        if (!isOutgoing && !isIncoming) return PixelRouteEntryVisual.Hidden
        if (hasCustomBuilder) {
            // The retained source remains laid out and discoverable exactly once. Its ordinary
            // opacity is zero while the custom presentation proxy paints the same render subtree.
            return PixelRouteEntryVisual(
                opacity = 0f,
                isLogicallyPresented = true,
            )
        }
        val safeProgress = progress.coerceIn(0f, 1f)
        return when (transition) {
            PixelRouteTransition.Fade -> PixelRouteEntryVisual(
                opacity = if (isOutgoing) 1f - safeProgress else safeProgress,
            )
            PixelRouteTransition.SlideHorizontal,
            PixelRouteTransition.SlideVertical,
            -> {
                val direction = if (transitionRecord.operation == PixelNavigatorOperation.Pop) -1f else 1f
                val fraction = if (isOutgoing) {
                    -safeProgress * direction
                } else {
                    (1f - safeProgress) * direction
                }
                PixelRouteEntryVisual(
                    opacity = 1f,
                    fractionX = if (transition == PixelRouteTransition.SlideHorizontal) fraction else 0f,
                    fractionY = if (transition == PixelRouteTransition.SlideVertical) fraction else 0f,
                )
            }
            PixelRouteTransition.None,
            null,
            -> if (isIncoming) PixelRouteEntryVisual.Visible else PixelRouteEntryVisual.Hidden
        }
    }

    /** Builds one entry under its own state bucket and entry-ID-based retained key. */
    private fun routeChild(
        entry: PixelRouteEntry<*, *>,
        suffix: String = "retained",
    ): Widget {
        val entryKey = entry.id.value
        return PixelRouteStorageScope(
            bucket = entry.stateBucket,
            child = Builder(key = "route-entry:$entryKey:$suffix") { routeContext ->
                entry.build(routeContext)
            },
            key = "route-entry-storage:$entryKey:$suffix",
        )
    }

    /** Returns the stable custom-presentation link owned by one retained route entry. */
    private fun presentationLinkFor(entry: PixelRouteEntry<*, *>): PixelRoutePresentationLink {
        return presentationLinks.getOrPut(entry.id) { PixelRoutePresentationLink() }
    }
}

/** Immutable Navigator-specific motion policy resolved for one build. */
private data class NavigatorResolvedMotion(
    /** Theme token after animator scale and reduce-motion adaptation. */
    val resolved: PixelResolvedMotion,
    /** Host settings retained so custom and built-in policy use the same decision. */
    val settings: PixelMotionSettings,
    /** Combined delay and interpolation duration owned by the transition controller. */
    val totalDuration: Duration,
)

/** Encodes a resolved route delay into one normalized controller curve. */
private fun navigatorDelayedCurve(
    delay: Duration,
    duration: Duration,
    curve: Curve,
): Curve {
    val total = delay + duration
    if (delay == Duration.ZERO) return curve
    if (duration == Duration.ZERO) return Curve { progress -> if (progress >= 1f) 1f else 0f }
    val delayFraction = when {
        delay.isInfinite() -> 1f
        total.isInfinite() && duration.isInfinite() -> 0f
        total.isInfinite() -> 1f
        else -> (delay.inWholeNanoseconds.toDouble() / total.inWholeNanoseconds.toDouble()).toFloat()
    }.coerceIn(0f, 1f)
    return Curve { progress ->
        when {
            progress <= delayFraction -> 0f
            delayFraction >= 1f -> 0f
            else -> curve.transform(
                ((progress - delayFraction) / (1f - delayFraction)).coerceIn(0f, 1f),
            )
        }
    }
}

/** Immutable visual properties applied to one retained route entry wrapper. */
private data class PixelRouteEntryVisual(
    /** Entry opacity in the inclusive `0f..1f` range. */
    val opacity: Float,
    /** Horizontal translation as a fraction of the laid-out entry width. */
    val fractionX: Float = 0f,
    /** Vertical translation as a fraction of the laid-out entry height. */
    val fractionY: Float = 0f,
    /** Whether finders should treat this mounted route as part of the presented page set. */
    val isLogicallyPresented: Boolean = true,
) {
    /** Common fully hidden visual configuration. */
    companion object {
        /** Fully hidden entry visual. */
        val Hidden: PixelRouteEntryVisual = PixelRouteEntryVisual(
            opacity = 0f,
            isLogicallyPresented = false,
        )

        /** Fully visible, untranslated entry visual. */
        val Visible: PixelRouteEntryVisual = PixelRouteEntryVisual(opacity = 1f)
    }
}

/** Stable single-child wrapper that translates an entry by a fraction of its laid-out size. */
private class PixelRouteFractionalTranslationWidget(
    /** Horizontal translation as a fraction of child width. */
    val fractionX: Float,
    /** Vertical translation as a fraction of child height. */
    val fractionY: Float,
    /** Whether the translated route may expose interaction and semantics targets. */
    val interactive: Boolean,
    /** Whether this foreground/transition entry should lay out its retained render subtree. */
    val layoutEnabled: Boolean,
    /** Whether test widget collection should descend into this logically presented entry. */
    override val exposesSubtreeToWidgetCollection: Boolean,
    /** Stable link used by custom transitions to present this retained route without rebuilding it. */
    val presentationLink: PixelRoutePresentationLink,
    /** Retained route entry subtree. */
    override val child: Widget,
    /** Entry-ID-based identity retained across transition frames. */
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key), ElementSubtreeVisibility {
    /** Creates the fractional translation render object. */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderRouteFractionalTranslation(
            fractionX = fractionX,
            fractionY = fractionY,
            interactive = interactive,
            layoutEnabled = layoutEnabled,
            presentationLink = presentationLink,
        )
    }

    /** Updates visual translation without replacing the retained child element. */
    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderRouteFractionalTranslation).update(
            fractionX = fractionX,
            fractionY = fractionY,
            interactive = interactive,
            layoutEnabled = layoutEnabled,
            presentationLink = presentationLink,
        )
    }
}

/** Render object implementing size-relative route transition offsets. */
private class RenderRouteFractionalTranslation(
    /** Current horizontal size fraction. */
    private var fractionX: Float,
    /** Current vertical size fraction. */
    private var fractionY: Float,
    /** Whether this route is the foreground interaction owner. */
    private var interactive: Boolean,
    /** Whether the retained child should participate in the current layout pass. */
    private var layoutEnabled: Boolean,
    /** Link that exposes this source to one optional custom-transition presentation proxy. */
    private var presentationLink: PixelRoutePresentationLink,
) : SingleChildRenderObject() {
    init {
        presentationLink.attach(this)
    }

    /** Updates transition properties and schedules repaint when they change. */
    fun update(
        fractionX: Float,
        fractionY: Float,
        interactive: Boolean,
        layoutEnabled: Boolean,
        presentationLink: PixelRoutePresentationLink,
    ) {
        if (
            this.fractionX == fractionX &&
            this.fractionY == fractionY &&
            this.interactive == interactive &&
            this.layoutEnabled == layoutEnabled &&
            this.presentationLink === presentationLink
        ) {
            return
        }
        // Re-entering or leaving offstage mode changes whether the retained child must be laid out.
        val layoutPolicyChanged = this.layoutEnabled != layoutEnabled
        this.fractionX = fractionX
        this.fractionY = fractionY
        this.interactive = interactive
        this.layoutEnabled = layoutEnabled
        if (this.presentationLink !== presentationLink) {
            this.presentationLink.detach(this)
            this.presentationLink = presentationLink
            presentationLink.attach(this)
        }
        if (layoutPolicyChanged) markNeedsLayout()
        markNeedsPaint()
    }

    /** Re-registers this source when its retained render tree is attached to a pipeline. */
    override fun onAttach() {
        presentationLink.attach(this)
    }

    /** Clears the link before this source leaves its retained render tree. */
    override fun onDetach() {
        presentationLink.detach(this)
    }

    /** Lays out the retained route under the Navigator's full constraints. */
    override fun layout(constraints: RenderConstraints) {
        if (layoutEnabled) {
            renderChild?.layout(constraints)
        }
        val childSize = if (layoutEnabled) renderChild?.size ?: RenderSize.Zero else RenderSize.Zero
        size = RenderSize(
            width = constraints.constrainWidth(childSize.width),
            height = constraints.constrainHeight(childSize.height),
        )
    }

    /** Paints the retained route at its current size-relative transition offset. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        renderChild?.paint(context, offsetX + translatedX, offsetY + translatedY)
    }

    /** Hit-tests only the active route after compensating for its paint translation. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        if (!interactive) return
        renderChild?.hitTest(localX - translatedX, localY - translatedY, result)
    }

    /** Exports translated click targets only for the active route. */
    override fun collectClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ) {
        if (interactive) renderChild?.collectClickTargets(offsetX + translatedX, offsetY + translatedY, targets)
    }

    /** Exports translated pager targets only for the active route. */
    override fun collectPagerTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelPagerTarget>,
    ) {
        if (interactive) renderChild?.collectPagerTargets(offsetX + translatedX, offsetY + translatedY, targets)
    }

    /** Exports translated list targets only for the active route. */
    override fun collectListTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelListTarget>,
    ) {
        if (interactive) renderChild?.collectListTargets(offsetX + translatedX, offsetY + translatedY, targets)
    }

    /** Exports translated scrollbar targets only for the active route. */
    override fun collectScrollbarTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelScrollbarTarget>,
    ) {
        if (interactive) renderChild?.collectScrollbarTargets(offsetX + translatedX, offsetY + translatedY, targets)
    }

    /** Exports translated refresh targets only for the active route. */
    override fun collectRefreshTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelRefreshTarget>,
    ) {
        if (interactive) renderChild?.collectRefreshTargets(offsetX + translatedX, offsetY + translatedY, targets)
    }

    /** Exports translated text-input targets only for the active route. */
    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        if (interactive) renderChild?.collectTextInputTargets(offsetX + translatedX, offsetY + translatedY, targets)
    }

    /** Exports translated slider targets only for the active route. */
    override fun collectSliderTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelSliderTarget>,
    ) {
        if (interactive) renderChild?.collectSliderTargets(offsetX + translatedX, offsetY + translatedY, targets)
    }

    /** Exports translated semantics only for the active route. */
    override fun collectSemantics(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelSemanticsTarget>,
    ) {
        if (interactive) renderChild?.collectSemantics(offsetX + translatedX, offsetY + translatedY, targets)
    }

    /** Current horizontal pixel offset derived from [fractionX]. */
    private val translatedX: Int
        get() = (fractionX * size.width).roundToInt()

    /** Current vertical pixel offset derived from [fractionY]. */
    private val translatedY: Int
        get() = (fractionY * size.height).roundToInt()

    /** Typed child render box, when one is mounted. */
    private val renderChild: RenderBox?
        get() = child as? RenderBox

    /** Size exposed to a custom-transition proxy after the stable source has been laid out. */
    val presentationSize: RenderSize
        get() = size

    /** Paints the retained child at a custom proxy location without mounting a second subtree. */
    fun paintPresentation(context: PaintContext, offsetX: Int, offsetY: Int) {
        renderChild?.paint(context, offsetX, offsetY)
    }

    /** Hit-tests the retained child through a custom-transition proxy. */
    fun hitTestPresentation(localX: Int, localY: Int, result: HitTestResult) {
        renderChild?.hitTest(localX, localY, result)
    }

    /** Exports retained click targets through a custom-transition proxy. */
    fun collectPresentationClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ) {
        renderChild?.collectClickTargets(offsetX, offsetY, targets)
    }

    /** Exports retained pager targets through a custom-transition proxy. */
    fun collectPresentationPagerTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelPagerTarget>,
    ) {
        renderChild?.collectPagerTargets(offsetX, offsetY, targets)
    }

    /** Exports retained list targets through a custom-transition proxy. */
    fun collectPresentationListTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelListTarget>,
    ) {
        renderChild?.collectListTargets(offsetX, offsetY, targets)
    }

    /** Exports retained scrollbar targets through a custom-transition proxy. */
    fun collectPresentationScrollbarTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelScrollbarTarget>,
    ) {
        renderChild?.collectScrollbarTargets(offsetX, offsetY, targets)
    }

    /** Exports retained refresh targets through a custom-transition proxy. */
    fun collectPresentationRefreshTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelRefreshTarget>,
    ) {
        renderChild?.collectRefreshTargets(offsetX, offsetY, targets)
    }

    /** Exports retained text-input targets through a custom-transition proxy. */
    fun collectPresentationTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        renderChild?.collectTextInputTargets(offsetX, offsetY, targets)
    }

    /** Exports retained slider targets through a custom-transition proxy. */
    fun collectPresentationSliderTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelSliderTarget>,
    ) {
        renderChild?.collectSliderTargets(offsetX, offsetY, targets)
    }

    /** Exports retained accessibility semantics through a custom-transition proxy. */
    fun collectPresentationSemantics(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelSemanticsTarget>,
    ) {
        renderChild?.collectSemantics(offsetX, offsetY, targets)
    }
}

/** Mutable bridge from one retained route source to one or more paint-only presentation proxies. */
private class PixelRoutePresentationLink {
    /** Currently mounted source render object, or `null` while its entry is detached. */
    var source: RenderRouteFractionalTranslation? = null
        private set

    /** Registers the one retained source render object. */
    fun attach(nextSource: RenderRouteFractionalTranslation) {
        source = nextSource
    }

    /** Clears [source] only when the detaching render object still owns this link. */
    fun detach(previousSource: RenderRouteFractionalTranslation) {
        if (source === previousSource) source = null
    }
}

/** Leaf placeholder that presents a linked retained route inside a consumer custom transition. */
private class PixelRoutePresentationProxyWidget(
    /** Link to the retained source route. */
    val presentationLink: PixelRoutePresentationLink,
    /** Whether this proxy owns interaction and semantics for the current route. */
    val interactive: Boolean,
    /** Transition-specific proxy identity. */
    override val key: Any? = null,
) : LeafRenderObjectWidget(key = key) {
    /** Creates the non-owning presentation proxy render box. */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderRoutePresentationProxy(
            presentationLink = presentationLink,
            interactive = interactive,
        )
    }

    /** Retargets the proxy without changing the retained route source. */
    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderRoutePresentationProxy).update(
            presentationLink = presentationLink,
            interactive = interactive,
        )
    }
}

/** Non-owning render proxy that paints and exports channels from one stable route source. */
private class RenderRoutePresentationProxy(
    /** Link to the retained source route. */
    private var presentationLink: PixelRoutePresentationLink,
    /** Whether non-paint channels are enabled for this proxy. */
    private var interactive: Boolean,
) : RenderBox() {
    /** Updates the linked route and interaction policy for the next frame. */
    fun update(
        presentationLink: PixelRoutePresentationLink,
        interactive: Boolean,
    ) {
        if (this.presentationLink === presentationLink && this.interactive == interactive) return
        this.presentationLink = presentationLink
        this.interactive = interactive
        markNeedsLayout()
        markNeedsPaint()
    }

    /** Adopts the already laid-out source size without owning or reparenting its render subtree. */
    override fun layout(constraints: RenderConstraints) {
        val sourceSize = presentationLink.source?.presentationSize ?: RenderSize.Zero
        size = RenderSize(
            width = constraints.constrainWidth(sourceSize.width),
            height = constraints.constrainHeight(sourceSize.height),
        )
    }

    /** Paints the linked retained source at this proxy's custom-transition offset. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        presentationLink.source?.paintPresentation(context, offsetX, offsetY)
    }

    /** Routes hit testing only through the foreground custom-transition proxy. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        if (interactive) presentationLink.source?.hitTestPresentation(localX, localY, result)
    }

    /** Routes click targets only through the foreground custom-transition proxy. */
    override fun collectClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ) {
        if (interactive) {
            presentationLink.source?.collectPresentationClickTargets(offsetX, offsetY, targets)
        }
    }

    /** Routes pager targets only through the foreground custom-transition proxy. */
    override fun collectPagerTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelPagerTarget>,
    ) {
        if (interactive) {
            presentationLink.source?.collectPresentationPagerTargets(offsetX, offsetY, targets)
        }
    }

    /** Routes list targets only through the foreground custom-transition proxy. */
    override fun collectListTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelListTarget>,
    ) {
        if (interactive) {
            presentationLink.source?.collectPresentationListTargets(offsetX, offsetY, targets)
        }
    }

    /** Routes scrollbar targets only through the foreground custom-transition proxy. */
    override fun collectScrollbarTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelScrollbarTarget>,
    ) {
        if (interactive) {
            presentationLink.source?.collectPresentationScrollbarTargets(offsetX, offsetY, targets)
        }
    }

    /** Routes refresh targets only through the foreground custom-transition proxy. */
    override fun collectRefreshTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelRefreshTarget>,
    ) {
        if (interactive) {
            presentationLink.source?.collectPresentationRefreshTargets(offsetX, offsetY, targets)
        }
    }

    /** Routes text-input targets only through the foreground custom-transition proxy. */
    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        if (interactive) {
            presentationLink.source?.collectPresentationTextInputTargets(offsetX, offsetY, targets)
        }
    }

    /** Routes slider targets only through the foreground custom-transition proxy. */
    override fun collectSliderTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelSliderTarget>,
    ) {
        if (interactive) {
            presentationLink.source?.collectPresentationSliderTargets(offsetX, offsetY, targets)
        }
    }

    /** Routes semantics only through the foreground custom-transition proxy. */
    override fun collectSemantics(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelSemanticsTarget>,
    ) {
        if (interactive) {
            presentationLink.source?.collectPresentationSemantics(offsetX, offsetY, targets)
        }
    }
}

private class PixelNavigatorScope(
    val navigatorState: PixelNavigatorState,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return (oldWidget as? PixelNavigatorScope)?.navigatorState !== navigatorState
    }
}

internal class PixelRouteStorageScope(
    val bucket: PixelRouteStateBucket,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return (oldWidget as? PixelRouteStorageScope)?.bucket !== bucket
    }
}

internal data class PixelNavigatorTransitionRecord(
    val id: Long,
    val outgoingEntry: PixelRouteEntry<*, *>,
    val incomingEntry: PixelRouteEntry<*, *>,
    val operation: PixelNavigatorOperation,
)

/** Immutable gesture-controlled presentation state that does not yet mutate the route stack. */
internal data class PixelNavigatorPredictiveBackRecord(
    /** Pop transition whose entries remain in their pre-commit lifecycle states. */
    val transition: PixelNavigatorTransitionRecord,
    /** Latest normalized platform progress in the inclusive `0f..1f` range. */
    val progress: Float,
)

/** Erased terminal result retained until an outgoing entry has been disposed. */
private sealed interface PendingRouteCompletion {
    /** Successful completion carrying a legacy-erased value. */
    data class Success(val value: Any?) : PendingRouteCompletion

    /** Explicit cancellation carrying a machine-readable reason. */
    data class Cancel(val reason: PixelRouteCancellationReason) : PendingRouteCompletion

    /** No delivery because legacy replace transferred the callback to a new entry. */
    data object None : PendingRouteCompletion
}

/** Entry plus its deferred terminal result action. */
private data class PendingRouteEntryFinalization(
    /** Entry awaiting terminal disposal. */
    val entry: PixelRouteEntry<*, *>,
    /** Result action resolved only after [entry] disposal. */
    val completion: PendingRouteCompletion,
)
