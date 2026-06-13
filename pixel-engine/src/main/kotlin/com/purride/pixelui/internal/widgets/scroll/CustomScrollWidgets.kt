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
import com.purride.pixelui.state.PixelSliverListGeometry

internal data class CustomScrollViewWidget(
    val slivers: List<PixelSliver>,
    val state: PixelListState,
    val controller: PixelListController,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val activeLazySliverIndexes = slivers.indices
            .filter { index -> slivers[index] is PixelSliverListBuilder }
            .toSet()
        state.sliverListGeometries.keys.retainAll(activeLazySliverIndexes)
        if (state.pendingSliverScrollIntoView?.sliverIndex !in activeLazySliverIndexes) {
            state.pendingSliverScrollIntoView = null
        }
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
                        val existingGeometry = state.sliverListGeometries[sliverIndex]
                        val geometry = existingGeometry ?: PixelSliverListGeometry(
                            contentStartPx = contentStart,
                            itemCount = sliver.itemCount,
                            estimatedItemExtent = fixedExtent ?: requireNotNull(estimatedExtent),
                            spacing = sliver.spacing,
                            variableHeight = fixedExtent == null,
                        ).also { state.sliverListGeometries[sliverIndex] = it }
                        geometry.update(
                            contentStartPx = existingGeometry?.contentStartPx ?: contentStart,
                            itemCount = sliver.itemCount,
                            estimatedItemExtent = fixedExtent ?: requireNotNull(estimatedExtent),
                            spacing = sliver.spacing,
                            variableHeight = fixedExtent == null,
                        )
                        val totalHeight: Int
                        val range: FixedSliverRange
                        if (fixedExtent != null) {
                            totalHeight = geometry.contentHeightPx()
                            range = FixedSliverRange.resolve(
                                itemCount = sliver.itemCount,
                                itemExtent = fixedExtent,
                                spacing = sliver.spacing,
                                scrollOffsetPx = state.scrollOffsetPx - geometry.contentStartPx,
                                viewportHeightPx = state.viewportHeightPx,
                                cacheExtent = sliver.cacheExtent,
                            )
                        } else {
                            totalHeight = geometry.contentHeightPx()
                            range = FixedSliverRange.resolveVariable(
                                geometry = geometry,
                                scrollOffsetPx = state.scrollOffsetPx - geometry.contentStartPx,
                                viewportHeightPx = state.viewportHeightPx,
                                cacheExtent = sliver.cacheExtent,
                            )
                        }
                        repeat(range.count) { offset ->
                            val itemIndex = range.firstIndex + offset
                            val itemTop = geometry.itemTopPx(itemIndex)
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
                                    measuredItemCount = null,
                                    estimatedExtent = null,
                                    sliverGeometry = geometry,
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
                        val contentTop = estimatedCursorY
                        val stretchExtent = if (sliver.stretch && state.scrollOffsetPx < 0f) {
                            (-state.scrollOffsetPx).toInt().coerceAtMost(sliver.stretchLimit)
                        } else {
                            0
                        }
                        add(
                            CustomScrollChildEntry(
                                sliverIndex = sliverIndex,
                                itemIndex = 0,
                                pinned = true,
                                spacingAfter = 0,
                                minExtent = collapsedHeight,
                                maxExtent = expandedHeight + stretchExtent,
                                baseMaxExtent = expandedHeight,
                                contentTop = contentTop,
                                contentEnd = null,
                                measuredItemCount = null,
                                estimatedExtent = null,
                                floating = sliver.floating,
                                snap = sliver.snap,
                                stretch = sliver.stretch,
                            ),
                        )
                        add(SizedBox(height = expandedHeight + stretchExtent, child = sliver.child, key = sliver.key))
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
    val baseMaxExtent: Int? = null,
    val contentTop: Int?,
    val contentEnd: Int?,
    val measuredItemCount: Int?,
    val estimatedExtent: Int?,
    val floating: Boolean = false,
    val snap: Boolean = false,
    val stretch: Boolean = false,
    val sliverGeometry: PixelSliverListGeometry? = null,
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
            geometry: PixelSliverListGeometry,
            scrollOffsetPx: Float,
            viewportHeightPx: Int,
            cacheExtent: Int,
        ): FixedSliverRange {
            val itemCount = geometry.itemCount
            val safeEstimate = geometry.estimatedItemExtent.coerceAtLeast(1)
            if (itemCount <= 0) return FixedSliverRange(firstIndex = 0, count = 0)
            val safeCache = cacheExtent.coerceAtLeast(0)
            val effectiveViewportHeight = if (viewportHeightPx > 0) viewportHeightPx else safeEstimate * (safeCache + 8)
            val cachePx = safeEstimate * safeCache
            val windowTop = (scrollOffsetPx.toInt() - cachePx).coerceAtLeast(0)
            val windowBottom = scrollOffsetPx.toInt().coerceAtLeast(0) + effectiveViewportHeight + cachePx

            var first = 0
            while (
                first < itemCount - 1 &&
                geometry.itemBottomPx(first) - geometry.contentStartPx <= windowTop
            ) {
                first += 1
            }

            var last = first
            while (
                last < itemCount - 1 &&
                geometry.itemTopPx(last) - geometry.contentStartPx < windowBottom
            ) {
                last += 1
            }
            if (geometry.itemTopPx(last) - geometry.contentStartPx >= windowBottom && last > first) {
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
