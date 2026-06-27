package com.purride.pixelui

import com.purride.pixelcore.PixelColor

/**
 * [ToastQueue] 中的一条消息。
 *
 * [id] 由 [PixelToastQueueController] 分配，调用方可用它关闭指定 toast。
 */
public data class PixelToastQueueItem(
    val id: Int,
    val message: String,
    val fillColor: PixelColor = PixelColor.Black,
    val textStyle: PixelTextStyle = PixelTextStyle.Default,
)

/**
 * 轻量 toast 队列控制器。
 *
 * 控制器只保存消息队列，不内置计时器、动画或 overlay 生命周期；业务侧负责在合适时机调用
 * [enqueue]、[dismissCurrent] 或 [dismiss]。
 */
public class PixelToastQueueController : ChangeNotifier() {
    private var nextId = 1
    private var items: List<PixelToastQueueItem> = emptyList()

    /**
     * 当前排队消息数量。
     */
    public val size: Int
        get() = items.size

    /**
     * 队首消息；没有消息时为 null。
     */
    public val current: PixelToastQueueItem?
        get() = items.firstOrNull()

    /**
     * 追加一条 toast 消息并返回对应 item。
     */
    public fun enqueue(
        message: String,
        fillColor: PixelColor = PixelColor.Black,
        textStyle: PixelTextStyle = PixelTextStyle.Default,
    ): PixelToastQueueItem {
        val item = PixelToastQueueItem(
            id = nextId++,
            message = message,
            fillColor = fillColor,
            textStyle = textStyle,
        )
        items = items + item
        notifyListeners()
        return item
    }

    /**
     * 关闭当前队首 toast。
     */
    public fun dismissCurrent(): Boolean {
        if (items.isEmpty()) return false
        items = items.drop(1)
        notifyListeners()
        return true
    }

    /**
     * 关闭指定 id 的 toast。
     */
    public fun dismiss(id: Int): Boolean {
        val next = items.filterNot { it.id == id }
        if (next.size == items.size) return false
        items = next
        notifyListeners()
        return true
    }

    /**
     * 清空所有排队 toast。
     */
    public fun clear() {
        if (items.isEmpty()) return
        items = emptyList()
        notifyListeners()
    }
}

/**
 * 显示 [PixelToastQueueController] 队首消息的 widget。
 *
 * 该组件适合放入 [PixelOverlayHost] 或页面级 [Stack]；没有消息时渲染为空尺寸占位。
 */
public fun ToastQueue(
    controller: PixelToastQueueController,
    key: Any? = null,
): Widget {
    return ToastQueueWidget(controller = controller, key = key)
}

private class ToastQueueWidget(
    private val controller: PixelToastQueueController,
    key: Any?,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val item = controller.current ?: return SizedBox(width = 0, height = 0, key = key)
        return Toast(
            message = item.message,
            fillColor = item.fillColor,
            textStyle = item.textStyle,
            key = key,
        )
    }
}
