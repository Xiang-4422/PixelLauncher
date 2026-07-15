package com.purride.pixelui

/**
 * CustomScrollView v1 的 sliver 描述。
 */
public sealed interface PixelSliver {
    /** 提供 `PixelSlivers` 用于识别或兼容校验的 `key` 值。 */
    public val key: Any?
}

/**
 * 参与 CustomScrollView 普通滚动流的 widget 列表。
 */
public data class PixelSliverList(
    /** 控制 `PixelSlivers` 的 `items` 时间参数，单位为毫秒。 */
    public val items: List<Widget>,
    /** 定义 `PixelSlivers` 布局中的 `spacing` 逻辑像素度量。 */
    public val spacing: Int,
    override val key: Any?,
) : PixelSliver

/**
 * 固定或估算 item 高度的 lazy SliverList 描述。
 */
public data class PixelSliverListBuilder(
    /** 保存 `PixelSlivers` 的 `itemCount` 计数或索引边界。 */
    public val itemCount: Int,
    /** 提供 `PixelSlivers` 当前管理的 `itemBuilder` 内容。 */
    public val itemBuilder: (Int) -> Widget,
    /** 定义 `PixelSlivers` 布局中的 `itemExtent` 逻辑像素度量。 */
    public val itemExtent: Int?,
    /** 定义 `PixelSlivers` 布局中的 `estimatedItemExtent` 逻辑像素度量。 */
    public val estimatedItemExtent: Int?,
    /** 定义 `PixelSlivers` 布局中的 `spacing` 逻辑像素度量。 */
    public val spacing: Int,
    /** 定义 `PixelSlivers` 布局中的 `cacheExtent` 逻辑像素度量。 */
    public val cacheExtent: Int,
    override val key: Any?,
) : PixelSliver

/**
 * 参与滚动流并在滚过自身时固定在顶部的 header。
 */
public data class PixelSliverPinnedHeader(
    /** 提供 `PixelSlivers` 当前管理的 `child` 内容。 */
    public val child: Widget,
    override val key: Any?,
) : PixelSliver

/**
 * 支持收起、反向浮出、边界吸附和顶部拉伸的 app bar 描述。
 */
public data class PixelSliverAppBar(
    /** 提供 `PixelSlivers` 当前管理的 `child` 内容。 */
    public val child: Widget,
    /** 定义 `PixelSlivers` 布局中的 `expandedHeight` 逻辑像素度量。 */
    public val expandedHeight: Int,
    /** 定义 `PixelSlivers` 布局中的 `collapsedHeight` 逻辑像素度量。 */
    public val collapsedHeight: Int,
    /** 表示 `PixelSlivers` 当前是否满足 `floating` 对应条件。 */
    public val floating: Boolean,
    /** 表示 `PixelSlivers` 当前是否满足 `snap` 对应条件。 */
    public val snap: Boolean,
    /** 表示 `PixelSlivers` 当前是否满足 `stretch` 对应条件。 */
    public val stretch: Boolean,
    /** 记录 `PixelSlivers` 的 `stretchLimit` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val stretchLimit: Int,
    override val key: Any?,
) : PixelSliver
