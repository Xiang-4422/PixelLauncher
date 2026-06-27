package com.purride.pixelui

/**
 * widget 树内的 back 事件调度器。
 *
 * handler 按注册顺序入栈，最近挂载的 handler 先处理。返回 true 表示已消费。
 */
public class PixelBackDispatcher {
    private var nextId = 1
    private val handlers = mutableListOf<PixelBackEntry>()

    /**
     * 注册一个 back handler。
     */
    public fun register(handler: () -> Boolean): PixelBackRegistration {
        val entry = PixelBackEntry(id = nextId++, handler = handler)
        handlers += entry
        return PixelBackRegistrationImpl(
            id = entry.id,
            disposeAction = { handlers.removeAll { it.id == entry.id } },
        )
    }

    /**
     * 从栈顶开始派发 back 事件。
     */
    public fun handleBack(): Boolean {
        val snapshot = handlers.toList()
        for (entry in snapshot.asReversed()) {
            if (entry.handler()) return true
        }
        return false
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
    override fun dispose() { disposeAction() }
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

private data class PixelBackEntry(
    val id: Int,
    val handler: () -> Boolean,
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
    private var dispatcher: PixelBackDispatcher? = null
    private var registration: PixelBackRegistration? = null

    override fun didChangeDependencies() {
        val next = PixelBackDispatcher.maybeOf(context)
        if (next === dispatcher) return
        registration?.dispose()
        dispatcher = next
        registration = next?.register {
            if (widget.enabled) widget.onBack() else false
        }
    }

    override fun dispose() {
        registration?.dispose()
        registration = null
        dispatcher = null
    }

    override fun build(context: BuildContext): Widget = widget.child
}
