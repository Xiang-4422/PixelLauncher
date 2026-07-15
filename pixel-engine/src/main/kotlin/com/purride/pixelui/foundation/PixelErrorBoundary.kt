package com.purride.pixelui

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.internal.LeafRenderObjectWidget
import com.purride.pixelui.internal.PixelTextAlign
import com.purride.pixelui.internal.RenderObject
import com.purride.pixelui.internal.RenderText

/**
 * 捕获后代 widget build / render 异常，并用像素后备界面替换失败子树。
 *
 * 没有边界时，异常保持原行为并继续抛给宿主。这个边界只做两件事：
 * 通过 [onError] 上报 [Throwable]，再用 [errorBuilder] 构造替换 widget。
 */
public class PixelErrorBoundary(
    /** `onError` 事件发生时调用的回调；回调执行受 `PixelErrorBoundary` 生命周期约束。 */
    public val onError: ((Throwable) -> Unit)? = null,
    /** 保存 `PixelErrorBoundary` 的 `errorBuilder` 结果或失败信息。 */
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
    return PixelRuntimeErrorPanel(
        message = "ERROR ${message.take(24)}",
        color = color,
        key = key,
    )
}

/**
 * 不依赖标准 widgets artifact 的最小错误文本叶节点。
 *
 * ErrorBoundary 属于 retained runtime；默认后备不能反向依赖公开 `Text` 组件，否则 runtime 与
 * widgets 会形成发布环。该私有节点直接复用 runtime 的 RenderText 和 core 位图字体。
 */
private class PixelRuntimeErrorPanel(
    /** 截断后的稳定错误文案。 */
    private val message: String,
    /** 后备文本的显式高对比颜色。 */
    private val color: PixelColor,
    /** Retained tree 使用的可选业务 key。 */
    key: Any?,
) : LeafRenderObjectWidget(key = key) {
    /** 创建不读取主题或标准组件的文本 RenderObject。 */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderText(
            text = message,
            style = errorTextStyle(),
            textAlign = PixelTextAlign.START,
            textDirection = TextDirection.LTR,
            softWrap = false,
            overflow = PixelTextOverflow.ELLIPSIS,
            maxLines = 1,
            defaultTextRasterizer = PixelBitmapFont.Default,
        )
    }

    /** Retained key 命中时原地更新文案与颜色。 */
    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderText).updateText(
            text = message,
            style = errorTextStyle(),
            textAlign = PixelTextAlign.START,
            textDirection = TextDirection.LTR,
            softWrap = false,
            overflow = PixelTextOverflow.ELLIPSIS,
            maxLines = 1,
            defaultTextRasterizer = PixelBitmapFont.Default,
        )
    }

    /** 构造不继承主题的确定性错误文本样式。 */
    private fun errorTextStyle(): PixelTextStyle = PixelTextStyle(color = color)
}
