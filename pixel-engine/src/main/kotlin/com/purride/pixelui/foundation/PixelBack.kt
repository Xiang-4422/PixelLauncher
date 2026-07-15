package com.purride.pixelui

import com.purride.pixelui.internal.PixelArtifactInternalApi

/**
 * widget 树内的 back 事件调度器。
 *
 * handler 按注册顺序入栈，最近挂载的 handler 先处理。返回 true 表示已消费。
 */
public class PixelBackDispatcher {
    /** 单调递增的 handler/observer 注册标识。 */
    private var nextId = 1

    /** 当前有效的离散或预测返回处理器，顺序与挂载顺序一致。 */
    private val handlers = mutableListOf<PixelBackEntry>()

    /** 监听“是否存在 handler”变化的 Host 观察者。 */
    private val availabilityListeners = linkedMapOf<Int, (Boolean) -> Unit>()

    /** 当前正在进行、尚未取消或提交的预测返回会话。 */
    private var activePredictiveSession: PixelPredictiveBackSession? = null

    /** 是否至少有一个已启用并已注册的返回处理器。 */
    /** 供 Android Host 同步系统 Back 注册状态的内部可用性快照。 */
    @PixelArtifactInternalApi
    public val hasRegisteredHandlers: Boolean
        get() = handlers.isNotEmpty()

    /**
     * 注册一个 back handler。
     */
    public fun register(handler: () -> Boolean): PixelBackRegistration {
        return addEntry(
            PixelBackEntry.Discrete(
                id = nextId++,
                handler = handler,
            ),
        )
    }

    /**
     * 注册支持 start/progress/cancel/commit 的预测返回处理器。
     *
     * API 24–32 的离散返回和 API 33 仅完成回调会调用
     * [PixelPredictiveBackCallback.onBackInvoked]；API 34+ 才能提供完整进度会话。
     */
    public fun registerPredictive(callback: PixelPredictiveBackCallback): PixelBackRegistration {
        return addEntry(
            PixelBackEntry.Predictive(
                id = nextId++,
                callback = callback,
            ),
        )
    }

    /**
     * 从栈顶开始派发 back 事件。
     */
    public fun handleBack(): Boolean {
        cancelPredictiveBack()
        val snapshot = handlers.toList()
        for (entry in snapshot.asReversed()) {
            if (entry.handleInvoked()) return true
        }
        return false
    }

    /**
     * 从栈顶寻找愿意接管 [event] 的 handler，并锁定本次预测返回会话。
     *
     * 离散 handler 会阻挡其下方页面的预览，直到提交时才真正执行，避免在 overlay
     * 等顶层 handler 尚未决定是否消费前错误动画底层 Navigator。
     */
    public fun startPredictiveBack(event: PixelPredictiveBackEvent): Boolean {
        cancelPredictiveBack()
        val candidates = handlers.toList().asReversed()
        candidates.forEachIndexed { index, entry ->
            val accepted = when (entry) {
                is PixelBackEntry.Discrete -> true
                is PixelBackEntry.Predictive -> entry.callback.onBackStarted(event)
            }
            if (accepted) {
                activePredictiveSession = PixelPredictiveBackSession(
                    entry = entry,
                    fallbackEntries = candidates.drop(index + 1),
                )
                return true
            }
        }
        return false
    }

    /** 把最新的 [event] 只发送给本次 start 已选中的预测返回 handler。 */
    public fun updatePredictiveBack(event: PixelPredictiveBackEvent) {
        val entry = activePredictiveSession?.entry as? PixelBackEntry.Predictive ?: return
        entry.callback.onBackProgressed(event)
    }

    /** 取消当前预测返回会话；重复取消是安全的空操作。 */
    public fun cancelPredictiveBack() {
        val session = activePredictiveSession ?: return
        activePredictiveSession = null
        (session.entry as? PixelBackEntry.Predictive)?.callback?.onBackCancelled()
    }

    /**
     * 提交当前预测返回会话。
     *
     * 会话会在调用消费者代码前清空，确保回调重入不会重复提交。同一 handler 若在提交
     * 时返回 `false`，仅执行 start 时位于它下方的离散 fallback，不把完成事件错误发送
     * 给一个从未收到 start 的预测会话。
     */
    public fun commitPredictiveBack(): Boolean {
        val session = activePredictiveSession ?: return handleBack()
        activePredictiveSession = null
        if (session.entry.handleCommitted()) return true
        return session.fallbackEntries.any { entry ->
            handlers.any { registered -> registered.id == entry.id } && entry.handleInvoked()
        }
    }

    /** 注册 Host 使用的 handler 可用性观察者，并立即返回可释放句柄。 */
    /** 供 Android Host 观察 Back handler 空/非空边界的内部 SPI。 */
    @PixelArtifactInternalApi
    public fun addAvailabilityListener(listener: (Boolean) -> Unit): PixelBackRegistration {
        val id = nextId++
        availabilityListeners[id] = listener
        return PixelBackRegistrationImpl(
            id = id,
            disposeAction = { availabilityListeners.remove(id) },
        )
    }

    /** 把一个返回处理器加入栈，并在空/非空边界变化时通知 Host。 */
    private fun addEntry(entry: PixelBackEntry): PixelBackRegistration {
        val wasAvailable = hasRegisteredHandlers
        handlers += entry
        notifyAvailabilityIfChanged(wasAvailable)
        return PixelBackRegistrationImpl(
            id = entry.id,
            disposeAction = { removeEntry(entry) },
        )
    }

    /** 移除 [entry]；若它拥有当前预测会话，会先按取消语义终止该会话。 */
    private fun removeEntry(entry: PixelBackEntry) {
        val wasAvailable = hasRegisteredHandlers
        val removed = handlers.removeAll { candidate -> candidate.id == entry.id }
        if (!removed) return
        if (activePredictiveSession?.entry?.id == entry.id) {
            cancelPredictiveBack()
        }
        notifyAvailabilityIfChanged(wasAvailable)
    }

    /** 仅在 handler 可用性布尔值变化时通知观察者，避免每次重排造成无效注册。 */
    private fun notifyAvailabilityIfChanged(wasAvailable: Boolean) {
        val isAvailable = hasRegisteredHandlers
        if (wasAvailable == isAvailable) return
        availabilityListeners.values.toList().forEach { listener -> listener(isAvailable) }
    }

    /**
     * 从当前 build context 读取最近的 back dispatcher。
     */
    public companion object {
        /**
         * 返回最近的 [PixelBackHost] dispatcher；没有 host 时返回 null。
         */
        public fun maybeOf(context: BuildContext): PixelBackDispatcher? {
            return context.dependOnInheritedWidgetOfExactType(PixelBackScope::class)?.dispatcher
        }

        /**
         * 返回最近的 [PixelBackHost] dispatcher；没有 host 时抛出错误。
         */
        public fun of(context: BuildContext): PixelBackDispatcher {
            return maybeOf(context) ?: error("当前上下文里没有 PixelBackHost。")
        }
    }
}

/** Android 无关的预测返回滑入边缘。 */
public enum class PixelPredictiveBackSwipeEdge {
    /** 手势从屏幕左边缘开始。 */
    Left,

    /** 手势从屏幕右边缘开始。 */
    Right,

    /** 平台没有边缘信息，例如硬件返回键或未知实现。 */
    None,
}

/**
 * 一帧 Android 无关的预测返回数据。
 *
 * [touchX] 与 [touchY] 保留宿主坐标单位；Android Host 中为 View 像素。进度严格位于
 * `0f..1f`，自定义宿主应在构造前完成归一化。
 */
public data class PixelPredictiveBackEvent(
    /** 记录 `PixelBack` 的 `progress` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val progress: Float,
    /** 记录 `PixelBack` 的 `touchX` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val touchX: Float,
    /** 记录 `PixelBack` 的 `touchY` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val touchY: Float,
    /** 记录 `PixelBack` 的 `swipeEdge` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val swipeEdge: PixelPredictiveBackSwipeEdge,
) {
    init {
        require(progress.isFinite() && progress in 0f..1f) {
            "Predictive back progress must be finite and in 0f..1f"
        }
        require(touchX.isFinite() && touchY.isFinite()) {
            "Predictive back touch coordinates must be finite"
        }
    }
}

/**
 * 可取消的预测返回会话回调。
 *
 * `start` 返回 `false` 时 Dispatcher 会继续询问更下层 handler，且不会再向当前 callback
 * 发送 progress/cancel/commit。离散系统返回通过 [onBackInvoked] 单独处理，不能冒充一条
 * 具有完整 start/progress 生命周期的手势。
 */
public interface PixelPredictiveBackCallback {
    /** 尝试锁定本次手势；返回 `true` 表示接受后续事件。 */
    public fun onBackStarted(event: PixelPredictiveBackEvent): Boolean

    /** 接收同一手势单调或非单调变化的最新平台进度。 */
    public fun onBackProgressed(event: PixelPredictiveBackEvent)

    /** 回滚 start/progress 建立的临时视觉状态，不得修改正式返回栈。 */
    public fun onBackCancelled()

    /** 原子提交已开始的手势；返回是否完成了返回动作。 */
    public fun onBackCommitted(): Boolean

    /**
     * 处理没有 progress 生命周期的离散返回。
     *
     * 默认直接使用提交逻辑；需要“先建预览、后提交”的实现应显式重写此方法。
     */
    public fun onBackInvoked(): Boolean = onBackCommitted()
}

/**
 * back handler 注册句柄。
 */
public interface PixelBackRegistration {
    /**
     * 注册 id。
     */
    public val id: Int

    /**
     * 注销当前 handler。
     */
    public fun dispose()
}

private class PixelBackRegistrationImpl(
    override val id: Int,
    private val disposeAction: () -> Unit,
) : PixelBackRegistration {
    /** 句柄是否已经释放，用于保证 dispose 幂等。 */
    private var disposed: Boolean = false

    override fun dispose() {
        if (disposed) return
        disposed = true
        disposeAction()
    }
}

/**
 * 在 widget 子树中提供 [PixelBackDispatcher]。
 */
public fun PixelBackHost(
    dispatcher: PixelBackDispatcher,
    child: Widget,
    key: Any? = null,
): Widget {
    return PixelBackScope(
        dispatcher = dispatcher,
        child = child,
        key = key,
    )
}

/**
 * 在 widget 子树中注册一个 back handler。
 */
public fun PixelBackHandler(
    enabled: Boolean = true,
    onBack: () -> Boolean,
    child: Widget,
    key: Any? = null,
): Widget {
    return PixelBackHandlerWidget(
        enabled = enabled,
        onBack = onBack,
        child = child,
        key = key,
    )
}

/**
 * 在 widget 子树中注册一个支持 Android 14+ progress 的预测返回 handler。
 *
 * [enabled] 为 `false` 时不会占据 Dispatcher 栈位，因此 Host 不会在根页面错误拦截系统
 * 返回。API 24–33 或硬件返回键会走 [PixelPredictiveBackCallback.onBackInvoked]。
 */
public fun PixelPredictiveBackHandler(
    enabled: Boolean = true,
    callback: PixelPredictiveBackCallback,
    child: Widget,
    key: Any? = null,
): Widget {
    return PixelPredictiveBackHandlerWidget(
        enabled = enabled,
        callback = callback,
        child = child,
        key = key,
    )
}

/** Dispatcher 栈中的两种 handler 类型。 */
private sealed interface PixelBackEntry {
    /** 稳定注册标识。 */
    val id: Int

    /** 处理没有 progress 的离散返回。 */
    fun handleInvoked(): Boolean

    /** 提交已经锁定的预测返回会话。 */
    fun handleCommitted(): Boolean

    /** 只有 commit 时执行的传统返回 handler。 */
    data class Discrete(
        override val id: Int,
        val handler: () -> Boolean,
    ) : PixelBackEntry {
        override fun handleInvoked(): Boolean = handler()

        override fun handleCommitted(): Boolean = handler()
    }

    /** 能接收完整预测返回生命周期的 handler。 */
    data class Predictive(
        override val id: Int,
        val callback: PixelPredictiveBackCallback,
    ) : PixelBackEntry {
        override fun handleInvoked(): Boolean = callback.onBackInvoked()

        override fun handleCommitted(): Boolean = callback.onBackCommitted()
    }
}

/** Dispatcher 锁定的 handler 及其提交失败时可用的下层 fallback 快照。 */
private data class PixelPredictiveBackSession(
    val entry: PixelBackEntry,
    val fallbackEntries: List<PixelBackEntry>,
)

private class PixelBackScope(
    val dispatcher: PixelBackDispatcher,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return dispatcher !== (oldWidget as? PixelBackScope)?.dispatcher
    }
}

private class PixelBackHandlerWidget(
    val enabled: Boolean,
    val onBack: () -> Boolean,
    val child: Widget,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = PixelBackHandlerState()
}

private class PixelBackHandlerState : State<PixelBackHandlerWidget>() {
    /** 当前提供注册栈的最近 Dispatcher。 */
    private var dispatcher: PixelBackDispatcher? = null

    /** 当前有效的离散 handler 注册。 */
    private var registration: PixelBackRegistration? = null

    override fun didChangeDependencies() {
        syncRegistration(PixelBackDispatcher.maybeOf(context))
    }

    override fun didUpdateWidget(oldWidget: PixelBackHandlerWidget) {
        if (oldWidget.enabled != widget.enabled) {
            syncRegistration(dispatcher, force = true)
        }
    }

    override fun dispose() {
        registration?.dispose()
        registration = null
        dispatcher = null
    }

    override fun build(context: BuildContext): Widget = widget.child

    /** 让 disabled handler 完全离开栈，保证平台回调只在确有消费者时注册。 */
    private fun syncRegistration(next: PixelBackDispatcher?, force: Boolean = false) {
        if (!force && next === dispatcher) return
        registration?.dispose()
        dispatcher = next
        registration = if (widget.enabled) {
            next?.register { widget.onBack() }
        } else {
            null
        }
    }
}

/** 预测返回 handler 的 retained widget 配置。 */
private class PixelPredictiveBackHandlerWidget(
    val enabled: Boolean,
    val callback: PixelPredictiveBackCallback,
    val child: Widget,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = PixelPredictiveBackHandlerState()
}

/** 管理预测返回 handler 与最近 Dispatcher 之间的注册生命周期。 */
private class PixelPredictiveBackHandlerState : State<PixelPredictiveBackHandlerWidget>() {
    /** 当前提供注册栈的最近 Dispatcher。 */
    private var dispatcher: PixelBackDispatcher? = null

    /** 当前有效的预测返回 handler 注册。 */
    private var registration: PixelBackRegistration? = null

    override fun didChangeDependencies() {
        syncRegistration(PixelBackDispatcher.maybeOf(context))
    }

    override fun didUpdateWidget(oldWidget: PixelPredictiveBackHandlerWidget) {
        if (oldWidget.enabled != widget.enabled) {
            syncRegistration(dispatcher, force = true)
        }
    }

    override fun dispose() {
        registration?.dispose()
        registration = null
        dispatcher = null
    }

    override fun build(context: BuildContext): Widget = widget.child

    /** 同步 enabled/Dispatcher 变化，同时让注册闭包始终读取最新 widget callback。 */
    private fun syncRegistration(next: PixelBackDispatcher?, force: Boolean = false) {
        if (!force && next === dispatcher) return
        registration?.dispose()
        dispatcher = next
        registration = if (widget.enabled) {
            next?.registerPredictive(
                object : PixelPredictiveBackCallback {
                    override fun onBackStarted(event: PixelPredictiveBackEvent): Boolean {
                        return widget.callback.onBackStarted(event)
                    }

                    override fun onBackProgressed(event: PixelPredictiveBackEvent) {
                        widget.callback.onBackProgressed(event)
                    }

                    override fun onBackCancelled() {
                        widget.callback.onBackCancelled()
                    }

                    override fun onBackCommitted(): Boolean {
                        return widget.callback.onBackCommitted()
                    }

                    override fun onBackInvoked(): Boolean {
                        return widget.callback.onBackInvoked()
                    }
                },
            )
        } else {
            null
        }
    }
}
