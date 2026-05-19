package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState

/**
 * Flutter 风格 `ListView` 的 direct pipeline widget。
 */
internal data class ListViewWidget(
    val items: List<Widget>,
    val state: PixelListState,
    val controller: PixelListController,
    val spacing: Int,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 在 build 时监听 controller，并返回 direct list viewport。
     */
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        return ListViewportWidget(
            children = items,
            state = state,
            controller = controller,
            spacing = spacing,
            key = key,
        )
    }
}

/**
 * 固定 item 高度的 lazy `ListViewBuilder`。
 *
 * 这条路径只在调用方显式传入 `itemExtent` 时启用，保留原 `ListViewBuilder`
 * 的 eager 兼容行为。
 */
internal data class LazyListViewWidget(
    val itemCount: Int,
    val itemBuilder: (Int) -> Widget,
    val itemExtent: Int,
    val state: PixelListState,
    val controller: PixelListController,
    val spacing: Int,
    val cacheExtent: Int,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val range = LazyListRange.resolve(
            itemCount = itemCount,
            itemExtent = itemExtent,
            spacing = spacing,
            scrollOffsetPx = state.scrollOffsetPx,
            viewportHeightPx = state.viewportHeightPx,
            cacheExtent = cacheExtent,
        )
        return LazyListViewportWidget(
            children = List(range.count) { offset ->
                itemBuilder(range.firstIndex + offset)
            },
            firstItemIndex = range.firstIndex,
            itemCount = itemCount,
            itemExtent = itemExtent,
            state = state,
            controller = controller,
            spacing = spacing,
            key = key,
        )
    }
}

/**
 * 固定 item 与 separator 高度的 lazy `ListViewSeparatedBuilder`。
 */
internal data class LazySeparatedListViewWidget(
    val itemCount: Int,
    val itemBuilder: (Int) -> Widget,
    val separatorBuilder: (Int) -> Widget,
    val itemExtent: Int,
    val separatorExtent: Int,
    val state: PixelListState,
    val controller: PixelListController,
    val cacheExtent: Int,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val range = LazySeparatedListRange.resolve(
            itemCount = itemCount,
            itemExtent = itemExtent,
            separatorExtent = separatorExtent,
            scrollOffsetPx = state.scrollOffsetPx,
            viewportHeightPx = state.viewportHeightPx,
            cacheExtent = cacheExtent,
        )
        return LazySeparatedListViewportWidget(
            children = List(range.count) { offset ->
                val virtualIndex = range.firstVirtualIndex + offset
                if (virtualIndex % 2 == 0) {
                    itemBuilder(virtualIndex / 2)
                } else {
                    separatorBuilder(virtualIndex / 2)
                }
            },
            firstVirtualIndex = range.firstVirtualIndex,
            itemCount = itemCount,
            itemExtent = itemExtent,
            separatorExtent = separatorExtent,
            state = state,
            controller = controller,
            key = key,
        )
    }
}

/**
 * `ListView` 对应的多子节点 render object widget。
 */
private data class ListViewportWidget(
    override val children: List<Widget>,
    val state: PixelListState,
    val controller: PixelListController,
    val spacing: Int,
    override val key: Any? = null,
) : MultiChildRenderObjectWidget(
    children = children,
    key = key,
) {
    /**
     * 创建垂直列表视口 render object。
     */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderListViewport(
            state = state,
            controller = controller,
            spacing = spacing,
        )
    }

    /**
     * 同步列表视口配置。
     */
    override fun updateRenderObject(
        context: BuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderListViewport).updateListViewport(
            state = state,
            controller = controller,
            spacing = spacing,
        )
    }
}

/**
 * 固定高度 lazy list 对应的 render object widget。
 */
private data class LazyListViewportWidget(
    override val children: List<Widget>,
    val firstItemIndex: Int,
    val itemCount: Int,
    val itemExtent: Int,
    val state: PixelListState,
    val controller: PixelListController,
    val spacing: Int,
    override val key: Any? = null,
) : MultiChildRenderObjectWidget(
    children = children,
    key = key,
) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderLazyListViewport(
            firstItemIndex = firstItemIndex,
            itemCount = itemCount,
            itemExtent = itemExtent,
            state = state,
            controller = controller,
            spacing = spacing,
        )
    }

    override fun updateRenderObject(
        context: BuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderLazyListViewport).updateLazyListViewport(
            firstItemIndex = firstItemIndex,
            itemCount = itemCount,
            itemExtent = itemExtent,
            state = state,
            controller = controller,
            spacing = spacing,
        )
    }
}

/**
 * 固定高度 separated lazy list 对应的 render object widget。
 */
private data class LazySeparatedListViewportWidget(
    override val children: List<Widget>,
    val firstVirtualIndex: Int,
    val itemCount: Int,
    val itemExtent: Int,
    val separatorExtent: Int,
    val state: PixelListState,
    val controller: PixelListController,
    override val key: Any? = null,
) : MultiChildRenderObjectWidget(
    children = children,
    key = key,
) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderLazySeparatedListViewport(
            firstVirtualIndex = firstVirtualIndex,
            itemCount = itemCount,
            itemExtent = itemExtent,
            separatorExtent = separatorExtent,
            state = state,
            controller = controller,
        )
    }

    override fun updateRenderObject(
        context: BuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderLazySeparatedListViewport).updateLazySeparatedListViewport(
            firstVirtualIndex = firstVirtualIndex,
            itemCount = itemCount,
            itemExtent = itemExtent,
            separatorExtent = separatorExtent,
            state = state,
            controller = controller,
        )
    }
}

private data class LazyListRange(
    val firstIndex: Int,
    val count: Int,
) {
    companion object {
        fun resolve(
            itemCount: Int,
            itemExtent: Int,
            spacing: Int,
            scrollOffsetPx: Float,
            viewportHeightPx: Int,
            cacheExtent: Int,
        ): LazyListRange {
            if (itemCount <= 0 || itemExtent <= 0) {
                return LazyListRange(firstIndex = 0, count = 0)
            }
            val safeSpacing = spacing.coerceAtLeast(0)
            val itemStride = itemExtent + safeSpacing
            val safeCache = cacheExtent.coerceAtLeast(0)
            val effectiveViewportHeight = if (viewportHeightPx > 0) {
                viewportHeightPx
            } else {
                itemExtent * (safeCache + 8)
            }
            val first = ((scrollOffsetPx.toInt() / itemStride) - safeCache)
                .coerceIn(0, itemCount - 1)
            val visibleEndPx = scrollOffsetPx.toInt() + effectiveViewportHeight
            val last = ((visibleEndPx + itemStride - 1) / itemStride + safeCache)
                .coerceIn(first, itemCount - 1)
            return LazyListRange(
                firstIndex = first,
                count = last - first + 1,
            )
        }
    }
}

private data class LazySeparatedListRange(
    val firstVirtualIndex: Int,
    val count: Int,
) {
    companion object {
        fun resolve(
            itemCount: Int,
            itemExtent: Int,
            separatorExtent: Int,
            scrollOffsetPx: Float,
            viewportHeightPx: Int,
            cacheExtent: Int,
        ): LazySeparatedListRange {
            val virtualCount = separatedVirtualCount(itemCount)
            val safeItemExtent = itemExtent.coerceAtLeast(0)
            val safeSeparatorExtent = separatorExtent.coerceAtLeast(0)
            val stride = safeItemExtent + safeSeparatorExtent
            if (virtualCount <= 0 || safeItemExtent <= 0 || stride <= 0) {
                return LazySeparatedListRange(firstVirtualIndex = 0, count = 0)
            }
            val safeCache = cacheExtent.coerceAtLeast(0)
            val effectiveViewportHeight = if (viewportHeightPx > 0) {
                viewportHeightPx
            } else {
                safeItemExtent * (safeCache + 8)
            }
            val cachePx = stride * safeCache
            val windowTop = (scrollOffsetPx.toInt() - cachePx).coerceAtLeast(0)
            val windowBottom = scrollOffsetPx.toInt() + effectiveViewportHeight + cachePx
            val firstItemIndex = (windowTop / stride).coerceIn(0, itemCount - 1)
            var firstVirtualIndex = (firstItemIndex * 2 - 1).coerceAtLeast(0)
            while (
                firstVirtualIndex < virtualCount - 1 &&
                virtualTopPx(firstVirtualIndex, safeItemExtent, safeSeparatorExtent) +
                    virtualExtentPx(firstVirtualIndex, safeItemExtent, safeSeparatorExtent) <= windowTop
            ) {
                firstVirtualIndex += 1
            }

            var lastVirtualIndex = firstVirtualIndex
            while (
                lastVirtualIndex < virtualCount - 1 &&
                virtualTopPx(lastVirtualIndex, safeItemExtent, safeSeparatorExtent) < windowBottom
            ) {
                lastVirtualIndex += 1
            }
            if (
                virtualTopPx(lastVirtualIndex, safeItemExtent, safeSeparatorExtent) >= windowBottom &&
                lastVirtualIndex > firstVirtualIndex
            ) {
                lastVirtualIndex -= 1
            }
            return LazySeparatedListRange(
                firstVirtualIndex = firstVirtualIndex,
                count = lastVirtualIndex - firstVirtualIndex + 1,
            )
        }
    }
}

internal fun separatedVirtualCount(itemCount: Int): Int {
    return if (itemCount <= 0) 0 else (itemCount * 2) - 1
}

internal fun virtualTopPx(
    virtualIndex: Int,
    itemExtent: Int,
    separatorExtent: Int,
): Int {
    val safeItemExtent = itemExtent.coerceAtLeast(0)
    val safeSeparatorExtent = separatorExtent.coerceAtLeast(0)
    val stride = safeItemExtent + safeSeparatorExtent
    return if (virtualIndex % 2 == 0) {
        (virtualIndex / 2) * stride
    } else {
        (virtualIndex / 2) * stride + safeItemExtent
    }
}

internal fun virtualExtentPx(
    virtualIndex: Int,
    itemExtent: Int,
    separatorExtent: Int,
): Int {
    return if (virtualIndex % 2 == 0) {
        itemExtent.coerceAtLeast(0)
    } else {
        separatorExtent.coerceAtLeast(0)
    }
}
