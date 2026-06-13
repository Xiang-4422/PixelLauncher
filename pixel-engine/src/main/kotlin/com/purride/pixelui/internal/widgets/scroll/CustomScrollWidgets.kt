package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelSliver
import com.purride.pixelui.PixelSliverAppBar
import com.purride.pixelui.PixelSliverList
import com.purride.pixelui.PixelSliverListBuilder
import com.purride.pixelui.PixelSliverPinnedHeader
import com.purride.pixelui.SizedBox
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState

internal data class CustomScrollViewWidget(
    val slivers: List<PixelSliver>,
    val state: PixelListState,
    val controller: PixelListController,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        var estimatedCursorY = 0
        val entries = buildList {
            slivers.forEachIndexed { sliverIndex, sliver ->
                when (sliver) {
                    is PixelSliverList -> {
                        sliver.items.forEachIndexed { itemIndex, item ->
                            add(
                                CustomScrollChildEntry(
                                    sliverIndex = sliverIndex,
                                    itemIndex = itemIndex,
                                    pinned = false,
                                    spacingAfter = if (itemIndex < sliver.items.lastIndex) sliver.spacing.coerceAtLeast(0) else 0,
                                    minExtent = null,
                                    maxExtent = null,
                                    contentTop = null,
                                    contentEnd = null,
                                    measuredItemCount = null,
                                    estimatedExtent = null,
                                ),
                            )
                            add(item)
                        }
                        estimatedCursorY += 0
                    }
                    is PixelSliverListBuilder -> {
                        val contentStart = estimatedCursorY
                        val fixedExtent = sliver.itemExtent
                        val estimatedExtent = sliver.estimatedItemExtent
                        val totalHeight: Int
                        val range: FixedSliverRange
                        if (fixedExtent != null) {
                            totalHeight = fixedListContentHeight(
                                itemCount = sliver.itemCount,
                                itemExtent = fixedExtent,
                                spacing = sliver.spacing,
                            )
                            range = FixedSliverRange.resolve(
                                itemCount = sliver.itemCount,
                                itemExtent = fixedExtent,
                                spacing = sliver.spacing,
                                scrollOffsetPx = state.scrollOffsetPx - contentStart,
                                viewportHeightPx = state.viewportHeightPx,
                                cacheExtent = sliver.cacheExtent,
                            )
                        } else {
                            val safeEstimate = requireNotNull(estimatedExtent).coerceAtLeast(1)
                            state.ensureMeasuredItemCapacity(sliver.itemCount)
                            totalHeight = variableItemContentHeightPx(
                                state = state,
                                itemCount = sliver.itemCount,
                                estimatedItemExtent = safeEstimate,
                                spacing = sliver.spacing,
                            )
                            range = FixedSliverRange.resolveVariable(
                                itemCount = sliver.itemCount,
                                state = state,
                                estimatedItemExtent = safeEstimate,
                                spacing = sliver.spacing,
                                scrollOffsetPx = state.scrollOffsetPx - contentStart,
                                viewportHeightPx = state.viewportHeightPx,
                                cacheExtent = sliver.cacheExtent,
                            )
                        }
                        repeat(range.count) { offset ->
                            val itemIndex = range.firstIndex + offset
                            val itemTop = if (fixedExtent != null) {
                                contentStart + itemIndex * (fixedExtent.coerceAtLeast(1) + sliver.spacing.coerceAtLeast(0))
                            } else {
                                contentStart + variableItemTopPx(
                                    state = state,
                                    itemIndex = itemIndex,
                                    estimatedItemExtent = requireNotNull(estimatedExtent).coerceAtLeast(1),
                                    spacing = sliver.spacing,
                                )
                            }
                            add(
                                CustomScrollChildEntry(
                                    sliverIndex = sliverIndex,
                                    itemIndex = itemIndex,
                                    pinned = false,
                                    spacingAfter = if (itemIndex < sliver.itemCount - 1) sliver.spacing.coerceAtLeast(0) else 0,
                                    minExtent = null,
                                    maxExtent = fixedExtent?.coerceAtLeast(1),
                                    contentTop = itemTop,
                                    contentEnd = contentStart + totalHeight,
                                    measuredItemCount = if (fixedExtent == null) sliver.itemCount else null,
                                    estimatedExtent = estimatedExtent,
                                ),
                            )
                            add(sliver.itemBuilder(itemIndex))
                        }
                        estimatedCursorY += totalHeight
                    }
                    is PixelSliverPinnedHeader -> {
                        add(
                            CustomScrollChildEntry(
                                sliverIndex = sliverIndex,
                                itemIndex = 0,
                                pinned = true,
                                spacingAfter = 0,
                                minExtent = null,
                                maxExtent = null,
                                contentTop = null,
                                contentEnd = null,
                                measuredItemCount = null,
                                estimatedExtent = null,
                            ),
                        )
                        add(sliver.child)
                    }
                    is PixelSliverAppBar -> {
                        val expandedHeight = sliver.expandedHeight.coerceAtLeast(0)
                        val collapsedHeight = sliver.collapsedHeight.coerceIn(0, expandedHeight.coerceAtLeast(0))
                        add(
                            CustomScrollChildEntry(
                                sliverIndex = sliverIndex,
                                itemIndex = 0,
                                pinned = true,
                                spacingAfter = 0,
                                minExtent = collapsedHeight,
                                maxExtent = expandedHeight,
                                contentTop = null,
                                contentEnd = null,
                                measuredItemCount = null,
                                estimatedExtent = null,
                            ),
                        )
                        add(SizedBox(height = expandedHeight, child = sliver.child, key = sliver.key))
                        estimatedCursorY += expandedHeight
                    }
                }
            }
        }
        val metadata = entries.filterIsInstance<CustomScrollChildEntry>()
        val children = entries.filterIsInstance<Widget>()
        return CustomScrollViewportWidget(
            children = children,
            metadata = metadata,
            state = state,
            controller = controller,
            key = key,
        )
    }
}

internal data class CustomScrollChildEntry(
    val sliverIndex: Int,
    val itemIndex: Int,
    val pinned: Boolean,
    val spacingAfter: Int,
    val minExtent: Int?,
    val maxExtent: Int?,
    val contentTop: Int?,
    val contentEnd: Int?,
    val measuredItemCount: Int?,
    val estimatedExtent: Int?,
)

private data class CustomScrollViewportWidget(
    override val children: List<Widget>,
    val metadata: List<CustomScrollChildEntry>,
    val state: PixelListState,
    val controller: PixelListController,
    override val key: Any? = null,
) : MultiChildRenderObjectWidget(children = children, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderCustomScrollViewport(
            metadata = metadata,
            state = state,
            controller = controller,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderCustomScrollViewport).updateCustomScrollViewport(
            metadata = metadata,
            state = state,
            controller = controller,
        )
    }
}

private data class FixedSliverRange(
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
        ): FixedSliverRange {
            if (itemCount <= 0 || itemExtent <= 0) return FixedSliverRange(firstIndex = 0, count = 0)
            val safeSpacing = spacing.coerceAtLeast(0)
            val itemStride = itemExtent + safeSpacing
            val safeCache = cacheExtent.coerceAtLeast(0)
            val effectiveViewportHeight = if (viewportHeightPx > 0) viewportHeightPx else itemExtent * (safeCache + 8)
            val scrollTop = scrollOffsetPx.toInt().coerceAtLeast(0)
            val first = ((scrollTop / itemStride) - safeCache).coerceIn(0, itemCount - 1)
            val visibleEndPx = scrollTop + effectiveViewportHeight
            val last = ((visibleEndPx + itemStride - 1) / itemStride + safeCache).coerceIn(first, itemCount - 1)
            return FixedSliverRange(firstIndex = first, count = last - first + 1)
        }

        fun resolveVariable(
            itemCount: Int,
            state: PixelListState,
            estimatedItemExtent: Int,
            spacing: Int,
            scrollOffsetPx: Float,
            viewportHeightPx: Int,
            cacheExtent: Int,
        ): FixedSliverRange {
            val safeEstimate = estimatedItemExtent.coerceAtLeast(1)
            if (itemCount <= 0) return FixedSliverRange(firstIndex = 0, count = 0)
            val safeSpacing = spacing.coerceAtLeast(0)
            val safeCache = cacheExtent.coerceAtLeast(0)
            val effectiveViewportHeight = if (viewportHeightPx > 0) viewportHeightPx else safeEstimate * (safeCache + 8)
            val cachePx = safeEstimate * safeCache
            val windowTop = (scrollOffsetPx.toInt() - cachePx).coerceAtLeast(0)
            val windowBottom = scrollOffsetPx.toInt().coerceAtLeast(0) + effectiveViewportHeight + cachePx

            var first = 0
            while (
                first < itemCount - 1 &&
                variableItemBottomPx(state, first, safeEstimate, safeSpacing) <= windowTop
            ) {
                first += 1
            }

            var last = first
            while (
                last < itemCount - 1 &&
                variableItemTopPx(state, last, safeEstimate, safeSpacing) < windowBottom
            ) {
                last += 1
            }
            if (variableItemTopPx(state, last, safeEstimate, safeSpacing) >= windowBottom && last > first) {
                last -= 1
            }
            return FixedSliverRange(firstIndex = first, count = last - first + 1)
        }
    }
}

private fun fixedListContentHeight(
    itemCount: Int,
    itemExtent: Int,
    spacing: Int,
): Int {
    if (itemCount <= 0 || itemExtent <= 0) return 0
    return (itemCount * itemExtent) + ((itemCount - 1).coerceAtLeast(0) * spacing.coerceAtLeast(0))
}
