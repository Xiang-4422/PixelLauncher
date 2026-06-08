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
 * Logical pixel insets exposed to widgets through [MediaQuery].
 *
 * Values are already converted into pixel-engine logical coordinates. Host integrations should
 * map platform/system insets into this type before injecting them into the widget tree.
 */
public data class PixelWindowInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    /**
     * Converts this inset value into the existing layout padding type.
     */
    public fun toEdgeInsets(): EdgeInsets {
        return EdgeInsets(left = left, top = top, right = right, bottom = bottom)
    }

    /**
     * Returns a copy with disabled sides zeroed out.
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
     * Applies per-side minimum inset values.
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
         * No system or window inset.
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
