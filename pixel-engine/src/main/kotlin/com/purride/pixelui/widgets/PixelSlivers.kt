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
 * 固定 item 高度的 lazy SliverList 描述。
 */
public data class PixelSliverListBuilder(
    public val itemCount: Int,
    public val itemBuilder: (Int) -> Widget,
    public val itemExtent: Int,
    public val spacing: Int,
    public val cacheExtent: Int,
    override val key: Any?,
) : PixelSliver

/**
 * 参与滚动流并在滚过自身时固定在顶部的 header。
 */
public data class PixelSliverPinnedHeader(
    public val child: Widget,
    override val key: Any?,
) : PixelSliver

/**
 * 参与滚动流并在滚过后收起到 collapsedHeight 的 app bar。
 */
public data class PixelSliverAppBar(
    public val child: Widget,
    public val expandedHeight: Int,
    public val collapsedHeight: Int,
    override val key: Any?,
) : PixelSliver
