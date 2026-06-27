package com.purride.pixelui

import com.purride.pixelcore.PixelColor

/**
 * 捕获后代 widget build 异常，并用像素后备界面替换失败子树。
 *
 * 没有边界时，build 异常保持原行为并继续抛给宿主。这个边界只做两件事：
 * 通过 [onError] 上报 [Throwable]，再用 [errorBuilder] 构造替换 widget。
 */
public class PixelErrorBoundary(
    public val onError: ((Throwable) -> Unit)? = null,
    public val errorBuilder: (Throwable) -> Widget = { error ->
        PixelErrorPanel(message = error.message ?: error::class.simpleName.orEmpty())
    },
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        val oldBoundary = oldWidget as? PixelErrorBoundary ?: return true
        return onError !== oldBoundary.onError ||
            errorBuilder !== oldBoundary.errorBuilder ||
            child !== oldBoundary.child
    }

    internal fun fallbackFor(error: Throwable): Widget {
        onError?.invoke(error)
        return errorBuilder(error)
    }
}

/**
 * [PixelErrorBoundary] 的默认可见后备界面。
 */
public fun PixelErrorPanel(
    message: String,
    color: PixelColor = PixelColor.fromRgb(255, 80, 80),
    key: Any? = null,
): Widget {
    return Text(
        data = "ERROR ${message.take(24)}",
        color = color,
        overflow = PixelTextOverflow.ELLIPSIS,
        key = key,
    )
}
