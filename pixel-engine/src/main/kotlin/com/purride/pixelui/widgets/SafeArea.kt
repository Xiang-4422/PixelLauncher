package com.purride.pixelui

/**
 * Adds padding from [MediaQuery.padding] around [child].
 *
 * This is the pixel-engine equivalent of a minimal Flutter `SafeArea`: it consumes logical
 * window inset values injected by [PixelHostView] and turns them into a regular [Padding].
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
