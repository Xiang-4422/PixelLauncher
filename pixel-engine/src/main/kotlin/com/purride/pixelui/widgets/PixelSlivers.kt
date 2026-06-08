package com.purride.pixelui

/**
 * CustomScrollView v1 的 sliver 描述。
 */
public sealed interface PixelSliver {
    public val key: Any?
}

/**
 * 参与 CustomScrollView 普通滚动流的 widget 列表。
 */
public data class PixelSliverList(
    public val items: List<Widget>,
    public val spacing: Int,
    override val key: Any?,
) : PixelSliver

/**
 * 参与滚动流并在滚过自身时固定在顶部的 header。
 */
public data class PixelSliverPinnedHeader(
    public val child: Widget,
    override val key: Any?,
) : PixelSliver
