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
