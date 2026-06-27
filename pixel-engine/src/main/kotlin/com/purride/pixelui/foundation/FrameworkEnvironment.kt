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
    val viewInsets: PixelWindowInsets = PixelWindowInsets.Zero,
    val viewPadding: PixelWindowInsets = PixelWindowInsets.Zero,
    val padding: PixelWindowInsets = viewPadding,
) {
    public constructor(
        logicalWidth: Int,
        logicalHeight: Int,
        screenProfile: ScreenProfile,
    ) : this(
        logicalWidth = logicalWidth,
        logicalHeight = logicalHeight,
        screenProfile = screenProfile,
        viewInsets = PixelWindowInsets.Zero,
        viewPadding = PixelWindowInsets.Zero,
        padding = PixelWindowInsets.Zero,
    )
}

/**
 * 通过 [MediaQuery] 暴露给 widget 的逻辑像素 inset。
 *
 * 值已经转换到 pixel-engine 逻辑坐标。Android 宿主负责把系统栏、IME 等平台
 * inset 映射成这个类型，再注入 widget 树。
 */
public data class PixelWindowInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    /**
     * 转成布局层使用的 [EdgeInsets]。
     */
    public fun toEdgeInsets(): EdgeInsets {
        return EdgeInsets(left = left, top = top, right = right, bottom = bottom)
    }

    /**
     * 返回只保留指定方向的副本。
     */
    public fun only(
        left: Boolean = true,
        top: Boolean = true,
        right: Boolean = true,
        bottom: Boolean = true,
    ): PixelWindowInsets {
        return PixelWindowInsets(
            left = if (left) this.left else 0,
            top = if (top) this.top else 0,
            right = if (right) this.right else 0,
            bottom = if (bottom) this.bottom else 0,
        )
    }

    /**
     * 对每个方向应用最小 inset。
     */
    public fun atLeast(minimum: PixelWindowInsets): PixelWindowInsets {
        return PixelWindowInsets(
            left = maxOf(left, minimum.left),
            top = maxOf(top, minimum.top),
            right = maxOf(right, minimum.right),
            bottom = maxOf(bottom, minimum.bottom),
        )
    }

    public companion object {
        /**
         * 无系统栏或窗口 inset。
         */
        public val Zero: PixelWindowInsets = PixelWindowInsets()
    }
}

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
