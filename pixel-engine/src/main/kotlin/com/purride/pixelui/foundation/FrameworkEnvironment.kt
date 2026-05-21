package com.purride.pixelui

import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelcore.ScreenProfile

public enum class TextDirection {
    LTR,
    RTL,
}

public data class MediaQueryData(
    val logicalWidth: Int,
    val logicalHeight: Int,
    val screenProfile: ScreenProfile,
)

public class Directionality(
    public val textDirection: TextDirection,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(
    child = child,
    key = key,
) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return textDirection != (oldWidget as? Directionality)?.textDirection
    }

    public companion object {
        public fun maybeOf(context: BuildContext): TextDirection? {
            return context.dependOnInheritedWidgetOfExactType<Directionality>()?.textDirection
        }

        public fun of(context: BuildContext): TextDirection {
            return maybeOf(context) ?: TextDirection.LTR
        }
    }
}

/**
 * 在 widget 树里继承当前的默认 [PixelTextRasterizer]。
 *
 * `TextWidget` / `RichTextWidget` 通过 `DefaultTextRasterizer.of(context)`
 * 读取这个 inherited 值；宿主层 `PixelHostView` 在根环境包装时自动注入
 * `hostView.textRasterizer`，因此运行时切换文本栅格器对全树即时生效。
 *
 * 单条 widget 仍可以通过 `PixelTextStyle.textRasterizer` 进行点对点覆盖。
 */
public class DefaultTextRasterizer(
    public val rasterizer: PixelTextRasterizer,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(
    child = child,
    key = key,
) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return rasterizer !== (oldWidget as? DefaultTextRasterizer)?.rasterizer
    }

    public companion object {
        public fun maybeOf(context: BuildContext): PixelTextRasterizer? {
            return context.dependOnInheritedWidgetOfExactType<DefaultTextRasterizer>()?.rasterizer
        }

        public fun of(context: BuildContext, fallback: PixelTextRasterizer): PixelTextRasterizer {
            return maybeOf(context) ?: fallback
        }
    }
}

public class MediaQuery(
    public val data: MediaQueryData,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(
    child = child,
    key = key,
) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return data != (oldWidget as? MediaQuery)?.data
    }

    public companion object {
        public fun maybeOf(context: BuildContext): MediaQueryData? {
            return context.dependOnInheritedWidgetOfExactType<MediaQuery>()?.data
        }

        public fun of(context: BuildContext): MediaQueryData {
            return maybeOf(context)
                ?: error("当前上下文里没有 MediaQuery，宿主需要先包一层 MediaQuery。")
        }
    }
}
