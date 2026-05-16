package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.InternalBuildContext
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
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
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
        context: InternalBuildContext,
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
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
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
        context: InternalBuildContext,
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
