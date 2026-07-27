package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.animation.PixelTicker
import com.purride.pixelui.animation.PixelTickerProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [ToastQueue] 中的一条消息。
 *
 * [id] 由 [PixelToastQueueController] 分配，调用方可用它关闭指定 toast。
 */
public data class PixelToastQueueItem(
    /** 当前控制器生命周期内单调递增的消息标识。 */
    val id: Int,
    /** Toast 展示的可读消息。 */
    val message: String,
    /** 可选 Toast 表面填充色；null 时由挂载的 Toast 解析通知容器角色。 */
    val fillColor: PixelColor? = null,
    /** 可选 Toast 文字样式；null 时由挂载的 Toast 解析 caption typography。 */
    val textStyle: PixelTextStyle? = null,
)

/**
 * Immutable presentation metadata kept outside public queue item constructors for binary safety.
 *
 * Null visual fields mean that the mounted notification component must resolve its active theme.
 */
internal data class PixelNotificationVisualConfig(
    /** Persistent item-level visual and capability states. */
    val states: PixelControlStateSet,
    /** Optional explicit surface fill above component tokens. */
    val fillColor: PixelColor?,
    /** Optional explicit typography above component and foundation tokens. */
    val textStyle: PixelTextStyle?,
)

/**
 * FIFO toast 队列控制器。
 *
 * 控制器保存消息和每条消息的停留时间；[ToastQueue] 在 Host 的 active-time ticker 上为队首
 * 计时。Host pause 期间停留时间不会流逝，且 reduce-motion 或 animator duration scale 不会把
 * 可读停留时间缩短为零。没有 [PixelMotionScope] 时队列仍可手动控制，但不会创建隐式时钟。
 */
public class PixelToastQueueController : ChangeNotifier() {
    /** 将公开 item 与仅由控制器维护的超时配置绑定。 */
    private data class QueueEntry(
        /** 调用方可观察和关闭的 toast item。 */
        val item: PixelToastQueueItem,
        /** 此 item 在成为队首后的 active-time 停留时间。 */
        val timeout: Duration,
        /** Binary-safe item presentation metadata. */
        val visualConfig: PixelNotificationVisualConfig,
    )

    /** 下一个新消息使用的控制器内标识。 */
    private var nextId: Int = 1

    /** 按入队顺序保存的不可变快照。 */
    private var entries: List<QueueEntry> = emptyList()

    /** 当前排队消息数量。 */
    public val size: Int
        get() = entries.size

    /** 队首消息；没有消息时为 null。 */
    public val current: PixelToastQueueItem?
        get() = entries.firstOrNull()?.item

    /** 队首消息的 active-time 停留时间；没有消息时为 null。 */
    public val currentTimeout: Duration?
        get() = entries.firstOrNull()?.timeout

    /** Current item presentation metadata, or null when the queue is empty. */
    internal val currentVisualConfig: PixelNotificationVisualConfig?
        get() = entries.firstOrNull()?.visualConfig

    /**
     * 使用 [DefaultTimeout] 追加一条 toast；省略的视觉字段由挂载的 Toast 解析 token。
     */
    public fun enqueue(
        message: String,
        fillColor: PixelColor? = null,
        textStyle: PixelTextStyle? = null,
    ): PixelToastQueueItem {
        return enqueue(
            message = message,
            timeout = DefaultTimeout,
            fillColor = fillColor,
            textStyle = textStyle,
        )
    }

    /**
     * 追加一条具有显式 active-time [timeout] 的 toast。
     *
     * [Duration.INFINITE] 表示只允许手动关闭；零或负值会被拒绝，避免消息在进入 retained
     * 树之前同步消失。
     */
    public fun enqueue(
        message: String,
        timeout: Duration,
        fillColor: PixelColor? = null,
        textStyle: PixelTextStyle? = null,
    ): PixelToastQueueItem {
        return enqueueItem(
            message = message,
            timeout = timeout,
            states = PixelControlStateSet.Normal,
            fillColor = fillColor,
            textStyle = textStyle,
        )
    }

    /**
 * 执行 `ToastQueue` 的 `enqueue` 公开行为；具体参数、返回和副作用见下文。
 *
     * Appends one state-aware Toast while preserving the existing item constructor and methods.
     *
     * Error and Loading are painted by Toast component tokens. Explicit visual values remain above
     * theme values, and null means that the mounted queue resolves the latest inherited theme.
     */
    @kotlin.jvm.JvmName("enqueueWithControlStates")
    public fun enqueue(
        message: String,
        states: PixelControlStateSet,
        timeout: Duration = DefaultTimeout,
        fillColor: PixelColor? = null,
        textStyle: PixelTextStyle? = null,
    ): PixelToastQueueItem {
        return enqueueItem(
            message = message,
            timeout = timeout,
            states = states,
            fillColor = fillColor,
            textStyle = textStyle,
        )
    }

    /** Validates and appends one immutable Toast queue entry. */
    private fun enqueueItem(
        message: String,
        timeout: Duration,
        states: PixelControlStateSet,
        fillColor: PixelColor?,
        textStyle: PixelTextStyle?,
    ): PixelToastQueueItem {
        requireValidToastTimeout(timeout)
        /** 本次入队使用的稳定公开 item。 */
        val item = PixelToastQueueItem(
            id = nextId++,
            message = message,
            fillColor = fillColor,
            textStyle = textStyle,
        )
        /** Binary-safe presentation metadata resolved only after mounting. */
        val visualConfig = PixelNotificationVisualConfig(
            states = states,
            fillColor = fillColor,
            textStyle = textStyle,
        )
        entries = entries + QueueEntry(
            item = item,
            timeout = timeout,
            visualConfig = visualConfig,
        )
        notifyListeners()
        return item
    }

    /** 关闭当前队首 toast。 */
    public fun dismissCurrent(): Boolean {
        if (entries.isEmpty()) return false
        entries = entries.drop(1)
        notifyListeners()
        return true
    }

    /** 关闭指定 [id] 的 toast。 */
    public fun dismiss(id: Int): Boolean {
        /** 删除目标之后的下一份队列快照。 */
        val nextEntries = entries.filterNot { entry -> entry.item.id == id }
        if (nextEntries.size == entries.size) return false
        entries = nextEntries
        notifyListeners()
        return true
    }

    /** 清空所有排队 toast。 */
    public fun clear() {
        if (entries.isEmpty()) return
        entries = emptyList()
        notifyListeners()
    }

    /** 集中提供 `ToastQueue` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 未显式指定时，每条 toast 的默认 active-time 停留时间。 */
        public val DefaultTimeout: Duration = 2.seconds
    }
}

/**
 * 显示 [PixelToastQueueController] 队首消息的 widget。
 *
 * 该组件适合放入 [PixelOverlayHost] 或页面级 [Stack]；没有消息时渲染为空尺寸占位。
 * 每个队首 item 使用独立 retained key，保证连续消息会触发新的 live-region 内容。
 */
public fun ToastQueue(
    controller: PixelToastQueueController,
    key: Any? = null,
): Widget {
    return ToastQueueWidget(
        controller = controller,
        states = PixelControlStateSet.Normal,
        key = key,
    )
}

/**
 * 执行 `ToastQueue` 的 `ToastQueue` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware Toast queue presentation.
 *
 * [states] combine with item-level states without mutating the controller snapshot. This is useful
 * for a page-wide Error or Loading policy while each queued item retains independent metadata.
 */
@kotlin.jvm.JvmName("ToastQueueWithControlStates")
public fun ToastQueue(
    controller: PixelToastQueueController,
    states: PixelControlStateSet,
    key: Any? = null,
): Widget {
    return ToastQueueWidget(controller = controller, states = states, key = key)
}

/** 持有 toast 队首计时器并随控制器变化重建内容。 */
private class ToastQueueWidget(
    /** 提供当前消息及队列操作的控制器。 */
    val controller: PixelToastQueueController,
    /** Presentation-level states combined with the current item state. */
    val states: PixelControlStateSet,
    key: Any?,
) : StatefulWidget(key = key) {
    /** 为该 retained 槽位创建唯一的计时 State。 */
    override fun createState(): State<out StatefulWidget> = ToastQueueState()
}

/** 管理 toast controller 监听、active-time 计时与资源释放。 */
private class ToastQueueState : State<ToastQueueWidget>() {
    /** 控制器同步通知使用的稳定 listener identity。 */
    private val controllerListener: VoidCallback = VoidCallback(::handleControllerChanged)

    /** 仅在当前 widget 挂载期间存活的超时驱动器。 */
    private val timeoutDriver = PixelNotificationTimeoutDriver(
        currentEntry = {
            widget.controller.current?.let { item ->
                widget.controller.currentTimeout?.let { timeout ->
                    PixelNotificationTimeoutEntry(
                        owner = widget.controller,
                        id = item.id,
                        timeout = timeout,
                    )
                }
            }
        },
        dismiss = { id -> widget.controller.dismiss(id) },
    )

    /** 订阅初始控制器，使手动关闭能同步取消 ticker。 */
    override fun initState() {
        widget.controller.addListener(controllerListener)
    }

    /** 跟随最近 Host 的 ticker provider，忽略 reduce-motion 的视觉时长缩放。 */
    override fun didChangeDependencies() {
        timeoutDriver.configure(PixelMotionScope.maybeOf(context)?.vsync)
    }

    /** 切换控制器时解除旧 listener、绑定新 listener，并立即对齐队首计时。 */
    override fun didUpdateWidget(oldWidget: ToastQueueWidget) {
        if (oldWidget.controller === widget.controller) return
        oldWidget.controller.removeListener(controllerListener)
        widget.controller.addListener(controllerListener)
        timeoutDriver.synchronize()
    }

    /** 解除 controller 和 ticker 所有权，保证卸载后没有回调残留。 */
    override fun dispose() {
        widget.controller.removeListener(controllerListener)
        timeoutDriver.dispose()
    }

    /** 渲染当前队首，空队列使用零尺寸占位。 */
    override fun build(context: BuildContext): Widget {
        timeoutDriver.synchronize()
        /** 当前帧应展示的队首 item。 */
        val item = widget.controller.current
            ?: return SizedBox(width = 0, height = 0, key = widget.key)
        /** Item metadata is always present for an item produced by this controller. */
        val visualConfig = widget.controller.currentVisualConfig
            ?: PixelNotificationVisualConfig(
                states = PixelControlStateSet.Normal,
                fillColor = item.fillColor,
                textStyle = item.textStyle,
            )
        /** Presentation and item state sets merged without losing fixed priority semantics. */
        val effectiveStates = mergeNotificationStates(widget.states, visualConfig.states)
        return Toast(
            message = item.message,
            states = effectiveStates,
            fillColor = visualConfig.fillColor,
            textStyle = visualConfig.textStyle,
            key = PixelNotificationQueueItemKey(
                queueKey = widget.key,
                queueOwner = widget.controller,
                itemId = item.id,
                kind = "toast",
            ),
        )
    }

    /** 同步停止/切换 ticker，然后请求 retained State 重建。 */
    private fun handleControllerChanged() {
        timeoutDriver.synchronize()
        setState { }
    }
}

/** Merges two immutable state sets while retaining Normal as the empty representation. */
internal fun mergeNotificationStates(
    first: PixelControlStateSet,
    second: PixelControlStateSet,
): PixelControlStateSet {
    /** Accumulated state set returned to the mounted notification component. */
    var result = first
    second.toSet().forEach { state -> result += state }
    return result
}

/** 队首 timeout 驱动器读取的最小不可变配置。 */
internal data class PixelNotificationTimeoutEntry(
    /** 产生 item id 的永久 controller identity。 */
    val owner: Any,
    /** 当前控制器内唯一的 item 标识。 */
    val id: Int,
    /** 从 item 成为队首起计算的 active-time 停留时间。 */
    val timeout: Duration,
)

/**
 * 将任意通知队列的当前 item 绑定到一个 Host-owned active-time ticker。
 *
 * 该类不读取 Motion settings，因而视觉减弱策略不会改变可访问内容的停留时长。
 */
internal class PixelNotificationTimeoutDriver(
    /** 每次同步时读取当前队首及 timeout 的函数。 */
    private val currentEntry: () -> PixelNotificationTimeoutEntry?,
    /** timeout 到达时按稳定 id 删除队首的函数。 */
    private val dismiss: (Int) -> Boolean,
) {
    /** 当前 Host 提供的 active-time ticker provider。 */
    private var provider: PixelTickerProvider? = null

    /** 当前队首独占的 ticker；无限时长或无 Host 时为 null。 */
    private var ticker: PixelTicker? = null

    /** [ticker] 正在计时的 item id。 */
    private var timedItemId: Int? = null

    /** [ticker] 正在计时的 controller identity。 */
    private var timedOwner: Any? = null

    /** 当前 State 是否已经完成终止释放。 */
    private var disposed: Boolean = false

    /**
     * 绑定 [nextProvider]；provider identity 变化时终止旧 ticker，并从当前队首重新开始。
     */
    internal fun configure(nextProvider: PixelTickerProvider?) {
        if (disposed || provider === nextProvider) return
        cancelTicker()
        provider = nextProvider
        synchronize()
    }

    /**
     * 对齐当前队首：同一 item 保留 elapsed，新 item 重启，无 item 或无限时长停止计时。
     */
    internal fun synchronize() {
        if (disposed) return
        /** 最新控制器快照中的队首计时配置。 */
        val entry = currentEntry()
        /** 当前仍可创建 ticker 的 Host provider。 */
        val activeProvider = provider
        if (entry == null || activeProvider == null || entry.timeout.isInfinite()) {
            cancelTicker()
            return
        }
        if (timedOwner === entry.owner && timedItemId == entry.id && ticker != null) return

        cancelTicker()
        timedOwner = entry.owner
        timedItemId = entry.id
        /** 此 item 的有限 active-time 纳秒数。 */
        val timeoutNanos = entry.timeout.inWholeNanoseconds
        /** 回调捕获的 controller identity，防止切换 controller 后迟到帧越界。 */
        val expectedOwner = entry.owner
        /** 回调捕获的稳定 item id，避免迟到帧关闭后继消息。 */
        val expectedId = entry.id
        ticker = activeProvider.createTicker { elapsedNanos ->
            if (
                elapsedNanos < timeoutNanos ||
                timedOwner !== expectedOwner ||
                timedItemId != expectedId
            ) {
                return@createTicker
            }
            cancelTicker()
            dismiss(expectedId)
        }.also(PixelTicker::start)
    }

    /** 幂等释放 ticker，并禁止任何后续 controller 通知重启它。 */
    internal fun dispose() {
        if (disposed) return
        disposed = true
        cancelTicker()
        provider = null
    }

    /** 立即停止并释放当前 ticker，同时清除其 item ownership。 */
    private fun cancelTicker() {
        /** 在清空字段后释放，避免 provider 回调中的重入观察到旧 ownership。 */
        val ownedTicker = ticker
        ticker = null
        timedOwner = null
        timedItemId = null
        ownedTicker?.dispose()
    }
}

/** 区分连续通知 item 与调用方队列槽位的 retained identity。 */
internal data class PixelNotificationQueueItemKey(
    /** 调用方提供的队列级 key。 */
    val queueKey: Any?,
    /** 产生 [itemId] 的 controller，避免局部 id 在 controller 切换时复用。 */
    val queueOwner: Any,
    /** 当前 controller 内的 item id。 */
    val itemId: Int,
    /** Toast 与 Snackbar 之间的类型区分。 */
    val kind: String,
)

/** 拒绝会导致同步丢失或倒计时方向错误的 toast 停留时间。 */
private fun requireValidToastTimeout(timeout: Duration) {
    require(timeout > Duration.ZERO) {
        "Notification timeout must be positive or infinite, got $timeout"
    }
}
