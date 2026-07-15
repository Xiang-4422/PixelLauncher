package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [SnackbarQueue] 中的一条消息。
 *
 * Action callback 由 [PixelSnackbarQueueController] 私有持有，避免公开 item 被复制后重复执行；
 * 调用 [PixelSnackbarQueueController.performAction] 会先从队列移除 item，再恰好执行一次 callback。
 */
public data class PixelSnackbarQueueItem(
    /** 当前控制器生命周期内单调递增的消息标识。 */
    val id: Int,
    /** Snackbar 展示的可读消息。 */
    val message: String,
    /** 可选操作按钮文字；null 表示没有操作按钮。 */
    val actionLabel: String? = null,
    /** Snackbar 表面的填充色。 */
    val fillColor: PixelColor = PixelColor.fromRgb(40, 40, 40),
    /** Snackbar 消息使用的文字样式。 */
    val textStyle: PixelTextStyle = PixelTextStyle.Default,
)

/** Legacy Snackbar fill used only to recognize an omitted queue-item theme override. */
private val LEGACY_SNACKBAR_QUEUE_FILL_COLOR: PixelColor = PixelColor.fromRgb(40, 40, 40)

/**
 * FIFO snackbar 队列控制器。
 *
 * 每条消息拥有独立 timeout 和可选 action。队首计时由 [SnackbarQueue] 绑定到 Host active
 * time；手动关闭、action、clear 或 widget dispose 都会同步释放 ticker，迟到帧不会关闭后继项。
 */
public class PixelSnackbarQueueController : ChangeNotifier() {
    /** 将公开 item、超时配置和只可消费一次的 action callback 绑定。 */
    private data class QueueEntry(
        /** 调用方可观察和关闭的 snackbar item。 */
        val item: PixelSnackbarQueueItem,
        /** 此 item 在成为队首后的 active-time 停留时间。 */
        val timeout: Duration,
        /** 操作按钮触发时最多执行一次的业务 callback。 */
        val onAction: (() -> Unit)?,
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
    public val current: PixelSnackbarQueueItem?
        get() = entries.firstOrNull()?.item

    /** 队首消息的 active-time 停留时间；没有消息时为 null。 */
    public val currentTimeout: Duration?
        get() = entries.firstOrNull()?.timeout

    /** Current item presentation metadata, or null when the queue is empty. */
    internal val currentVisualConfig: PixelNotificationVisualConfig?
        get() = entries.firstOrNull()?.visualConfig

    /**
     * 追加一条 snackbar 并返回稳定 item。
     *
     * [actionLabel] 非 null 时 [SnackbarQueue] 渲染可聚焦操作按钮；该按钮先移除 item，再执行
     * [onAction]。[Duration.INFINITE] 表示只允许手动关闭或执行 action。
     */
    public fun enqueue(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        fillColor: PixelColor = PixelColor.fromRgb(40, 40, 40),
        textStyle: PixelTextStyle = PixelTextStyle.Default,
        timeout: Duration = DefaultTimeout,
    ): PixelSnackbarQueueItem {
        return enqueueItem(
            message = message,
            states = PixelControlStateSet.Normal,
            actionLabel = actionLabel,
            onAction = onAction,
            fillColor = fillColor.takeUnless { color -> color == LEGACY_SNACKBAR_QUEUE_FILL_COLOR },
            textStyle = textStyle.takeUnless { style -> style == PixelTextStyle.Default },
            timeout = timeout,
        )
    }

    /**
 * 执行 `SnackbarQueue` 的 `enqueue` 公开行为；具体参数、返回和副作用见下文。
 *
     * Appends one state-aware Snackbar without changing the public item constructor.
     *
     * Loading and Disabled suppress its action while preserving FIFO and timeout ownership. Null
     * visual fields defer to the mounted Snackbar's active component and foundation tokens.
     */
    @kotlin.jvm.JvmName("enqueueWithControlStates")
    public fun enqueue(
        message: String,
        states: PixelControlStateSet,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        fillColor: PixelColor? = null,
        textStyle: PixelTextStyle? = null,
        timeout: Duration = DefaultTimeout,
    ): PixelSnackbarQueueItem {
        return enqueueItem(
            message = message,
            states = states,
            actionLabel = actionLabel,
            onAction = onAction,
            fillColor = fillColor,
            textStyle = textStyle,
            timeout = timeout,
        )
    }

    /** Validates and appends one immutable Snackbar queue entry. */
    private fun enqueueItem(
        message: String,
        states: PixelControlStateSet,
        actionLabel: String?,
        onAction: (() -> Unit)?,
        fillColor: PixelColor?,
        textStyle: PixelTextStyle?,
        timeout: Duration,
    ): PixelSnackbarQueueItem {
        requireValidSnackbarTimeout(timeout)
        require(actionLabel != null || onAction == null) {
            "Snackbar onAction requires a non-null actionLabel"
        }
        require(actionLabel == null || actionLabel.isNotBlank()) {
            "Snackbar actionLabel must not be blank"
        }
        /** 本次入队使用的稳定公开 item。 */
        val item = PixelSnackbarQueueItem(
            id = nextId++,
            message = message,
            actionLabel = actionLabel,
            fillColor = fillColor ?: LEGACY_SNACKBAR_QUEUE_FILL_COLOR,
            textStyle = textStyle ?: PixelTextStyle.Default,
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
            onAction = onAction,
            visualConfig = visualConfig,
        )
        notifyListeners()
        return item
    }

    /** 关闭当前队首 snackbar。 */
    public fun dismissCurrent(): Boolean {
        if (entries.isEmpty()) return false
        entries = entries.drop(1)
        notifyListeners()
        return true
    }

    /** 关闭指定 [id] 的 snackbar，不执行其 action callback。 */
    public fun dismiss(id: Int): Boolean {
        /** 删除目标之后的下一份队列快照。 */
        val nextEntries = entries.filterNot { entry -> entry.item.id == id }
        if (nextEntries.size == entries.size) return false
        entries = nextEntries
        notifyListeners()
        return true
    }

    /** 执行当前队首 action；没有 action 时返回 false 且不关闭消息。 */
    public fun performCurrentAction(): Boolean {
        /** 当前可执行 action 的队首 item。 */
        val item = current ?: return false
        return performAction(item.id)
    }

    /**
     * 消费指定 [id] 的 action。
     *
     * Item 在 callback 前删除并通知 listener，因此 callback 抛出异常也不会留下 ticker 或被再次执行。
     */
    public fun performAction(id: Int): Boolean {
        /** 与 id 对应且声明了操作按钮的 entry。 */
        val entry = entries.firstOrNull { candidate -> candidate.item.id == id }
            ?.takeIf { candidate -> candidate.item.actionLabel != null }
            ?.takeIf { candidate ->
                PixelControlState.Disabled !in candidate.visualConfig.states &&
                    PixelControlState.Loading !in candidate.visualConfig.states
            }
            ?: return false
        entries = entries.filterNot { candidate -> candidate.item.id == id }
        notifyListeners()
        entry.onAction?.invoke()
        return true
    }

    /** 清空所有排队 snackbar，不执行 action callback。 */
    public fun clear() {
        if (entries.isEmpty()) return
        entries = emptyList()
        notifyListeners()
    }

    /** 集中提供 `SnackbarQueue` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 未显式指定时，每条 snackbar 的默认 active-time 停留时间。 */
        public val DefaultTimeout: Duration = 4.seconds
    }
}

/**
 * 显示 [PixelSnackbarQueueController] 队首消息并管理其 Host active-time timeout。
 *
 * 没有消息时渲染零尺寸占位；action 使用标准 [TextButton]，并与 controller 的一次性消费语义绑定。
 */
public fun SnackbarQueue(
    controller: PixelSnackbarQueueController,
    key: Any? = null,
): Widget {
    return SnackbarQueueWidget(
        controller = controller,
        states = PixelControlStateSet.Normal,
        key = key,
    )
}

/**
 * 执行 `SnackbarQueue` 的 `SnackbarQueue` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware Snackbar queue presentation.
 *
 * Presentation-level states combine with item states without changing controller snapshots.
 */
@kotlin.jvm.JvmName("SnackbarQueueWithControlStates")
public fun SnackbarQueue(
    controller: PixelSnackbarQueueController,
    states: PixelControlStateSet,
    key: Any? = null,
): Widget {
    return SnackbarQueueWidget(controller = controller, states = states, key = key)
}

/** 持有 snackbar 队首计时器并随控制器变化重建内容。 */
private class SnackbarQueueWidget(
    /** 提供当前消息、action 和队列操作的控制器。 */
    val controller: PixelSnackbarQueueController,
    /** Presentation-level states combined with the current item state. */
    val states: PixelControlStateSet,
    key: Any?,
) : StatefulWidget(key = key) {
    /** 为该 retained 槽位创建唯一的计时 State。 */
    override fun createState(): State<out StatefulWidget> = SnackbarQueueState()
}

/** 管理 snackbar controller 监听、active-time 计时与资源释放。 */
private class SnackbarQueueState : State<SnackbarQueueWidget>() {
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

    /** 订阅初始控制器，使 action、手动关闭和 clear 能同步取消 ticker。 */
    override fun initState() {
        widget.controller.addListener(controllerListener)
    }

    /** 跟随最近 Host 的 ticker provider，忽略 reduce-motion 的视觉时长缩放。 */
    override fun didChangeDependencies() {
        timeoutDriver.configure(PixelMotionScope.maybeOf(context)?.vsync)
    }

    /** 切换控制器时解除旧 listener、绑定新 listener，并立即对齐队首计时。 */
    override fun didUpdateWidget(oldWidget: SnackbarQueueWidget) {
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

    /** 渲染当前队首及其一次性 action，空队列使用零尺寸占位。 */
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
        /** 可选的标准操作按钮，消费时由 controller 先取消计时。 */
        val action = item.actionLabel?.let { label ->
            TextButton(
                text = label,
                onPressed = { widget.controller.performAction(item.id) },
                states = effectiveStates,
                key = PixelNotificationQueueItemKey(
                    queueKey = widget.key,
                    queueOwner = widget.controller,
                    itemId = item.id,
                    kind = "snackbar-action",
                ),
            )
        }
        return Snackbar(
            message = item.message,
            states = effectiveStates,
            action = action,
            fillColor = visualConfig.fillColor,
            textStyle = visualConfig.textStyle,
            key = PixelNotificationQueueItemKey(
                queueKey = widget.key,
                queueOwner = widget.controller,
                itemId = item.id,
                kind = "snackbar",
            ),
        )
    }

    /** 同步停止/切换 ticker，然后请求 retained State 重建。 */
    private fun handleControllerChanged() {
        timeoutDriver.synchronize()
        setState { }
    }
}

/** 拒绝会导致同步丢失或倒计时方向错误的 snackbar 停留时间。 */
private fun requireValidSnackbarTimeout(timeout: Duration) {
    require(timeout > Duration.ZERO) {
        "Notification timeout must be positive or infinite, got $timeout"
    }
}
