package com.purride.pixelui

import com.purride.pixelui.animation.Curve
import com.purride.pixelui.animation.CurvedAnimation
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelAnimationStatus
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.internal.ElementSubtreeVisibility
import com.purride.pixelui.internal.VisualOnlyWidget
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 定义 `PixelTypedNavigatorStack` 在 `PixelMultiStackNavigator` 中承担的数据与行为边界。
 *
 * Typed, persistable definition of one independently retained Navigator stack.
 *
 * 栈标识在同一个 [PixelMultiStackNavigator] 内必须唯一；[initialRequest] 始终是该栈的最底层
 * entry，通常对应一个底部导航标签。
 *
 * Stack identifiers must be unique inside one [PixelMultiStackNavigator]. The [initialRequest]
 * remains the bottom entry for this stack, which is a common fit for one bottom-navigation tab.
 *
 * @param A Non-null root argument type. 根 entry 的非空参数类型。
 * @param R Successful result type of the root entry. 根 entry 的成功结果类型。
 * @property id 选择、恢复和诊断该栈时使用的稳定非空标识。
 * @property initialRequest 该栈首次挂载时用于创建根 entry 的类型化请求。
 */
public data class PixelTypedNavigatorStack<A : Any, R>(
    public val id: String,
    public val initialRequest: PixelRouteRequest<A, R>,
) {
    init {
        require(id.isNotBlank()) { "PixelTypedNavigatorStack id must not be blank" }
    }
}

/** 表示 `PixelMultiStackNavigator` 的 `PixelStackSelectionResult` 稳定结果或事件分支。
 *
 * Result of requesting a stack selection through [PixelMultiStackNavigatorController].
 */
public enum class PixelStackSelectionResult {
    /** A different known stack became active. */
    Activated,

    /** The requested stack was already active and its route stack was left unchanged. */
    AlreadyActive,

    /** Reselecting the active stack removed every entry above its root. */
    PoppedToRoot,

    /** No mounted host declares the requested stack identifier. */
    UnknownStack,
}

/**
 * 定义 `PixelMultiStackNavigatorController` 在 `PixelMultiStackNavigator` 中承担的数据与行为边界。
 *
 * Coordinates independent route stacks owned by one [PixelMultiStackNavigator].
 *
 * The controller does not create Navigator states itself. States become available after the host
 * mounts and are detached when that host is removed. A controller may be attached to only one host
 * at a time so stack identifiers can never resolve to an ambiguous Navigator.
 *
 * @property initialStackId Stack selected initially and used as the back fallback from other roots.
 */
public class PixelMultiStackNavigatorController(
    public val initialStackId: String,
) : ChangeNotifier(), PixelPredictiveBackCallback {
    /** Opaque identity of the currently attached multi-stack widget state. */
    private var hostOwner: Any? = null

    /** Ordered identifiers declared by the attached host. */
    private var mountedStackIds: LinkedHashSet<String> = linkedSetOf()

    /** Navigator states captured from each mounted stack root. */
    private val navigatorStates: MutableMap<String, PixelNavigatorState> = linkedMapOf()

    /** Isolated back dispatchers owned by each mounted stack. */
    private val backDispatchers: MutableMap<String, PixelBackDispatcher> = linkedMapOf()

    /** Availability observers that keep the parent bridge absent at an unhandled root. */
    private val backAvailabilityRegistrations: MutableMap<String, PixelBackRegistration> =
        linkedMapOf()

    /** Stack locked by the active predictive-back session, if one has started. */
    private var predictiveSessionStackId: String? = null

    /** Whether the active predictive session represents switching to the initial stack. */
    private var predictiveSessionReturnsToInitial: Boolean = false

    /** Latest platform progress for a secondary-root return preview, or `null` outside that session. */
    private var predictiveReturnProgress: Float? = null

    /** Counts committed secondary-root previews so the retained host can suppress a second fade. */
    private var predictiveReturnCommitGeneration: Long = 0L

    /** Progress currently previewing the initial stack beneath the active secondary root. */
    internal val activePredictiveReturnProgress: Float?
        get() = predictiveReturnProgress

    /** Monotonic generation incremented only when a predictive root return commits. */
    internal val completedPredictiveReturnGeneration: Long
        get() = predictiveReturnCommitGeneration

    /** 公开 `PixelMultiStackNavigator` 的 `activeStackId` 配置或运行值。
 *
 * Currently selected stack identifier.
 */
    public var activeStackId: String = initialStackId
        private set

    /** 表示 `PixelMultiStackNavigator` 当前是否满足 `isAttached` 对应条件。
 *
 * Whether this controller is currently bound to a multi-stack host.
 */
    public val isAttached: Boolean
        get() = hostOwner != null

    /** 公开 `PixelMultiStackNavigator` 的 `stackIds` 配置或运行值。
 *
 * Immutable ordered identifiers advertised by the currently attached host.
 */
    public val stackIds: Set<String>
        get() = mountedStackIds.toSet()

    /** 公开 `PixelMultiStackNavigator` 当前的 `activeNavigatorState` 状态维度。
 *
 * Navigator state of [activeStackId], or `null` before its root has mounted.
 */
    public val activeNavigatorState: PixelNavigatorState?
        get() = navigatorStates[activeStackId]

    /** 表示 `PixelMultiStackNavigator` 当前是否满足 `canHandleBack` 对应条件。
 *
 * Whether the active child can handle back or a secondary root can return to initial.
 */
    public val canHandleBack: Boolean
        get() = backDispatchers[activeStackId]?.hasRegisteredHandlers == true ||
            (activeStackId != initialStackId && initialStackId in mountedStackIds)

    init {
        require(initialStackId.isNotBlank()) {
            "PixelMultiStackNavigatorController initialStackId must not be blank"
        }
    }

    /** 执行 `PixelMultiStackNavigator` 的 `navigatorState` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the mounted Navigator state for [stackId], or `null` when unavailable.
 */
    public fun navigatorState(stackId: String): PixelNavigatorState? = navigatorStates[stackId]

    /**
 * 执行 `PixelMultiStackNavigator` 的 `selectStack` 公开行为；具体参数、返回和副作用见下文。
 *
     * Selects [stackId] without mutating any inactive route stack.
     *
     * When [popToRootOnReselect] is true, selecting the active stack again clears only entries
     * above that stack's root. This directly supports the common bottom-navigation reselection
     * convention while leaving the default behavior lossless.
     */
    public fun selectStack(
        stackId: String,
        popToRootOnReselect: Boolean = false,
        animated: Boolean = true,
    ): PixelStackSelectionResult {
        if (stackId !in mountedStackIds) return PixelStackSelectionResult.UnknownStack
        cancelPredictiveSession()
        if (stackId == activeStackId) {
            val popped = popToRootOnReselect &&
                (navigatorStates[stackId]?.clear(animated = animated) == true)
            return if (popped) {
                PixelStackSelectionResult.PoppedToRoot
            } else {
                PixelStackSelectionResult.AlreadyActive
            }
        }
        activeStackId = stackId
        notifyListeners()
        return PixelStackSelectionResult.Activated
    }

    /**
 * 执行 `PixelMultiStackNavigator` 的 `handleBack` 公开行为；具体参数、返回和副作用见下文。
 *
     * Dispatches back only inside the active stack, including nested Navigators registered there.
     *
     * If the active stack is already at its root and [switchToInitialStack] is true, back selects
     * [initialStackId]. Back at the initial stack root remains unhandled for the enclosing host.
     */
    public fun handleBack(switchToInitialStack: Boolean = true): Boolean {
        cancelPredictiveSession()
        if (backDispatchers[activeStackId]?.handleBack() == true) return true
        if (
            switchToInitialStack &&
            activeStackId != initialStackId &&
            initialStackId in mountedStackIds
        ) {
            activeStackId = initialStackId
            notifyListeners()
            return true
        }
        return false
    }

    /** Locks predictive back to the active stack or to the secondary-root fallback. */
    override fun onBackStarted(event: PixelPredictiveBackEvent): Boolean {
        cancelPredictiveSession()
        val sessionStackId = activeStackId
        val childAccepted = backDispatchers[sessionStackId]?.startPredictiveBack(event) == true
        if (childAccepted) {
            predictiveSessionStackId = sessionStackId
            predictiveSessionReturnsToInitial = false
            return true
        }
        if (sessionStackId != initialStackId && initialStackId in mountedStackIds) {
            predictiveSessionStackId = sessionStackId
            predictiveSessionReturnsToInitial = true
            predictiveReturnProgress = event.progress
            notifyListeners()
            return true
        }
        return false
    }

    /** Forwards progress only to the child dispatcher locked during [onBackStarted]. */
    override fun onBackProgressed(event: PixelPredictiveBackEvent) {
        val sessionStackId = predictiveSessionStackId ?: return
        if (!predictiveSessionReturnsToInitial) {
            backDispatchers[sessionStackId]?.updatePredictiveBack(event)
            return
        }
        if (activeStackId != sessionStackId) return
        if (predictiveReturnProgress == event.progress) return
        predictiveReturnProgress = event.progress
        notifyListeners()
    }

    /** Cancels only the locked active-stack session and leaves every route stack unchanged. */
    override fun onBackCancelled() {
        cancelPredictiveSession()
    }

    /** Commits the locked active child or the secondary-root switch exactly once. */
    override fun onBackCommitted(): Boolean {
        val sessionStackId = predictiveSessionStackId ?: return false
        val returnsToInitial = predictiveSessionReturnsToInitial
        predictiveSessionStackId = null
        predictiveSessionReturnsToInitial = false
        if (returnsToInitial) {
            predictiveReturnProgress = null
            if (activeStackId != sessionStackId || initialStackId !in mountedStackIds) {
                notifyListeners()
                return false
            }
            activeStackId = initialStackId
            predictiveReturnCommitGeneration += 1L
            notifyListeners()
            return true
        }
        val handled = backDispatchers[sessionStackId]?.commitPredictiveBack() == true
        return handled
    }

    /** Uses the same active-stack-first policy for hardware and legacy discrete back. */
    override fun onBackInvoked(): Boolean = handleBack()

    /** Binds this controller to exactly one host and validates its complete stack definition. */
    internal fun bindHost(owner: Any, stackIds: List<String>) {
        check(hostOwner == null || hostOwner === owner) {
            "PixelMultiStackNavigatorController is already attached to another host"
        }
        require(stackIds.isNotEmpty()) { "PixelMultiStackNavigator requires at least one stack" }
        require(stackIds.distinct().size == stackIds.size) {
            "PixelMultiStackNavigator stack ids must be unique"
        }
        require(initialStackId in stackIds) {
            "Initial stack '$initialStackId' is not declared by PixelMultiStackNavigator"
        }
        val nextStackIds = LinkedHashSet(stackIds)
        if (
            predictiveSessionStackId != null &&
            predictiveSessionStackId !in nextStackIds
        ) {
            cancelPredictiveSession()
        }
        hostOwner = owner
        mountedStackIds = nextStackIds
        navigatorStates.keys.retainAll(mountedStackIds)
        backDispatchers.keys.retainAll(mountedStackIds)
        val removedAvailabilityIds = backAvailabilityRegistrations.keys - mountedStackIds
        removedAvailabilityIds.forEach { stackId ->
            backAvailabilityRegistrations.remove(stackId)?.dispose()
        }
        if (activeStackId !in mountedStackIds) activeStackId = initialStackId
    }

    /** Records the concrete state mounted for [stackId] when it belongs to [owner]. */
    internal fun attachNavigator(
        owner: Any,
        stackId: String,
        navigatorState: PixelNavigatorState,
    ) {
        if (hostOwner !== owner || stackId !in mountedStackIds) return
        navigatorStates[stackId] = navigatorState
    }

    /** Records the isolated dispatcher that owns all back handlers under [stackId]. */
    internal fun attachBackDispatcher(
        owner: Any,
        stackId: String,
        dispatcher: PixelBackDispatcher,
    ) {
        if (hostOwner !== owner || stackId !in mountedStackIds) return
        if (
            predictiveSessionStackId == stackId &&
            backDispatchers[stackId] !== dispatcher
        ) {
            cancelPredictiveSession()
        }
        if (backDispatchers[stackId] === dispatcher) return
        backAvailabilityRegistrations.remove(stackId)?.dispose()
        backDispatchers[stackId] = dispatcher
        backAvailabilityRegistrations[stackId] = dispatcher.addAvailabilityListener {
            if (stackId == activeStackId) notifyListeners()
        }
    }

    /** Detaches all host-owned state references without disposing this user-owned controller. */
    internal fun unbindHost(owner: Any) {
        if (hostOwner !== owner) return
        cancelPredictiveSession()
        hostOwner = null
        mountedStackIds.clear()
        navigatorStates.clear()
        backDispatchers.clear()
        backAvailabilityRegistrations.values.forEach(PixelBackRegistration::dispose)
        backAvailabilityRegistrations.clear()
    }

    /** Clears controller session state after notifying only the locked child dispatcher. */
    private fun cancelPredictiveSession() {
        val sessionStackId = predictiveSessionStackId
        val shouldCancelChild = sessionStackId != null && !predictiveSessionReturnsToInitial
        val hadPredictiveReturn = sessionStackId != null && predictiveSessionReturnsToInitial
        predictiveSessionStackId = null
        predictiveSessionReturnsToInitial = false
        predictiveReturnProgress = null
        if (shouldCancelChild) backDispatchers[sessionStackId]?.cancelPredictiveBack()
        if (hadPredictiveReturn) notifyListeners()
    }
}

/**
 * 定义 `PixelNestedNavigatorController` 在 `PixelMultiStackNavigator` 中承担的数据与行为边界。
 *
 * Exposes the typed Navigator state owned by one [PixelNestedNavigator] host.
 *
 * The controller never creates or retains a Navigator beyond its host lifetime. It may be bound
 * to only one mounted nested host at a time, preventing snapshot operations from targeting an
 * ambiguous subtree. Persistent capture and restoration delegate to the same versioned,
 * allowlisted codec used by a top-level [PixelNavigatorState].
 */
public class PixelNestedNavigatorController {
    /** Opaque identity of the currently mounted nested host. */
    private var hostOwner: Any? = null

    /** Navigator state attached by the mounted nested host after its child is initialized. */
    private var mountedNavigatorState: PixelNavigatorState? = null

    /** 表示 `PixelMultiStackNavigator` 当前是否满足 `isAttached` 对应条件。
 *
 * Whether this controller currently belongs to one mounted nested host.
 */
    public val isAttached: Boolean
        get() = hostOwner != null

    /** 公开 `PixelMultiStackNavigator` 当前的 `navigatorState` 状态维度。
 *
 * Mounted nested Navigator state, or `null` before mount and after host disposal.
 */
    public val navigatorState: PixelNavigatorState?
        get() = mountedNavigatorState

    /**
 * 执行 `PixelMultiStackNavigator` 的 `persistentSnapshot` 公开行为；具体参数、返回和副作用见下文。
 *
     * Encodes the mounted typed nested stack through the explicit [registry] allowlist.
     *
     * Calling this before mount or after disposal returns a structured rejection rather than
     * retaining or manufacturing a detached Navigator state.
     */
    public fun persistentSnapshot(
        registry: PixelRouteSnapshotRegistry,
        codec: PixelNavigatorSnapshotCodec = PixelNavigatorSnapshotCodec(),
    ): PixelNavigatorSnapshotEncodeResult {
        // Mounted state is the only valid capture source and is never synthesized by the controller.
        val state = mountedNavigatorState ?: return PixelNavigatorSnapshotEncodeResult.Rejected(
            PixelNavigatorSnapshotFailure(
                reason = PixelNavigatorSnapshotFailureReason.InvalidStack,
                message = "PixelNestedNavigatorController has no mounted Navigator state",
            ),
        )
        return state.persistentSnapshot(registry = registry, codec = codec)
    }

    /**
 * 执行 `PixelMultiStackNavigator` 的 `restore` 公开行为；具体参数、返回和副作用见下文。
 *
     * Atomically installs a previously decoded [plan] into the mounted nested Navigator.
     *
     * A missing host returns `false` without consuming the one-shot plan.
     */
    public fun restore(plan: PixelNavigatorRestorePlan): Boolean {
        return mountedNavigatorState?.restore(plan) == true
    }

    /**
 * 执行 `PixelMultiStackNavigator` 的 `restorePersistentSnapshot` 公开行为；具体参数、返回和副作用见下文。
 *
     * Decodes versioned [bytes] and restores the mounted typed nested stack atomically.
     *
     * A missing host or invalid payload leaves the current subtree unchanged and returns a
     * structured rejection.
     */
    public fun restorePersistentSnapshot(
        bytes: ByteArray,
        registry: PixelRouteSnapshotRegistry,
        codec: PixelNavigatorSnapshotCodec = PixelNavigatorSnapshotCodec(),
    ): PixelNavigatorSnapshotDecodeResult {
        // Mounted state is required so decoded entries always gain one live Navigator owner.
        val state = mountedNavigatorState ?: return PixelNavigatorSnapshotDecodeResult.Rejected(
            PixelNavigatorSnapshotFailure(
                reason = PixelNavigatorSnapshotFailureReason.InvalidStack,
                message = "PixelNestedNavigatorController has no mounted Navigator state",
            ),
        )
        return state.restorePersistentSnapshot(bytes = bytes, registry = registry, codec = codec)
    }

    /** Binds this controller to exactly one mounted nested host. */
    internal fun bindHost(owner: Any) {
        check(hostOwner == null || hostOwner === owner) {
            "PixelNestedNavigatorController is already attached to another host"
        }
        hostOwner = owner
    }

    /** Attaches [navigatorState] only when [owner] still owns this controller. */
    internal fun attachNavigator(
        owner: Any,
        navigatorState: PixelNavigatorState,
    ) {
        if (hostOwner !== owner) return
        mountedNavigatorState = navigatorState
    }

    /** Clears every host reference when the owning nested widget is disposed or replaced. */
    internal fun unbindHost(owner: Any) {
        if (hostOwner !== owner) return
        hostOwner = null
        mountedNavigatorState = null
    }
}

/**
 * 定义 `PixelMultiStackNavigator` 在 `PixelMultiStackNavigator` 中承担的数据与行为边界。
 *
 * Hosts several always-mounted Navigator stacks while exposing only the selected stack.
 *
 * Inactive stacks keep their Element/State trees and route entries mounted, but they do not paint,
 * hit-test, export semantics, appear in test finders, or receive back events. Pass [parentEntry]
 * when this widget itself lives inside a retained route so its outer back handler is disabled while
 * that parent entry is inactive.
 */
public class PixelMultiStackNavigator(
    /** 保存 `PixelMultiStackNavigator` 当前的 `stacks` 集合；元素顺序和所有权遵守所属类型契约。 */
    public val stacks: List<PixelTypedNavigatorStack<*, *>>,
    /** 提供 `PixelMultiStackNavigator` 执行 `controller` 职责时使用的协作者。 */
    public val controller: PixelMultiStackNavigatorController,
    /** 记录 `PixelMultiStackNavigator` 的 `vsync` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val vsync: PixelTickerProvider,
    /** 控制 `PixelMultiStackNavigator` 的 `transitionDuration` 时间参数，单位为声明约定的时间单位。 */
    public val transitionDuration: Duration = 200.milliseconds,
    /** 记录 `PixelMultiStackNavigator` 的 `defaultTransition` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val defaultTransition: PixelRouteTransition = PixelRouteTransition.SlideHorizontal,
    /** 记录 `PixelMultiStackNavigator` 的 `transitionBuilder` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val transitionBuilder: PixelRouteTransitionBuilder? = null,
    /** 表示 `PixelMultiStackNavigator` 当前是否满足 `backEnabled` 对应条件。 */
    public val backEnabled: Boolean = true,
    /** 提供 `PixelMultiStackNavigator` 当前管理的 `parentEntry` 内容。 */
    public val parentEntry: PixelRouteEntry<*, *>? = null,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    init {
        require(stacks.isNotEmpty()) { "PixelMultiStackNavigator requires at least one stack" }
        require(stacks.map { stack -> stack.id }.distinct().size == stacks.size) {
            "PixelMultiStackNavigator stack ids must be unique"
        }
        require(stacks.any { stack -> stack.id == controller.initialStackId }) {
            "Initial stack '${controller.initialStackId}' is not declared by PixelMultiStackNavigator"
        }
    }

    /** Creates the state that owns per-stack back dispatchers and host attachment. */
    override fun createState(): State<out StatefulWidget> = PixelMultiStackNavigatorWidgetState()
}

/**
 * 定义 `PixelNestedNavigator` 在 `PixelMultiStackNavigator` 中承担的数据与行为边界。
 *
 * Back-isolated wrapper for a Navigator nested inside another retained route.
 *
 * Pass the surrounding typed route [parentEntry] so the nested back dispatcher cannot consume
 * events while that route is inactive. Without a parent entry, [backEnabled] alone controls back.
 * Supply a [controller] when the nested stack must support versioned process restoration.
 *
 * 传入外层类型化路由的 [parentEntry] 可以让嵌套返回分发器在该路由非活跃时不消费返回事件；
 * 未传 parentEntry 时仅由 [backEnabled] 控制返回。需要版本化进程恢复时再传入 [controller]。
 */
public class PixelNestedNavigator(
    /** 提供 `PixelMultiStackNavigator` 嵌套根 entry 的类型化请求。 */
    public val initialRequest: PixelRouteRequest<*, *>,
    /** 记录 `PixelMultiStackNavigator` 的 `vsync` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val vsync: PixelTickerProvider,
    /** 提供 `PixelMultiStackNavigator` 暴露嵌套 Navigator 状态的单宿主控制器；省略即不支持持久化恢复。 */
    public val controller: PixelNestedNavigatorController? = null,
    /** 控制 `PixelMultiStackNavigator` 的 `transitionDuration` 时间参数，单位为声明约定的时间单位。 */
    public val transitionDuration: Duration = 200.milliseconds,
    /** 记录 `PixelMultiStackNavigator` 的 `defaultTransition` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val defaultTransition: PixelRouteTransition = PixelRouteTransition.SlideHorizontal,
    /** 记录 `PixelMultiStackNavigator` 的 `transitionBuilder` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val transitionBuilder: PixelRouteTransitionBuilder? = null,
    /** 表示 `PixelMultiStackNavigator` 当前是否满足 `backEnabled` 对应条件。 */
    public val backEnabled: Boolean = true,
    /** 提供 `PixelMultiStackNavigator` 当前管理的 `parentEntry` 内容。 */
    public val parentEntry: PixelRouteEntry<*, *>? = null,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    /** Creates state that keeps one isolated dispatcher stable across declarative rebuilds. */
    override fun createState(): State<out StatefulWidget> = PixelNestedNavigatorWidgetState()
}

/** State implementation that owns all mounted stack dispatchers and retained child Navigators. */
private class PixelMultiStackNavigatorWidgetState : State<PixelMultiStackNavigator>() {
    /** Stable host capability used to reject stale attachment calls. */
    private val hostOwner: Any = Any()

    /** Per-stack dispatchers retained across active-stack switches. */
    private val stackBackDispatchers: MutableMap<String, PixelBackDispatcher> = linkedMapOf()

    /** Active stack target already reconciled into the visual switch state machine. */
    private var presentedActiveStackId: String? = null

    /** Previous active stack retained only as a paint-only exit layer. */
    private var outgoingStackId: String? = null

    /** Opacity captured for the current outgoing stack at switch start. */
    private var outgoingStartOpacity: Float = 1f

    /** Host-backed controller that fades the visual-only outgoing stack. */
    private var stackSwitchController: PixelAnimationController? = null

    /** Theme curve, including resolved delay, paired with [stackSwitchController]. */
    private var stackSwitchCurve: CurvedAnimation? = null

    /** Motion environment owned by the current outgoing-stack fade segment. */
    private var stackSwitchEnvironment: PixelMultiStackSwitchEnvironment? = null

    /** Last root-return generation consumed by this retained visual presentation. */
    private var observedPredictiveReturnGeneration: Long = 0L

    /** Attaches the controller before any child root attempts to register its Navigator state. */
    override fun initState() {
        bindController()
        presentedActiveStackId = widget.controller.activeStackId
        observedPredictiveReturnGeneration =
            widget.controller.completedPredictiveReturnGeneration
    }

    /** Rebinds controller ownership when the host configuration changes. */
    override fun didUpdateWidget(oldWidget: PixelMultiStackNavigator) {
        if (oldWidget.controller !== widget.controller) {
            disposeStackSwitch()
            outgoingStackId = null
            presentedActiveStackId = widget.controller.activeStackId
            observedPredictiveReturnGeneration =
                widget.controller.completedPredictiveReturnGeneration
            oldWidget.controller.unbindHost(hostOwner)
        }
        bindController()
        stackBackDispatchers.keys.retainAll(widget.stacks.mapTo(linkedSetOf()) { stack -> stack.id })
        if (outgoingStackId !in widget.stacks.map { stack -> stack.id }) {
            outgoingStackId = null
        }
    }

    /** Builds every Navigator under its own back dispatcher and visibility boundary. */
    override fun build(context: BuildContext): Widget {
        context.watch(widget.controller)
        val activeStackId = widget.controller.activeStackId
        val predictiveReturnProgress = widget.controller.activePredictiveReturnProgress
        val completedPredictiveReturnGeneration =
            widget.controller.completedPredictiveReturnGeneration
        val nextSwitchEnvironment = resolveStackSwitchEnvironment(context)
        if (completedPredictiveReturnGeneration != observedPredictiveReturnGeneration) {
            // Predictive progress already rendered the destination; committing must not replay it.
            observedPredictiveReturnGeneration = completedPredictiveReturnGeneration
            disposeStackSwitch()
            outgoingStackId = null
            presentedActiveStackId = activeStackId
        } else if (predictiveReturnProgress != null) {
            // The initial stack is driven directly by platform progress, never by a second clock.
            disposeStackSwitch()
            outgoingStackId = null
            presentedActiveStackId = activeStackId
        } else {
            reconcileStackSwitch(
                activeStackId = activeStackId,
                environment = nextSwitchEnvironment,
            )
        }
        stackSwitchController?.let(context::watch)
        val switchProgress = stackSwitchCurve?.value?.coerceIn(0f, 1f) ?: 1f
        val outgoingOpacity = (outgoingStartOpacity * (1f - switchProgress)).coerceIn(0f, 1f)
        val activeDefinition = widget.stacks.firstOrNull { definition ->
            definition.id == activeStackId
        }
        val outgoingDefinition = outgoingStackId?.let { outgoingId ->
            widget.stacks.firstOrNull { definition -> definition.id == outgoingId }
        }
        // The interactive active stack paints below the exiting visual, which then reveals it.
        // 显式标注类型，避免星投影泛型在列表拼接后失去可推断的元素类型。
        val presentedDefinitions: List<PixelTypedNavigatorStack<*, *>> =
            widget.stacks.filter { definition ->
                definition !== activeDefinition && definition !== outgoingDefinition
            } + listOfNotNull(activeDefinition, outgoingDefinition)
        val orderedDefinitions = presentedDefinitions.distinctBy { definition -> definition.id }
        val stackChildren = orderedDefinitions.map { definition ->
            val stackDispatcher = stackBackDispatchers.getOrPut(definition.id) {
                PixelBackDispatcher()
            }
            widget.controller.attachBackDispatcher(
                owner = hostOwner,
                stackId = definition.id,
                dispatcher = stackDispatcher,
            )
            val childKey = PixelMultiStackNavigatorChildKey(widget.controller, definition.id)
            val navigator = PixelNavigator(
                initialRequest = definition.initialRequest,
                vsync = widget.vsync,
                transitionDuration = widget.transitionDuration,
                defaultTransition = widget.defaultTransition,
                transitionBuilder = widget.transitionBuilder,
                key = childKey,
            ).observeState { navigatorState ->
                widget.controller.attachNavigator(
                    owner = hostOwner,
                    stackId = definition.id,
                    navigatorState = navigatorState,
                )
            }
            PixelNavigatorStackVisibility(
                opacity = when {
                    predictiveReturnProgress != null && definition.id == activeStackId -> {
                        1f - predictiveReturnProgress
                    }
                    predictiveReturnProgress != null &&
                        definition.id == widget.controller.initialStackId -> 1f
                    definition.id == activeStackId -> 1f
                    definition.id == outgoingStackId -> outgoingOpacity
                    else -> 0f
                },
                interactive = definition.id == activeStackId,
                child = PixelBackHost(
                    dispatcher = stackDispatcher,
                    child = navigator,
                    key = "pixel-stack-back-host:${definition.id}",
                ),
                key = "pixel-stack-visibility:${definition.id}",
            )
        }
        val parentIsActive = widget.parentEntry?.lifecycleState?.let { lifecycle ->
            lifecycle == PixelRouteLifecycleState.Active
        } ?: true
        return PixelPredictiveBackHandler(
            enabled = widget.backEnabled && parentIsActive && widget.controller.canHandleBack,
            callback = widget.controller,
            child = Stack(children = stackChildren, key = "pixel-multi-stack-content"),
            key = "pixel-multi-stack-back-handler",
        )
    }

    /** Releases controller references while child Navigator disposal owns route cleanup. */
    override fun dispose() {
        disposeStackSwitch()
        widget.controller.unbindHost(hostOwner)
        stackBackDispatchers.clear()
    }

    /** Validates and binds the current controller to this state instance. */
    private fun bindController() {
        widget.controller.bindHost(
            owner = hostOwner,
            stackIds = widget.stacks.map { stack -> stack.id },
        )
    }

    /** Starts or synchronously completes a visual switch when the controlled active ID changes. */
    private fun reconcileStackSwitch(
        activeStackId: String,
        environment: PixelMultiStackSwitchEnvironment,
    ) {
        val previousActiveStackId = presentedActiveStackId
        if (previousActiveStackId == activeStackId) {
            reconcileActiveStackSwitchEnvironment(environment)
            return
        }
        val previousOutgoingStackId = outgoingStackId
        val previousOutgoingOpacity = (
            outgoingStartOpacity * (1f - (stackSwitchCurve?.value ?: 1f).coerceIn(0f, 1f))
            ).coerceIn(0f, 1f)
        disposeStackSwitch()
        presentedActiveStackId = activeStackId
        outgoingStackId = previousActiveStackId
            ?.takeIf { previousId -> previousId != activeStackId }
            ?.takeIf { previousId -> widget.stacks.any { stack -> stack.id == previousId } }
        outgoingStartOpacity = if (activeStackId == previousOutgoingStackId) {
            // Reversing A→B back to A swaps layer order; complementary alpha preserves pixels.
            1f - previousOutgoingOpacity
        } else {
            1f
        }
        startStackSwitch(environment)
    }

    /** Rebuilds an in-flight fade from its current opacity when inherited Motion values change. */
    private fun reconcileActiveStackSwitchEnvironment(environment: PixelMultiStackSwitchEnvironment) {
        val controller = stackSwitchController ?: return
        if (stackSwitchEnvironment == environment) return
        val visualOpacity = (
            outgoingStartOpacity * (1f - (stackSwitchCurve?.value ?: 1f).coerceIn(0f, 1f))
            ).coerceIn(0f, 1f)
        controller.dispose()
        stackSwitchController = null
        stackSwitchCurve = null
        stackSwitchEnvironment = null
        outgoingStartOpacity = visualOpacity
        startStackSwitch(environment)
    }

    /** Starts one resolved outgoing fade or commits the active stack synchronously. */
    private fun startStackSwitch(environment: PixelMultiStackSwitchEnvironment) {
        val resolved = environment.resolved
        val totalDuration = resolved.delay + resolved.duration
        if (
            outgoingStackId == null ||
            widget.defaultTransition == PixelRouteTransition.None ||
            environment.vsync == null ||
            resolved.isImmediate ||
            resolved.transition == PixelMotionTransitionPreset.None ||
            totalDuration <= Duration.ZERO
        ) {
            outgoingStackId = null
            disposeStackSwitch()
            return
        }
        val provider = checkNotNull(environment.vsync)
        val controller = PixelAnimationController(duration = totalDuration, vsync = provider)
        stackSwitchEnvironment = environment
        stackSwitchController = controller
        stackSwitchCurve = CurvedAnimation(
            parent = controller,
            curve = multiStackDelayedCurve(
                delay = resolved.delay,
                duration = resolved.duration,
                curve = resolved.curve,
            ),
        )
        controller.addListener {
            if (
                stackSwitchController === controller &&
                controller.status == PixelAnimationStatus.Completed
            ) {
                outgoingStackId = null
                disposeStackSwitch()
                setState { Unit }
            }
        }
        controller.forward(from = 0f)
    }

    /** Resolves the current Host clock, settings, and route token for stack switching. */
    private fun resolveStackSwitchEnvironment(context: BuildContext): PixelMultiStackSwitchEnvironment {
        val scope = PixelMotionScope.maybeOf(context)
        val settings = scope?.settings ?: PixelMotionSettings.Default
        val resolved = PixelMotionTheme.of(context).route
            .copy(duration = widget.transitionDuration)
            .resolve(settings)
        return PixelMultiStackSwitchEnvironment(
            vsync = scope?.vsync,
            settings = settings,
            resolved = resolved,
        )
    }

    /** Disposes the component-owned stack switch ticker without touching retained child stacks. */
    private fun disposeStackSwitch() {
        stackSwitchController?.dispose()
        stackSwitchController = null
        stackSwitchCurve = null
        stackSwitchEnvironment = null
    }
}

/** Immutable Motion inputs used to detect and retarget one multi-stack switch segment. */
private data class PixelMultiStackSwitchEnvironment(
    /** Host-owned ticker provider, or null outside a Host Motion scope. */
    val vsync: PixelTickerProvider?,
    /** Platform or application motion settings applied to the route token. */
    val settings: PixelMotionSettings,
    /** Route token after scale and reduce-motion resolution. */
    val resolved: PixelResolvedMotion,
)

/** Encodes the route token's optional delay for a retained multi-stack switch. */
private fun multiStackDelayedCurve(
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

/** State implementation that bridges one nested Navigator into its parent's back dispatcher. */
private class PixelNestedNavigatorWidgetState : State<PixelNestedNavigator>() {
    /** Stable capability proving ownership of the optional typed nested controller. */
    private val hostOwner: Any = Any()

    /** Dispatcher that receives only back handlers mounted inside the nested Navigator subtree. */
    private val nestedBackDispatcher: PixelBackDispatcher = PixelBackDispatcher()

    /** Whether the inner tree currently has a concrete handler above its root. */
    private var nestedCanHandleBack: Boolean = false

    /** Listener that removes the parent bridge when the nested tree cannot consume back. */
    private var availabilityRegistration: PixelBackRegistration? = null

    /**
     * 当前已绑定到该挂载宿主的类型化控制器；未传入控制器时为 `null`。
     *
     * Typed controller currently bound to this mounted host, or `null` when none was supplied.
     */
    private var boundController: PixelNestedNavigatorController? = null

    /** Parent-facing callback that forwards one complete session into the isolated dispatcher. */
    private val predictiveBackForwarder: PixelPredictiveBackCallback =
        object : PixelPredictiveBackCallback {
            /** Locks the deepest accepting handler inside this nested Navigator. */
            override fun onBackStarted(event: PixelPredictiveBackEvent): Boolean {
                return nestedBackDispatcher.startPredictiveBack(event)
            }

            /** Sends progress only to the handler selected by the nested dispatcher. */
            override fun onBackProgressed(event: PixelPredictiveBackEvent) {
                nestedBackDispatcher.updatePredictiveBack(event)
            }

            /** Cancels the nested preview without mutating its route stack. */
            override fun onBackCancelled() {
                nestedBackDispatcher.cancelPredictiveBack()
            }

            /** Commits the exact nested session that previously accepted start. */
            override fun onBackCommitted(): Boolean {
                return nestedBackDispatcher.commitPredictiveBack()
            }

            /** Sends discrete back only into this nested Navigator subtree. */
            override fun onBackInvoked(): Boolean = nestedBackDispatcher.handleBack()
        }

    /** Observes inner handler availability before the first child Navigator registration. */
    override fun initState() {
        // 未声明控制器的嵌套 Navigator 不建立任何宿主绑定。
        val controller = widget.controller
        controller?.bindHost(hostOwner)
        boundController = controller
        availabilityRegistration = nestedBackDispatcher.addAvailabilityListener { available ->
            if (nestedCanHandleBack == available) return@addAvailabilityListener
            setState { nestedCanHandleBack = available }
        }
    }

    /** Rebinds controller ownership without recreating the already retained child Navigator. */
    override fun didUpdateWidget(oldWidget: PixelNestedNavigator) {
        // New ownership is validated before the previous controller releases this valid host.
        val nextController = widget.controller
        if (nextController === boundController) return
        nextController?.bindHost(hostOwner)
        boundController?.unbindHost(hostOwner)
        boundController = nextController
    }

    /** Builds the inner Navigator under an isolated dispatcher and one parent-facing handler. */
    override fun build(context: BuildContext): Widget {
        val parentIsActive = widget.parentEntry?.lifecycleState?.let { lifecycle ->
            lifecycle == PixelRouteLifecycleState.Active
        } ?: true
        // Child key remains stable so declarative rebuilds retain the same nested stack.
        val navigator = PixelNavigator(
            initialRequest = widget.initialRequest,
            vsync = widget.vsync,
            transitionDuration = widget.transitionDuration,
            defaultTransition = widget.defaultTransition,
            transitionBuilder = widget.transitionBuilder,
            key = "pixel-nested-navigator-content",
        ).observeState { navigatorState ->
            // 无控制器时观察者只是空转，不需要额外分支。
            boundController?.attachNavigator(
                owner = hostOwner,
                navigatorState = navigatorState,
            )
        }
        return PixelPredictiveBackHandler(
            enabled = widget.backEnabled && parentIsActive && nestedCanHandleBack,
            callback = predictiveBackForwarder,
            child = PixelBackHost(
                dispatcher = nestedBackDispatcher,
                child = navigator,
                key = "pixel-nested-navigator-back-host",
            ),
            key = "pixel-nested-navigator-back-handler",
        )
    }

    /** Removes the availability observer after the nested subtree releases its own handlers. */
    override fun dispose() {
        availabilityRegistration?.dispose()
        availabilityRegistration = null
        boundController?.unbindHost(hostOwner)
        boundController = null
    }
}

/** Key that recreates child Navigator states when a different controller owns the same stack ID. */
private data class PixelMultiStackNavigatorChildKey(
    /** Controller identity owning the child state. */
    val controller: PixelMultiStackNavigatorController,
    /** Stable stack identifier within [controller]. */
    val stackId: String,
)

/**
 * Retained visibility boundary used by multi-stack children.
 *
 * [Opacity] blocks rendering, hit testing, target export, and semantics at zero. The internal
 * element marker additionally removes inactive descendants from test finder collection without
 * unmounting them.
 */
private class PixelNavigatorStackVisibility(
    /** Current paint opacity, including a possible visual-only outgoing fade. */
    private val opacity: Float,
    /** Whether this stack is the unique active interaction and semantics owner. */
    private val interactive: Boolean,
    /** Always-mounted Navigator subtree. */
    private val child: Widget,
    /** Stable stack key used by keyed sibling reconciliation. */
    override val key: Any? = null,
) : StatelessWidget(key = key), ElementSubtreeVisibility {
    /** Whether test widget collection may descend into this mounted stack. */
    override val exposesSubtreeToWidgetCollection: Boolean
        get() = interactive

    /** Applies independent paint and interaction policies without changing child identity. */
    override fun build(context: BuildContext): Widget {
        return Opacity(
            opacity = opacity,
            child = VisualOnlyWidget(
                visualOnly = !interactive,
                child = child,
                key = "pixel-stack-visual-only",
            ),
            key = "pixel-stack-opacity",
        )
    }
}
