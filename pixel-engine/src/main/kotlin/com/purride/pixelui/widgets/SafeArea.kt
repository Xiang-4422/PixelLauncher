package com.purride.pixelui

/**
 * 在 [child] 外侧应用 [MediaQuery.padding]。
 *
 * 这是 pixel-engine 的最小安全区组件：消费 [PixelHostView] 注入的逻辑窗口 inset，
 * 并转换成普通 [Padding]。
 */
public fun SafeArea(
    child: Widget,
    left: Boolean = true,
    top: Boolean = true,
    right: Boolean = true,
    bottom: Boolean = true,
    minimum: PixelWindowInsets = PixelWindowInsets.Zero,
    key: Any? = null,
): Widget {
    return SafeAreaWidget(
        child = child,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        minimum = minimum,
        key = key,
    )
}

/**
 * 根据 [MediaQuery.viewInsets] 避让 Android IME。
 *
 * 该组件只把宿主注入的 IME inset 转成 [Padding]，不负责显示/隐藏键盘，也不滚动聚焦输入框。
 * 通常只需要保留 [bottom]，系统栏安全区请继续使用 [SafeArea]。
 */
public fun ImeAvoidingView(
    child: Widget,
    left: Boolean = false,
    top: Boolean = false,
    right: Boolean = false,
    bottom: Boolean = true,
    minimum: PixelWindowInsets = PixelWindowInsets.Zero,
    key: Any? = null,
): Widget {
    return ViewInsetsPadding(
        child = child,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        minimum = minimum,
        key = key,
    )
}

/**
 * [ImeAvoidingView] 的开发者友好别名。
 *
 * Android 端实际来源仍然是 IME view inset；这个名称用于业务代码里表达“避让软键盘”的意图。
 */
public fun KeyboardAvoidingView(
    child: Widget,
    left: Boolean = false,
    top: Boolean = false,
    right: Boolean = false,
    bottom: Boolean = true,
    minimum: PixelWindowInsets = PixelWindowInsets.Zero,
    key: Any? = null,
): Widget {
    return ImeAvoidingView(
        child = child,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        minimum = minimum,
        key = key,
    )
}

private class SafeAreaWidget(
    private val child: Widget,
    private val left: Boolean,
    private val top: Boolean,
    private val right: Boolean,
    private val bottom: Boolean,
    private val minimum: PixelWindowInsets,
    key: Any?,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        val media = MediaQuery.of(context)
        val insets = media.padding
            .only(left = left, top = top, right = right, bottom = bottom)
            .atLeast(minimum)
        return Padding(
            child = child,
            padding = insets.toEdgeInsets(),
            key = key,
        )
    }
}

private class ViewInsetsPadding(
    private val child: Widget,
    private val left: Boolean,
    private val top: Boolean,
    private val right: Boolean,
    private val bottom: Boolean,
    private val minimum: PixelWindowInsets,
    key: Any?,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        val media = MediaQuery.of(context)
        val insets = media.viewInsets
            .only(left = left, top = top, right = right, bottom = bottom)
            .atLeast(minimum)
        return Padding(
            child = child,
            padding = insets.toEdgeInsets(),
            key = key,
        )
    }
}
