package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState

internal data class GridViewWidget(
    val items: List<Widget>,
    val cellWidth: Int,
    val cellHeight: Int,
    val state: PixelListState,
    val controller: PixelListController,
    val spacing: Int,
    val runSpacing: Int,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        return GridViewportWidget(
            children = items,
            firstItemIndex = 0,
            itemCount = items.size,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            state = state,
            controller = controller,
            spacing = spacing,
            runSpacing = runSpacing,
            key = key,
        )
    }
}

internal data class LazyGridViewWidget(
    val itemCount: Int,
    val itemBuilder: (Int) -> Widget,
    val cellWidth: Int,
    val cellHeight: Int,
    val state: PixelListState,
    val controller: PixelListController,
    val spacing: Int,
    val runSpacing: Int,
    val cacheExtent: Int,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val range = LazyGridRange.resolve(
            itemCount = itemCount,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            spacing = spacing,
            runSpacing = runSpacing,
            scrollOffsetPx = state.scrollOffsetPx,
            viewportWidthPx = state.viewportWidthPx,
            viewportHeightPx = state.viewportHeightPx,
            cacheExtent = cacheExtent,
        )
        return GridViewportWidget(
            children = List(range.count) { offset -> itemBuilder(range.firstIndex + offset) },
            firstItemIndex = range.firstIndex,
            itemCount = itemCount,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            state = state,
            controller = controller,
            spacing = spacing,
            runSpacing = runSpacing,
            key = key,
        )
    }
}

private data class GridViewportWidget(
    override val children: List<Widget>,
    val firstItemIndex: Int,
    val itemCount: Int,
    val cellWidth: Int,
    val cellHeight: Int,
    val state: PixelListState,
    val controller: PixelListController,
    val spacing: Int,
    val runSpacing: Int,
    override val key: Any? = null,
) : MultiChildRenderObjectWidget(children = children, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderGridViewport(
            firstItemIndex = firstItemIndex,
            itemCount = itemCount,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            state = state,
            controller = controller,
            spacing = spacing,
            runSpacing = runSpacing,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderGridViewport).updateGridViewport(
            firstItemIndex = firstItemIndex,
            itemCount = itemCount,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            state = state,
            controller = controller,
            spacing = spacing,
            runSpacing = runSpacing,
        )
    }
}

private data class LazyGridRange(
    val firstIndex: Int,
    val count: Int,
) {
    companion object {
        fun resolve(
            itemCount: Int,
            cellWidth: Int,
            cellHeight: Int,
            spacing: Int,
            runSpacing: Int,
            scrollOffsetPx: Float,
            viewportWidthPx: Int,
            viewportHeightPx: Int,
            cacheExtent: Int,
        ): LazyGridRange {
            if (itemCount <= 0 || cellWidth <= 0 || cellHeight <= 0) {
                return LazyGridRange(firstIndex = 0, count = 0)
            }
            val columns = gridColumnCount(viewportWidthPx.takeIf { it > 0 } ?: cellWidth * 4, cellWidth, spacing)
            val rowStride = cellHeight + runSpacing.coerceAtLeast(0)
            val safeCache = cacheExtent.coerceAtLeast(0)
            val effectiveViewportHeight = viewportHeightPx.takeIf { it > 0 } ?: cellHeight * (safeCache + 8)
            val firstRow = ((scrollOffsetPx.toInt() / rowStride) - safeCache).coerceAtLeast(0)
            val lastRow = ((scrollOffsetPx.toInt() + effectiveViewportHeight + rowStride - 1) / rowStride + safeCache)
                .coerceAtLeast(firstRow)
            val first = (firstRow * columns).coerceIn(0, itemCount - 1)
            val last = ((lastRow + 1) * columns - 1).coerceIn(first, itemCount - 1)
            return LazyGridRange(firstIndex = first, count = last - first + 1)
        }
    }
}

internal fun gridColumnCount(width: Int, cellWidth: Int, spacing: Int): Int {
    val safeCellWidth = cellWidth.coerceAtLeast(1)
    val safeSpacing = spacing.coerceAtLeast(0)
    return ((width + safeSpacing) / (safeCellWidth + safeSpacing)).coerceAtLeast(1)
}
