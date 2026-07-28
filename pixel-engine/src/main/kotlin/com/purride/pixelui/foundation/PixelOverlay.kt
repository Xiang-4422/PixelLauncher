package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelAnimationStatus
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.internal.ModalInteractionScopeWidget
import com.purride.pixelui.internal.VisualOnlyWidget
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

/** 一个关闭来源发生时路由应执行的动作。 */
public enum class PixelOverlayDismissAction {
    /** Route 自身忽略该来源；Back 可以继续向下派发，指针仍受 modal 隔离约束。 */
    Ignore,

    /** 消费该来源，但保持当前 entry 活跃。 */
    Consume,

    /** 消费该来源并关闭当前 entry。 */
    Dismiss,
}

/**
 * 定义返回键和遮罩点击如何影响一个 overlay route。
 *
 * @property back 系统 Back 或键盘 Escape/Back 的处理方式。
 * @property barrierTap 该 route 遮罩被点击时的处理方式。
 */
public data class PixelOverlayDismissPolicy(
    public val back: PixelOverlayDismissAction = PixelOverlayDismissAction.Dismiss,
    public val barrierTap: PixelOverlayDismissAction = PixelOverlayDismissAction.Dismiss,
) {
    /** 常用的关闭策略预设。 */
    public companion object {
        /** Back 和遮罩点击都会关闭 route。 */
        public val Dismissible: PixelOverlayDismissPolicy = PixelOverlayDismissPolicy()

        /** Back 和遮罩点击都被消费，但 route 不会关闭。 */
        public val Locked: PixelOverlayDismissPolicy = PixelOverlayDismissPolicy(
            back = PixelOverlayDismissAction.Consume,
            barrierTap = PixelOverlayDismissAction.Consume,
        )

        /** 不参与 Back 和遮罩关闭链。 */
        public val Passive: PixelOverlayDismissPolicy = PixelOverlayDismissPolicy(
            back = PixelOverlayDismissAction.Ignore,
            barrierTap = PixelOverlayDismissAction.Ignore,
        )
    }
}

/**
 * 在 route 内容下方绘制的全屏遮罩。
 *
 * @property color 遮罩的 ARGB 颜色；透明色仍可以承担指针隔离。
 */
public data class PixelOverlayBarrier(
    public val color: PixelColor = PixelColor.fromArgb(160, 0, 0, 0),
)

/** Overlay entry 从可交互到视觉完全移除的生命周期。 */
public enum class PixelOverlayLifecycle {
    /** Entry 仍在 controller 的逻辑栈内。 */
    Active,

    /** Entry 已逻辑关闭，但可能还在绘制退出帧。 */
    Removing,

    /** Entry 已从所有已挂载 host 的呈现树移除。 */
    Disposed,
}

/** 没有产生业务结果时的稳定关闭原因。 */
public enum class PixelOverlayDismissReason {
    /** 调用方通过 route 句柄主动关闭。 */
    Handle,

    /** 调用 controller 的 [PixelOverlayController.dismissTop] 或其他业务关闭入口。 */
    Programmatic,

    /** 系统 Back 关闭了当前 route。 */
    Back,

    /** 用户点击了 route 遮罩。 */
    Barrier,

    /** 焦点系统的 Escape/Back 请求关闭 route。 */
    DismissRequest,

    /** Controller 执行了 [PixelOverlayController.clear]。 */
    Clear,

    /** Entry 的显示时限结束，例如 toast/snackbar 自动超时。 */
    Timeout,

    /** 调用方观察到承载 host 释放后，显式选择结束 route。 */
    HostDisposed,

    /** 锚点离开 retained tree，依附它的 popup 无法继续定位。 */
    AnchorRemoved,
}

/** 一个 typed route 只会产生一次的终局。 */
public sealed interface PixelOverlayOutcome<out R> {
    /**
     * Route 正常完成并返回业务值。
     *
     * @property result 调用方定义的 typed 结果。
     */
    public data class Completed<R>(public val result: R) : PixelOverlayOutcome<R>

    /**
     * Route 没有业务结果地关闭。
     *
     * @property reason 触发关闭的稳定来源。
     */
    public data class Dismissed(
        public val reason: PixelOverlayDismissReason,
    ) : PixelOverlayOutcome<Nothing>
}

/** 可由公开 route 选择的呈现动画。 */
public enum class PixelOverlayMotion {
    /** 逻辑关闭时同步移除呈现。 */
    None,

    /** 使用 MotionTheme 的 dialog 进入/退出 token。 */
    Dialog,
}

/**
 * 可由 [PixelOverlayController] 呈现并返回 typed 结果的统一 popup route。
 *
 * @property content Route 的声明式内容。带可关闭 barrier 的自定义定位布局应在真实表面处使用
 * [PixelOverlaySurface]，避免表面空白区域穿透到 barrier。
 * @property layer Route 所在的显式绘制层。
 * @property dismissPolicy Back 与遮罩点击策略。
 * @property barrier 可选的全屏遮罩，总是绘制在 [content] 下方。
 * @property modal 是否隔离背景的焦点、指针、文本输入和无障碍交互。
 * @property motion 插入和移除呈现时使用的动画类型。
 * @property onOutcome Route 首次完成或关闭且 presentation 全部卸载后调用一次；
 * controller 保持逻辑关闭顺序，不让更快的退出越过更早的结果。
 */
public class PixelPopupRoute<R>(
    public val content: Widget,
    public val layer: PixelOverlayLayer = PixelOverlayLayer.Popup,
    public val dismissPolicy: PixelOverlayDismissPolicy = PixelOverlayDismissPolicy.Dismissible,
    public val barrier: PixelOverlayBarrier? = null,
    public val modal: Boolean = barrier != null,
    public val motion: PixelOverlayMotion = PixelOverlayMotion.None,
    public val onOutcome: (PixelOverlayOutcome<R>) -> Unit = {},
)

/**
 * Typed route 的稳定控制句柄。
 *
 * [complete] 与 [dismiss] 中只有首次调用会改变状态并产生
 * [outcome]；其余调用返回 `false`。
 */
public interface PixelOverlayEntry<R> : PixelOverlayHandle {
    /** Entry 当前的逻辑/呈现生命周期。 */
    public val lifecycle: PixelOverlayLifecycle

    /** 已决定的唯一结果；活跃时为 `null`，进入 Removing 后即可同步读取。 */
    public val outcome: PixelOverlayOutcome<R>?

    /** 以 typed [result] 完成 route。 */
    public fun complete(result: R): Boolean
}

/**
 * 拥有 overlay entry、分层、dismiss 策略和 typed outcome 的控制器。
 *
 * 逻辑关闭会立即从 [size] 和 Back 栈移除 entry；所有 hosted entry 都会在实际 presentation
 * 子树卸载后从 `Removing` 进入 `Disposed`。Outcome callback 按逻辑关闭 FIFO 延迟派发，因此
 * 更快的同步退出不会越过更早开始的 dialog 退出；没有 Host 时则可立即终结。
 */
public class PixelOverlayController : ChangeNotifier() {
    /** Monotonic insertion order used as the deterministic tie-breaker within one layer. */
    private var nextInsertionOrder: Long = 1L

    /** Logically active overlay entries in paint order. */
    private var items: List<PixelOverlayItem> = emptyList()

    /** Host identities whose keyed presentation wrappers are actually mounted for each entry. */
    private val presentingHosts: MutableMap<Long, MutableSet<Any>> = linkedMapOf()

    /** Removed entries waiting for every host that may still own a presentation subtree. */
    private val removingRecords: MutableMap<Long, PixelOverlayRemovalRecord> = linkedMapOf()

    /** Logical-close FIFO that delays callbacks until each presentation is fully unmounted. */
    private val pendingOutcomeDeliveries: MutableList<DefaultPixelOverlayEntry<*>> = mutableListOf()

    /** Reentrancy guard that keeps callback-triggered closes behind earlier outcomes. */
    private var drainingOutcomes: Boolean = false

    /** Generation used by [clear] to force hosts to drop retained exit visuals synchronously. */
    internal var clearGeneration: Long = 0L
        private set

    /**
     * 当前 overlay 数量。
     */
    public val size: Int
        get() = items.size

    /**
     * 显示一个 typed popup route。
     *
     * 返回的 entry 在关闭后仍保留最终 [PixelOverlayEntry.outcome]。
     */
    public fun <R> show(route: PixelPopupRoute<R>): PixelOverlayEntry<R> = append(route)

    /**
     * 显示一个自定义 overlay widget。
     */
    public fun show(widget: Widget): PixelOverlayHandle {
        return show(
            PixelPopupRoute<Unit>(
                content = widget,
                layer = PixelOverlayLayer.Popup,
            ),
        )
    }

    /**
     * 显示一个居中的像素 toast。
     */
    public fun showToast(
        message: String,
        fillColor: PixelColor = PixelColor.Black,
        textStyle: PixelTextStyle = PixelTextStyle.Default,
    ): PixelOverlayHandle {
        return show(
            PixelPopupRoute<Unit>(
                content = Toast(
                    message = message,
                    fillColor = fillColor,
                    textStyle = textStyle,
                ),
                layer = PixelOverlayLayer.Notification,
                dismissPolicy = PixelOverlayDismissPolicy.Passive,
            ),
        )
    }

    /**
     * 显示一个居中的像素 dialog。
     */
    public fun showDialog(
        title: Widget? = null,
        content: Widget,
        actions: List<Widget> = emptyList(),
        fillColor: PixelColor = PixelColor.Black,
        borderColor: PixelColor = PixelColor.White,
    ): PixelOverlayHandle {
        /** Assigned immediately after append so the retained dismiss action targets this entry. */
        var dialogHandle: PixelOverlayHandle? = null
        val handle = show(
            PixelPopupRoute<Unit>(
                content = Dialog(
                    title = title,
                    content = content,
                    actions = actions,
                    fillColor = fillColor,
                    borderColor = borderColor,
                    onDismissRequest = {
                        dialogHandle?.dismiss(PixelOverlayDismissReason.Programmatic)
                    },
                    modal = false,
                ),
                layer = PixelOverlayLayer.Modal,
                dismissPolicy = PixelOverlayDismissPolicy.Dismissible,
                barrier = PixelOverlayBarrier(color = PixelColor.Transparent),
                modal = true,
                motion = PixelOverlayMotion.Dialog,
            ),
        )
        dialogHandle = handle
        return handle
    }

    /**
     * 显示一个贴底的像素 snackbar。
     */
    public fun showSnackbar(
        message: String,
        action: Widget? = null,
        fillColor: PixelColor = PixelColor.fromRgb(40, 40, 40),
        textStyle: PixelTextStyle = PixelTextStyle.Default,
    ): PixelOverlayHandle {
        return show(
            PixelPopupRoute<Unit>(
                content = Positioned(
                    left = 0,
                    right = 0,
                    bottom = 0,
                    child = Snackbar(
                        message = message,
                        action = action,
                        fillColor = fillColor,
                        textStyle = textStyle,
                    ),
                ),
                layer = PixelOverlayLayer.Notification,
                dismissPolicy = PixelOverlayDismissPolicy.Passive,
            ),
        )
    }

    /**
     * 关闭当前最上层 overlay。
     */
    public fun dismissTop(): Boolean {
        val top = sortedItems().lastOrNull() ?: return false
        return dismissItem(top, PixelOverlayDismissReason.Programmatic)
    }

    /**
     * 清空所有 overlay。
     */
    public fun clear() {
        /** Topmost-first snapshot that defines deterministic outcome callback order. */
        val closingItems = sortedItems().asReversed().filter { item ->
            item.entry.beginDismiss(PixelOverlayDismissReason.Clear)
        }
        items = emptyList()
        closingItems.forEach { item ->
            pendingOutcomeDeliveries += item.entry
            registerRemoval(item)
        }
        clearGeneration += 1L
        notifyListenersAndDrainOutcomes()
    }

    /** Returns the immutable logical entry snapshot consumed by [PixelOverlayHost]. */
    internal fun itemsSnapshot(): List<PixelOverlayItem> = sortedItems()

    /** Adds one typed logical route and returns its permanent-identity entry. */
    private fun <R> append(route: PixelPopupRoute<R>): PixelOverlayEntry<R> {
        /** Process-wide identity prevents State migration across controller switches and id reuse. */
        val identity = nextPixelOverlayIdentity.getAndIncrement()
        /** Typed entry that owns exactly one terminal outcome callback. */
        val entry = DefaultPixelOverlayEntry(
            controller = this,
            identity = identity,
            onOutcome = route.onOutcome,
        )
        /** Immutable route snapshot used by every attached host. */
        val item = PixelOverlayItem(
            identity = identity,
            insertionOrder = nextInsertionOrder++,
            widget = route.content,
            layer = route.layer,
            dismissPolicy = route.dismissPolicy,
            barrier = route.barrier,
            modal = route.modal,
            motion = route.motion,
            entry = entry,
        )
        items = items + item
        notifyListeners()
        return entry
    }

    /** Completes [entry] with a typed result if it is still logically active. */
    internal fun <R> completeEntry(entry: DefaultPixelOverlayEntry<R>, result: R): Boolean {
        val item = items.firstOrNull { candidate -> candidate.identity == entry.identity } ?: return false
        if (!entry.beginComplete(result)) return false
        removePreparedItem(item)
        return true
    }

    /** Dismisses [entry] with an explicit reason if it is still logically active. */
    internal fun dismissEntry(
        entry: DefaultPixelOverlayEntry<*>,
        reason: PixelOverlayDismissReason,
    ): Boolean {
        val item = items.firstOrNull { candidate -> candidate.identity == entry.identity } ?: return false
        return dismissItem(item, reason)
    }

    /** Applies the topmost eligible Back policy and reports whether Back was consumed. */
    internal fun handleBack(): Boolean = handleDismissalRequest(PixelOverlayDismissReason.Back)

    /** Applies canonical route policy to an Escape or gamepad dismissal request. */
    internal fun handleDismissRequest(): Boolean =
        handleDismissalRequest(PixelOverlayDismissReason.DismissRequest)

    /** Scans canonical top-to-bottom order for the first route handling [reason]. */
    private fun handleDismissalRequest(reason: PixelOverlayDismissReason): Boolean {
        sortedItems().asReversed().forEach { item ->
            when (item.dismissPolicy.back) {
                PixelOverlayDismissAction.Ignore -> Unit
                PixelOverlayDismissAction.Consume -> return true
                PixelOverlayDismissAction.Dismiss -> {
                    dismissItem(item, reason)
                    return true
                }
            }
        }
        return false
    }

    /** True when at least one active route consumes or dismisses Back. */
    internal fun hasBackConsumer(): Boolean = items.any { item ->
        item.dismissPolicy.back != PixelOverlayDismissAction.Ignore
    }

    /** Records an actually mounted keyed presentation for exact multi-host removal tracking. */
    internal fun presentationMounted(identity: Long, hostIdentity: Any) {
        presentingHosts.getOrPut(identity) { linkedSetOf() } += hostIdentity
    }

    /** Unregisters one actual presentation and acknowledges removal after descendant disposal. */
    internal fun presentationDisposed(identity: Long, hostIdentity: Any) {
        presentingHosts[identity]?.let { hosts ->
            hosts -= hostIdentity
            if (hosts.isEmpty()) presentingHosts.remove(identity)
        }
        val record = removingRecords[identity] ?: return
        if (!record.pendingHosts.remove(hostIdentity)) return
        if (record.pendingHosts.isNotEmpty()) return
        removingRecords.remove(identity)
        record.item.entry.markDisposed()
        drainOutcomeDeliveries()
    }

    /** Handles a barrier action without allowing stale retained presentations to close newer entries. */
    internal fun handleBarrier(identity: Long) {
        val item = items.firstOrNull { candidate -> candidate.identity == identity } ?: return
        when (item.dismissPolicy.barrierTap) {
            PixelOverlayDismissAction.Ignore -> Unit
            PixelOverlayDismissAction.Consume -> Unit
            PixelOverlayDismissAction.Dismiss -> dismissItem(item, PixelOverlayDismissReason.Barrier)
        }
    }

    /** Begins a reasoned dismissal and then performs the shared logical removal path. */
    private fun dismissItem(item: PixelOverlayItem, reason: PixelOverlayDismissReason): Boolean {
        if (!item.entry.beginDismiss(reason)) return false
        removePreparedItem(item)
        return true
    }

    /** Removes an entry whose terminal outcome has already been chosen exactly once. */
    private fun removePreparedItem(item: PixelOverlayItem) {
        items = items.filterNot { candidate -> candidate.identity == item.identity }
        pendingOutcomeDeliveries += item.entry
        registerRemoval(item)
        notifyListenersAndDrainOutcomes()
    }

    /** Fans out the mutation and drains ready outcomes even when an external listener fails. */
    private fun notifyListenersAndDrainOutcomes() {
        /** First failure retained while both notification and outcome cleanup complete. */
        var firstFailure: Throwable? = null
        try {
            notifyListeners()
        } catch (failure: Throwable) {
            firstFailure = failure
        }
        try {
            drainOutcomeDeliveries()
        } catch (failure: Throwable) {
            /** Existing listener failure remains primary; outcome failure becomes suppressed. */
            val primary = firstFailure
            if (primary == null) {
                firstFailure = failure
            } else if (primary !== failure) {
                primary.addSuppressed(failure)
            }
        }
        firstFailure?.let { failure -> throw failure }
    }

    /** Waits for every attached host, or disposes immediately when no presentation can exist. */
    private fun registerRemoval(item: PixelOverlayItem) {
        /** Hosts captured from mounted wrappers, excluding scheduled-but-never-built presentations. */
        val mountedHosts = presentingHosts[item.identity].orEmpty()
        if (mountedHosts.isEmpty()) {
            item.entry.markDisposed()
            return
        }
        removingRecords[item.identity] = PixelOverlayRemovalRecord(
            item = item,
            pendingHosts = mountedHosts.toMutableSet(),
        )
    }

    /** Returns active entries in canonical paint order. */
    private fun sortedItems(): List<PixelOverlayItem> = items.sortedWith(pixelOverlayItemComparator)

    /** Delivers ready outcomes in logical-close order after their presentation is unmounted. */
    private fun drainOutcomeDeliveries() {
        if (drainingOutcomes) return
        drainingOutcomes = true
        /** First user callback failure retained until all exactly-once callbacks have run. */
        var firstFailure: Throwable? = null
        try {
            while (pendingOutcomeDeliveries.firstOrNull()?.lifecycle == PixelOverlayLifecycle.Disposed) {
                /** Queue head removed before user code so callback reentrancy cannot redeliver it. */
                val entry = pendingOutcomeDeliveries.removeAt(0)
                try {
                    entry.dispatchOutcome()
                } catch (failure: Throwable) {
                    /** Existing callback failure receiving only independent later failures. */
                    val retainedFailure = firstFailure
                    if (retainedFailure == null) {
                        firstFailure = failure
                    } else if (retainedFailure !== failure) {
                        retainedFailure.addSuppressed(failure)
                    }
                }
            }
        } finally {
            // Reentrant or later controller mutations must never observe a stranded drain guard.
            drainingOutcomes = false
        }
        firstFailure?.let { failure -> throw failure }
    }

    /**
     * 从当前 build context 读取最近的 overlay controller。
     */
    public companion object {
        /**
         * 返回最近的 [PixelOverlayHost] controller；没有 host 时返回 null。
         */
        public fun maybeOf(context: BuildContext): PixelOverlayController? {
            return context.dependOnInheritedWidgetOfExactType(PixelOverlayScope::class)?.controller
        }

        /**
         * 返回最近的 [PixelOverlayHost] controller；没有 host 时抛出错误。
         */
        public fun of(context: BuildContext): PixelOverlayController {
            return maybeOf(context) ?: error("当前上下文里没有 PixelOverlayHost。")
        }
    }
}

/**
 * show 调用返回的关闭句柄。
 */
public interface PixelOverlayHandle {
    /**
     * 以显式 [reason] 关闭当前 overlay；重复调用返回 `false`。
     */
    public fun dismiss(reason: PixelOverlayDismissReason): Boolean
}

/**
 * 把 [child] 的真实布局范围标记为 overlay 表面并吸收未被子控件处理的点击。
 *
 * 吸收目标先于后代真实点击目标导出，因此按钮等子控件仍优先处理；表面 padding、空白和
 * disabled 控件区域则不会穿透到下方的 dismiss barrier。若 route 的 [PixelPopupRoute.content]
 * 使用 `Center`、`Positioned` 或其他全屏定位容器，应在定位容器内部、紧贴实际表面调用本函数。
 * 标准 [Dialog]、[BottomSheet] 和 [Popover] 已自动应用，无需重复包装。
 *
 * @param child 具有实际视觉表面尺寸的子树。
 * @param key 可选的 retained identity。
 */
public fun PixelOverlaySurface(
    child: Widget,
    key: Any? = null,
): Widget = GestureDetector(
    child = child,
    onTap = {},
    key = key,
)

/**
 * 在子树顶部承载 overlay 的 host。
 */
public fun PixelOverlayHost(
    controller: PixelOverlayController,
    child: Widget,
    key: Any? = null,
): Widget {
    return PixelOverlayHostWidget(
        controller = controller,
        child = child,
        key = key,
    )
}

/** Immutable logical overlay entry owned by [PixelOverlayController]. */
internal data class PixelOverlayItem(
    /** Process-wide identity that is never reused by another controller. */
    val identity: Long,
    /** Monotonic insertion order within the owning controller. */
    val insertionOrder: Long,
    /** Declarative overlay subtree. */
    val widget: Widget,
    /** Explicit paint layer chosen by the route. */
    val layer: PixelOverlayLayer,
    /** Back and barrier behavior snapshot. */
    val dismissPolicy: PixelOverlayDismissPolicy,
    /** Optional barrier configuration below [widget]. */
    val barrier: PixelOverlayBarrier?,
    /** Whether this item owns modal focus and render interaction. */
    val modal: Boolean,
    /** Presentation motion selected by the route. */
    val motion: PixelOverlayMotion,
    /** Stable typed entry retained after logical removal. */
    val entry: DefaultPixelOverlayEntry<*>,
)

/** One asynchronous removal and the hosts that still retain its visual presentation. */
private data class PixelOverlayRemovalRecord(
    /** Removed item whose entry remains in [PixelOverlayLifecycle.Removing]. */
    val item: PixelOverlayItem,
    /** Host identities that must acknowledge final visual removal. */
    val pendingHosts: MutableSet<Any>,
)

/** Host-retained presentation that may outlive its logically active controller item. */
private data class PixelOverlayPresentation(
    /** Logical entry configuration retained for paint. */
    val item: PixelOverlayItem,
    /** True while the entry remains logically active and interactive. */
    val visible: Boolean,
)

/** Stable Element identity for every presentation, including unkeyed custom content. */
private data class PixelOverlayPresentationKey(
    /** Process-wide overlay identifier. */
    val identity: Long,
)

/** Stable child identity nested below one keyed presentation shell. */
private data class PixelOverlayContentKey(
    /** Process-wide overlay identifier. */
    val identity: Long,
)

/** Stable motion-state identity nested below one presentation shell. */
private data class PixelOverlayMotionKey(
    /** Process-wide overlay identifier. */
    val identity: Long,
)

/** Stable barrier identity used by pointer and semantics targets. */
private data class PixelOverlayBarrierKey(
    /** Process-wide overlay identifier. */
    val identity: Long,
)

/** Stable modal focus identity for one route presentation. */
private data class PixelOverlayModalFocusKey(
    /** Process-wide overlay identifier. */
    val identity: Long,
)

/** Stable canonical-order focus-scope identity for one route presentation. */
private data class PixelOverlayFocusLayerKey(
    /** Process-wide overlay identifier. */
    val identity: Long,
)

/** Controller-owned implementation that chooses and dispatches exactly one typed outcome. */
internal class DefaultPixelOverlayEntry<R>(
    /** Owning controller used by complete and dismissal calls. */
    private val controller: PixelOverlayController,
    /** 进程内唯一的 retained 身份，任何其他 controller 都不会复用。 */
    internal val identity: Long,
    /** User callback invoked after the logical stack has already changed. */
    private val onOutcome: (PixelOverlayOutcome<R>) -> Unit,
) : PixelOverlayEntry<R> {
    /** Mutable lifecycle advanced only by the owning controller and attached hosts. */
    override var lifecycle: PixelOverlayLifecycle = PixelOverlayLifecycle.Active
        private set

    /** Mutable terminal outcome retained for callers after disposal. */
    override var outcome: PixelOverlayOutcome<R>? = null
        private set

    /** Guards the user callback independently from lifecycle acknowledgement. */
    private var outcomeDispatched: Boolean = false

    /** Completes this entry with one typed business value. */
    override fun complete(result: R): Boolean = controller.completeEntry(this, result)

    /** Dismisses this entry with a caller-selected reason. */
    override fun dismiss(reason: PixelOverlayDismissReason): Boolean {
        return controller.dismissEntry(this, reason)
    }

    /** Chooses a typed completion outcome without dispatching user code yet. */
    internal fun beginComplete(result: R): Boolean {
        return beginRemoval(PixelOverlayOutcome.Completed(result))
    }

    /** Chooses a reasoned dismissal outcome without dispatching user code yet. */
    internal fun beginDismiss(reason: PixelOverlayDismissReason): Boolean {
        return beginRemoval(PixelOverlayOutcome.Dismissed(reason))
    }

    /** Advances Active to Removing and stores the only allowed outcome. */
    private fun beginRemoval(nextOutcome: PixelOverlayOutcome<R>): Boolean {
        if (lifecycle != PixelOverlayLifecycle.Active) return false
        lifecycle = PixelOverlayLifecycle.Removing
        outcome = nextOutcome
        return true
    }

    /** Advances an already removed entry to its terminal presentation state. */
    internal fun markDisposed() {
        if (lifecycle == PixelOverlayLifecycle.Removing) {
            lifecycle = PixelOverlayLifecycle.Disposed
        }
    }

    /** Invokes the retained user callback once after every host presentation has unmounted. */
    internal fun dispatchOutcome() {
        if (outcomeDispatched) return
        val terminalOutcome = outcome ?: return
        outcomeDispatched = true
        onOutcome(terminalOutcome)
    }
}

/** Process-wide monotonic source for presentation identity across all controllers. */
private val nextPixelOverlayIdentity: AtomicLong = AtomicLong(1L)

/** Canonical bottom-to-top ordering shared by controller snapshots and retained hosts. */
private val pixelOverlayItemComparator: Comparator<PixelOverlayItem> =
    compareBy<PixelOverlayItem>({ item -> item.layer.ordinal }, { item -> item.insertionOrder })

private class PixelOverlayHostWidget(
    /** Controller whose logical entries are presented above [child]. */
    val controller: PixelOverlayController,
    /** Stable application content below every overlay. */
    val child: Widget,
    override val key: Any?,
) : StatefulWidget(key = key) {
    /** Creates retained host state capable of preserving dialog exit visuals. */
    override fun createState(): State<out StatefulWidget> = PixelOverlayHostState()
}

/** Retains removed entries through visual exit and acknowledges their actual subtree unmount. */
private class PixelOverlayHostState : State<PixelOverlayHostWidget>() {
    /** Permanent identity used to acknowledge retained removals for this exact host State. */
    private val hostIdentity: Any = Any()

    /** Current logical and exit-only presentations in deterministic paint order. */
    private var presentations: List<PixelOverlayPresentation> = emptyList()

    /** Last force-clear generation consumed from the active controller. */
    private var observedClearGeneration: Long = -1L

    /** Drops presentations when a rebuilt host switches to another controller instance. */
    override fun didUpdateWidget(oldWidget: PixelOverlayHostWidget) {
        if (oldWidget.controller !== widget.controller) {
            presentations = emptyList()
            observedClearGeneration = -1L
        }
    }

    /** Reconciles logical entries, then layers retained exit visuals above application content. */
    override fun build(context: BuildContext): Widget {
        context.watch(widget.controller)
        synchronizePresentations(context)
        /** Canonical top modal chosen from the same layer/insertion order used for paint and Back. */
        val activeModalIdentity = presentations.asReversed()
            .firstOrNull { presentation -> presentation.visible && presentation.item.modal }
            ?.item
            ?.identity
        /** Presentations share one canonical modal owner instead of competing by mount order. */
        val overlays = presentations.mapIndexed { presentationOrder, presentation ->
            buildPresentation(
                presentation = presentation,
                ownsModal = presentation.item.identity == activeModalIdentity,
                overlayOrder = presentationOrder.toLong(),
            )
        }
        val children = buildList {
            add(widget.child)
            if (overlays.isNotEmpty()) {
                /** Canonically ordered visual stack shared by platform and normalized key paths. */
                val overlayStack = Stack(children = overlays)
                /** Host-level key interceptor covers non-modal routes without requesting focus. */
                val keyboardDismissPresentation = OverlayDismissKeyHandlerFactory.create(
                    enabled = widget.controller.hasBackConsumer(),
                    onDismissRequest = { widget.controller.handleDismissRequest() },
                    child = overlayStack,
                    key = "pixel-overlay-dismiss-key",
                )
                add(
                    PixelBackHandler(
                        enabled = widget.controller.hasBackConsumer(),
                        onBack = { widget.controller.handleBack() },
                        child = keyboardDismissPresentation,
                        key = "pixel-overlay-back",
                    ),
                )
            }
        }
        val content = Stack(children = children)
        return PixelOverlayScope(controller = widget.controller, child = content)
    }

    /**
     * Mirrors controller entries while retaining non-immediate removed dialogs as visual-only.
     */
    private fun synchronizePresentations(context: BuildContext) {
        val controller = widget.controller
        val logicalItems = controller.itemsSnapshot()
        if (controller.clearGeneration != observedClearGeneration) {
            presentations = logicalItems.map { item -> PixelOverlayPresentation(item, visible = true) }
            observedClearGeneration = controller.clearGeneration
            return
        }

        val logicalByIdentity = logicalItems.associateBy(PixelOverlayItem::identity)
        val next = mutableListOf<PixelOverlayPresentation>()
        presentations.forEach { presentation ->
            val current = logicalByIdentity[presentation.item.identity]
            when {
                current != null -> next += PixelOverlayPresentation(current, visible = true)
                !presentation.visible -> next += presentation
                presentation.item.motion == PixelOverlayMotion.Dialog &&
                    shouldRetainDialogExit(context) -> {
                    next += presentation.copy(visible = false)
                }
            }
        }
        val retainedIdentities = next.mapTo(mutableSetOf()) { presentation ->
            presentation.item.identity
        }
        logicalItems.forEach { item ->
            if (item.identity !in retainedIdentities) {
                next += PixelOverlayPresentation(item, visible = true)
            }
        }
        presentations = next.sortedWith { left, right ->
            pixelOverlayItemComparator.compare(left.item, right.item)
        }
    }

    /** Returns true only when a Host scope resolves dialog exit to real asynchronous motion. */
    private fun shouldRetainDialogExit(context: BuildContext): Boolean {
        val scope = PixelMotionScope.maybeOf(context) ?: return false
        val resolved = PixelMotionTheme.of(context).dialogExit.resolve(scope.settings)
        return !resolved.isImmediate && resolved.transition != PixelMotionTransitionPreset.None
    }

    /** Builds one permanently keyed, optionally modal and animated route presentation. */
    private fun buildPresentation(
        presentation: PixelOverlayPresentation,
        ownsModal: Boolean,
        overlayOrder: Long,
    ): Widget {
        val item = presentation.item
        /** Keyed shell prevents unkeyed same-type child State from migrating after removal. */
        val keyedContent = Builder(key = PixelOverlayContentKey(item.identity)) { item.widget }
        /** Optional barrier painted immediately below this route's content. */
        val barrier = buildBarrier(item)
        /** Unified visual subtree whose barrier and content share motion and exit gating. */
        val routeVisual = Stack(
            children = listOfNotNull(barrier, keyedContent),
            key = "pixel-overlay-route-visual-${item.identity}",
        )
        /** Motion wrapper retained only for routes that explicitly select dialog motion. */
        val motionPresentation = if (item.motion == PixelOverlayMotion.Dialog) {
            PixelDialogMotionEntry(
                visible = presentation.visible,
                child = routeVisual,
                onExitCompleted = { removePresentation(item.identity) },
                key = PixelOverlayMotionKey(item.identity),
            )
        } else {
            routeVisual
        }
        /** Every route carries order; only the canonical visible modal activates isolation. */
        val interactionPresentation = ModalInteractionScopeWidget(
            active = item.modal && presentation.visible && ownsModal,
            overlayOrder = overlayOrder,
            child = motionPresentation,
            key = "pixel-overlay-route-interaction-${item.identity}",
        )
        /** Focus isolation follows the same logical visibility as render interaction. */
        val focusedPresentation = if (item.modal) {
            /** Whether any canonical route currently handles Escape/Back for this host. */
            val hasDismissConsumer = widget.controller.hasBackConsumer()
            ModalFocusScopeFactory.create(
                active = presentation.visible && ownsModal,
                onDismissRequest = if (ownsModal && hasDismissConsumer) {
                    { widget.controller.handleDismissRequest() }
                } else {
                    null
                },
                consumeUnhandledDismissRequest = hasDismissConsumer,
                coalesceNestedMenu = true,
                coalesceNestedModal = true,
                child = interactionPresentation,
                key = PixelOverlayModalFocusKey(item.identity),
            )
        } else {
            interactionPresentation
        }
        /** Route scope exposes canonical focus order without making non-modal routes focus traps. */
        val orderedFocusPresentation = OverlayFocusScopeFactory.create(
            active = presentation.visible,
            order = PixelOverlayFocusOrder(
                hostIdentity = hostIdentity,
                layerOrder = item.layer.ordinal,
                insertionOrder = item.insertionOrder,
            ),
            child = focusedPresentation,
            key = PixelOverlayFocusLayerKey(item.identity),
        )
        return PixelOverlayPresentationLifecycleWidget(
            controller = widget.controller,
            identity = item.identity,
            hostIdentity = hostIdentity,
            child = orderedFocusPresentation,
            key = PixelOverlayPresentationKey(item.identity),
        )
    }

    /** Builds the configured barrier with Dismiss, Consume, or Ignore pointer behavior. */
    private fun buildBarrier(item: PixelOverlayItem): Widget? {
        val barrier = item.barrier ?: return null
        val key = PixelOverlayBarrierKey(item.identity)
        return when (item.dismissPolicy.barrierTap) {
            PixelOverlayDismissAction.Dismiss -> ModalBarrier(
                color = barrier.color,
                dismissible = true,
                onDismiss = { widget.controller.handleBarrier(item.identity) },
                key = key,
            )
            PixelOverlayDismissAction.Consume -> PositionedFill(
                child = GestureDetector(
                    child = Container(fillColor = barrier.color),
                    onTap = { widget.controller.handleBarrier(item.identity) },
                    key = key,
                ),
                key = "pixel-overlay-consuming-barrier-${item.identity}",
            )
            PixelOverlayDismissAction.Ignore -> ModalBarrier(
                color = barrier.color,
                dismissible = false,
                key = key,
            )
        }
    }

    /** Removes one completed exit without affecting newer logical overlay entries. */
    private fun removePresentation(identity: Long) {
        if (presentations.none { presentation -> presentation.item.identity == identity }) return
        setState {
            presentations = presentations.filterNot { presentation ->
                presentation.item.identity == identity
            }
        }
    }
}

/** Keyed presentation shell that acknowledges removal only after its subtree is unmounted. */
private class PixelOverlayPresentationLifecycleWidget(
    /** Controller that owns [identity]. */
    val controller: PixelOverlayController,
    /** Process-wide entry identity represented by this shell. */
    val identity: Long,
    /** Host identity that must acknowledge this exact presentation. */
    val hostIdentity: Any,
    /** Route presentation mounted below the lifecycle shell. */
    val child: Widget,
    /** Permanent key preventing sibling or controller State migration. */
    override val key: Any,
) : StatefulWidget(key = key) {
    /** Creates the State whose disposal is the presentation-removal boundary. */
    override fun createState(): State<out StatefulWidget> = PixelOverlayPresentationLifecycleState()
}

/** Owns the post-unmount acknowledgement for one host presentation. */
private class PixelOverlayPresentationLifecycleState :
    State<PixelOverlayPresentationLifecycleWidget>() {
    /** Registers this exact presentation before its descendant subtree can be dismissed. */
    override fun initState() {
        widget.controller.presentationMounted(widget.identity, widget.hostIdentity)
    }

    /** Returns the current route subtree without changing its identity. */
    override fun build(context: BuildContext): Widget = widget.child

    /** Delivers the host acknowledgement only after descendants have already unmounted. */
    override fun dispose() {
        widget.controller.presentationDisposed(widget.identity, widget.hostIdentity)
    }
}

/** Creates the retained dialog entry that owns one enter/exit controller at a time. */
private fun PixelDialogMotionEntry(
    visible: Boolean,
    child: Widget,
    onExitCompleted: () -> Unit,
    key: Any,
): Widget = PixelDialogMotionEntryWidget(
    visible = visible,
    child = child,
    onExitCompleted = onExitCompleted,
    key = key,
)

/** Declarative configuration for one controller-owned dialog presentation. */
private class PixelDialogMotionEntryWidget(
    /** Whether this dialog remains logically active. */
    val visible: Boolean,
    /** Dialog subtree whose State must survive the exit animation. */
    val child: Widget,
    /** Exactly-once callback removing the retained presentation after exit. */
    val onExitCompleted: () -> Unit,
    override val key: Any,
) : StatefulWidget(key = key) {
    /** Creates state that owns the dialog's normalized motion controller. */
    override fun createState(): State<out StatefulWidget> = PixelDialogMotionEntryState()
}

/** Owns interruption-safe dialog opacity motion and terminal cleanup. */
private class PixelDialogMotionEntryState : State<PixelDialogMotionEntryWidget>() {
    /** Normalized controller for the current enter or exit segment. */
    private var controller: PixelAnimationController? = null

    /** Provider identity used by the current controller. */
    private var configuredVsync: PixelTickerProvider? = null

    /** Theme token used to resolve the current segment. */
    private var configuredSpec: PixelMotionSpec? = null

    /** Host settings used to resolve the current segment. */
    private var configuredSettings: PixelMotionSettings? = null

    /** Resolved duration, delay, curve, and reduced-motion policy for the current segment. */
    private var resolvedMotion: PixelResolvedMotion? = null

    /** Visual opacity captured when the current segment started. */
    private var segmentStart: Float = 1f

    /** Logical opacity endpoint for the current segment. */
    private var segmentTarget: Float = 1f

    /** Whether the first inherited Motion environment has been applied. */
    private var initialized: Boolean = false

    /** Guards the exit callback across duplicate terminal notifications and disposal. */
    private var exitCompletionDispatched: Boolean = false

    /** Stable listener that observes exact terminal controller status. */
    private val controllerListener: VoidCallback = VoidCallback {
        if (controller?.status == PixelAnimationStatus.Completed && segmentTarget <= 0f) {
            dispatchExitCompletion()
        }
    }

    /** Re-resolves an inherited Motion change from the current visual opacity. */
    override fun didChangeDependencies() {
        configureForCurrentEnvironment(forceRestart = initialized)
    }

    /** Starts enter or exit from the exact opacity visible before this widget update. */
    override fun didUpdateWidget(oldWidget: PixelDialogMotionEntryWidget) {
        if (oldWidget.visible != widget.visible) {
            configureForCurrentEnvironment(forceRestart = true)
        }
    }

    /** Releases the only controller/ticker owned by this presentation. */
    override fun dispose() {
        disposeController()
    }

    /** Paints a stable opacity and makes retained exit content visual-only. */
    override fun build(context: BuildContext): Widget {
        if (!initialized) configureForCurrentEnvironment(forceRestart = false)
        context.watch(controller)
        return Opacity(
            opacity = currentOpacity(),
            child = VisualOnlyWidget(
                child = widget.child,
                visualOnly = !widget.visible,
                key = "dialog-interaction-gate",
            ),
            key = "dialog-opacity",
        )
    }

    /** Resolves the applicable enter/exit token and rebuilds a segment when required. */
    private fun configureForCurrentEnvironment(forceRestart: Boolean) {
        val scope = PixelMotionScope.maybeOf(context)
        val spec = if (widget.visible) {
            PixelMotionTheme.of(context).dialogEnter
        } else {
            PixelMotionTheme.of(context).dialogExit
        }
        val settings = scope?.settings ?: PixelMotionSettings.Default
        val environmentChanged = configuredVsync !== scope?.vsync ||
            configuredSpec != spec ||
            configuredSettings != settings
        if (initialized && !forceRestart && !environmentChanged) return

        val start = if (initialized) currentOpacity() else if (widget.visible) 0f else 1f
        configuredVsync = scope?.vsync
        configuredSpec = spec
        configuredSettings = settings
        resolvedMotion = spec.resolve(settings)
        initialized = true
        startSegment(start = start, target = if (widget.visible) 1f else 0f)
    }

    /** Starts one delayed/curved segment or applies its endpoint synchronously. */
    private fun startSegment(start: Float, target: Float) {
        disposeController()
        segmentStart = start.coerceIn(0f, 1f)
        segmentTarget = target.coerceIn(0f, 1f)
        if (segmentTarget > 0f) exitCompletionDispatched = false

        val resolved = checkNotNull(resolvedMotion)
        val provider = configuredVsync
        val totalDuration = resolved.delay + resolved.duration
        if (
            provider == null ||
            resolved.isImmediate ||
            resolved.transition == PixelMotionTransitionPreset.None ||
            totalDuration <= Duration.ZERO ||
            segmentStart == segmentTarget
        ) {
            segmentStart = segmentTarget
            if (segmentTarget <= 0f) dispatchExitCompletion()
            return
        }

        controller = PixelAnimationController(
            duration = totalDuration,
            vsync = provider,
        ).also { ownedController ->
            ownedController.addListener(controllerListener)
            ownedController.forward()
        }
    }

    /** Evaluates delay, curve, and segment endpoints into the opacity painted this frame. */
    private fun currentOpacity(): Float {
        val ownedController = controller ?: return segmentStart.coerceIn(0f, 1f)
        val resolved = resolvedMotion ?: return segmentStart.coerceIn(0f, 1f)
        val totalNanos = (resolved.delay + resolved.duration).inWholeNanoseconds.coerceAtLeast(1L)
        val delayRatio = resolved.delay.inWholeNanoseconds.toDouble() / totalNanos.toDouble()
        val rawProgress = ownedController.value.toDouble()
        val activeProgress = when {
            rawProgress <= delayRatio -> 0f
            delayRatio >= 1.0 -> if (ownedController.status == PixelAnimationStatus.Completed) 1f else 0f
            else -> ((rawProgress - delayRatio) / (1.0 - delayRatio)).toFloat().coerceIn(0f, 1f)
        }
        val transformed = resolved.curve.transform(activeProgress)
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, 1f)
            ?: 0f
        return (segmentStart + (segmentTarget - segmentStart) * transformed).coerceIn(0f, 1f)
    }

    /** Invokes parent cleanup once after this presentation is fully invisible. */
    private fun dispatchExitCompletion() {
        if (exitCompletionDispatched) return
        exitCompletionDispatched = true
        widget.onExitCompleted()
    }

    /** Detaches the stable listener and disposes the current segment controller. */
    private fun disposeController() {
        controller?.removeListener(controllerListener)
        controller?.dispose()
        controller = null
    }
}

/** Inherited controller binding consumed by overlay-aware descendants. */
private class PixelOverlayScope(
    /** Controller nearest to descendants of this hosted overlay stack. */
    val controller: PixelOverlayController,
    /** Application and presentation subtree sharing [controller]. */
    override val child: Widget,
) : InheritedWidget(child = child) {
    /** Notifies descendants only when the host switches to another controller instance. */
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return controller !== (oldWidget as? PixelOverlayScope)?.controller
    }
}
