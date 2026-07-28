package com.purride.pixelui

import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelcore.ScreenProfile

/** 定义 `TextDirection` 在 `FrameworkEnvironment` 中承担的数据与行为边界。
 *
 * Logical reading direction used by directional layout, text, and overlay placement.
 */
public enum class TextDirection {
    /** Places logical start at the left edge. */
    LTR,

    /** Places logical start at the right edge. */
    RTL,
}

/**
 * 定义 `MediaQueryData` 在 `FrameworkEnvironment` 中承担的数据与行为边界。
 *
 * Immutable logical viewport data exposed by [MediaQuery].
 *
 * 宿主环境能力（locale、文字缩放、对比度、动效、密度、刷新率、显示特性）属于
 * [HostCapabilities]，与这里的逻辑视口度量是两类独立关注点，不会合并进本数据类。
 *
 * @property logicalWidth Available logical pixel columns.
 * @property logicalHeight Available logical pixel rows.
 * @property screenProfile Physical-to-logical pixel mapping used for the current frame.
 * @property viewInsets Transient obscured edges such as the software keyboard.
 * @property viewPadding Stable safe edges such as system bars and display cutouts.
 * @property padding Stable safe edges after transient overlapping obscuration is excluded.
 */
public data class MediaQueryData(
    val logicalWidth: Int,
    val logicalHeight: Int,
    val screenProfile: ScreenProfile,
    val viewInsets: PixelWindowInsets = PixelWindowInsets.Zero,
    val viewPadding: PixelWindowInsets = PixelWindowInsets.Zero,
    val padding: PixelWindowInsets = viewPadding,
) {
    /**
 * 创建 `FrameworkEnvironment` 实例并建立初始不变量。
 *
     * Creates viewport data without any safe or obscured edges.
     *
     * This constructor is retained as the original three-argument JVM compatibility entry.
     */
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
    /** Logical inset from the left edge. */
    val left: Int = 0,
    /** Logical inset from the top edge. */
    val top: Int = 0,
    /** Logical inset from the right edge. */
    val right: Int = 0,
    /** Logical inset from the bottom edge. */
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

    /** 集中提供 `FrameworkEnvironment` 的 `<companion>` 共享入口。
 *
 * Shared canonical inset values.
 */
    public companion object {
        /**
         * 无系统栏或窗口 inset。
         */
        public val Zero: PixelWindowInsets = PixelWindowInsets()
    }
}

/**
 * 定义 `Directionality` 在 `FrameworkEnvironment` 中承担的数据与行为边界。
 *
 * Inherited logical reading direction for directional layout and text consumers.
 *
 * @property textDirection Direction supplied to the descendant subtree.
 * @property child Descendant widget receiving this direction.
 * @property key Optional retained identity for the inherited boundary.
 */
public class Directionality(
    public val textDirection: TextDirection,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(
    child = child,
    key = key,
) {
    /** Notifies dependents only when the logical direction changes. */
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return textDirection != (oldWidget as? Directionality)?.textDirection
    }

    /** 集中提供 `FrameworkEnvironment` 的 `<companion>` 共享入口。
 *
 * Direction lookup helpers for descendant build contexts.
 */
    public companion object {
        /** 执行 `FrameworkEnvironment` 的 `maybeOf` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest direction, or `null` outside a [Directionality] boundary.
 */
        public fun maybeOf(context: BuildContext): TextDirection? {
            return context.dependOnInheritedWidgetOfExactType<Directionality>()?.textDirection
        }

        /** 执行 `FrameworkEnvironment` 的 `of` 公开行为；具体参数、返回和副作用见下文。
 *
 * 返回最近的阅读方向；没有 [Directionality] 作用域时回到确定的 LTR 默认值。
 */
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
    /** 公开 `FrameworkEnvironment` 的 `rasterizer` 配置或运行值。
 *
 * Rasterizer inherited by text without an explicit style-level override.
 */
    public val rasterizer: PixelTextRasterizer,
    /** Descendant widget receiving the default rasterizer. */
    override val child: Widget,
    /** Optional retained identity for this inherited boundary. */
    override val key: Any? = null,
) : InheritedWidget(
    child = child,
    key = key,
) {
    /** Notifies dependents only when the rasterizer instance changes. */
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return rasterizer !== (oldWidget as? DefaultTextRasterizer)?.rasterizer
    }

    /** 集中提供 `FrameworkEnvironment` 的 `<companion>` 共享入口。
 *
 * Rasterizer lookup helpers for descendant build contexts.
 */
    public companion object {
        /** 执行 `FrameworkEnvironment` 的 `maybeOf` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest rasterizer, or `null` when no Host or explicit scope provides one.
 */
        public fun maybeOf(context: BuildContext): PixelTextRasterizer? {
            return context.dependOnInheritedWidgetOfExactType<DefaultTextRasterizer>()?.rasterizer
        }

        /** 执行 `FrameworkEnvironment` 的 `of` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest rasterizer or the caller-owned [fallback].
 */
        public fun of(context: BuildContext, fallback: PixelTextRasterizer): PixelTextRasterizer {
            return maybeOf(context) ?: fallback
        }
    }
}

/**
 * 定义 `MediaQuery` 在 `FrameworkEnvironment` 中承担的数据与行为边界。
 *
 * Inherited logical viewport metrics for layout consumers.
 *
 * 这里只承载逻辑视口尺寸与 inset；locale、文字缩放、对比度、动效等宿主环境信号属于
 * [HostCapabilities]，请直接从那里读取。
 *
 * @property data Logical metrics supplied to descendants.
 * @property child Descendant widget receiving the metrics.
 * @property key Optional retained identity for this inherited boundary.
 */
public class MediaQuery(
    public val data: MediaQueryData,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(
    child = child,
    key = key,
) {
    /** 仅当不可变视口快照发生变化时才通知依赖方。 */
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return data != (oldWidget as? MediaQuery)?.data
    }

    /** 集中提供 `FrameworkEnvironment` 的 `<companion>` 共享入口。
 *
 * Logical viewport lookup helpers.
 */
    public companion object {
        /** 执行 `FrameworkEnvironment` 的 `maybeOf` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest viewport data, or `null` outside a [MediaQuery] boundary.
 */
        public fun maybeOf(context: BuildContext): MediaQueryData? {
            return context.dependOnInheritedWidgetOfExactType<MediaQuery>()?.data
        }

        /** 执行 `FrameworkEnvironment` 的 `of` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest viewport data or fails when the Host root is missing.
 */
        public fun of(context: BuildContext): MediaQueryData {
            return maybeOf(context)
                ?: error("当前上下文里没有 MediaQuery，宿主需要先包一层 MediaQuery。")
        }
    }
}
