package com.purride.pixelui

import com.purride.pixelcore.PixelColor

/**
 * 最小 overlay 控制器。
 *
 * 它只管理当前显示的 overlay widget 列表，不负责动画和超时。
 * 当子树内存在 [PixelBackHost] 时，最上层 overlay 会优先响应 back。
 */
public class PixelOverlayController : ChangeNotifier() {
    private var nextId = 1
    private var items: List<PixelOverlayItem> = emptyList()

    /**
     * 当前 overlay 数量。
     */
    public val size: Int
        get() = items.size

    /**
     * 显示一个自定义 overlay widget。
     */
    public fun show(widget: Widget): PixelOverlayHandle {
        val id = nextId++
        items = items + PixelOverlayItem(id = id, widget = widget)
        notifyListeners()
        return DefaultPixelOverlayHandle(controller = this, id = id)
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
            Toast(
                message = message,
                fillColor = fillColor,
                textStyle = textStyle,
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
        return show(
            Dialog(
                title = title,
                content = content,
                actions = actions,
                fillColor = fillColor,
                borderColor = borderColor,
            ),
        )
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
            Positioned(
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
        )
    }

    /**
     * 关闭指定 id 的 overlay。
     */
    public fun dismiss(id: Int): Boolean {
        val next = items.filterNot { it.id == id }
        if (next.size == items.size) return false
        items = next
        notifyListeners()
        return true
    }

    /**
     * 关闭当前最上层 overlay。
     */
    public fun dismissTop(): Boolean {
        val top = items.lastOrNull() ?: return false
        return dismiss(top.id)
    }

    /**
     * 清空所有 overlay。
     */
    public fun clear() {
        if (items.isEmpty()) return
        items = emptyList()
        notifyListeners()
    }

    internal fun widgets(): List<Widget> = items.map { item -> item.widget }

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
     * overlay 在当前 controller 内的 id。
     */
    public val id: Int

    /**
     * 关闭当前 overlay。
     */
    public fun dismiss(): Boolean
}

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

private data class PixelOverlayItem(
    val id: Int,
    val widget: Widget,
)

private class DefaultPixelOverlayHandle(
    private val controller: PixelOverlayController,
    override val id: Int,
) : PixelOverlayHandle {
    override fun dismiss(): Boolean = controller.dismiss(id)
}

private class PixelOverlayHostWidget(
    private val controller: PixelOverlayController,
    private val child: Widget,
    override val key: Any?,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val overlays = controller.widgets()
        val children = buildList {
            add(child)
            if (overlays.isNotEmpty()) {
                add(
                    PixelBackHandler(
                        onBack = { controller.dismissTop() },
                        child = Stack(children = overlays),
                        key = "pixel-overlay-back",
                    ),
                )
            }
        }
        val content = Stack(children = children)
        return PixelOverlayScope(controller = controller, child = content)
    }
}

private class PixelOverlayScope(
    val controller: PixelOverlayController,
    override val child: Widget,
) : InheritedWidget(child = child) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return controller !== (oldWidget as? PixelOverlayScope)?.controller
    }
}
