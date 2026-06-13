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
        state.separatedItemGeometryActive = false
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
        state.separatedItemGeometryActive = false
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
 * 显式估算 item 高度的变高 lazy `ListViewBuilder`。
 */
internal data class VariableLazyListViewWidget(
    val itemCount: Int,
    val itemBuilder: (Int) -> Widget,
    val estimatedItemExtent: Int,
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
        state.separatedItemGeometryActive = false
        state.ensureMeasuredItemCapacity(itemCount)
        val range = VariableLazyListRange.resolve(
            itemCount = itemCount,
            state = state,
            estimatedItemExtent = estimatedItemExtent,
            spacing = spacing,
            scrollOffsetPx = state.scrollOffsetPx,
            viewportHeightPx = state.viewportHeightPx,
            cacheExtent = cacheExtent,
        )
        return VariableLazyListViewportWidget(
            children = List(range.count) { offset ->
                itemBuilder(range.firstIndex + offset)
            },
            firstItemIndex = range.firstIndex,
            itemCount = itemCount,
            estimatedItemExtent = estimatedItemExtent,
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
    val itemExtent: Int?,
    val separatorExtent: Int?,
    val estimatedItemExtent: Int?,
    val estimatedSeparatorExtent: Int?,
    val state: PixelListState,
    val controller: PixelListController,
    val cacheExtent: Int,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        state.separatedItemGeometryActive = true
        state.separatedItemExtentVariable = itemExtent == null
        state.ensureSeparatedVirtualMeasuredCapacity(itemCount)
        val resolvedItemExtent = itemExtent ?: requireNotNull(estimatedItemExtent)
        val resolvedSeparatorExtent = separatorExtent ?: estimatedSeparatorExtent ?: 1
        val range = LazySeparatedListRange.resolve(
            itemCount = itemCount,
            state = state,
            itemExtent = itemExtent,
            separatorExtent = separatorExtent,
            estimatedItemExtent = resolvedItemExtent,
            estimatedSeparatorExtent = resolvedSeparatorExtent,
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
            estimatedItemExtent = resolvedItemExtent,
            estimatedSeparatorExtent = resolvedSeparatorExtent,
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
 * 变高 lazy list 对应的 render object widget。
 */
private data class VariableLazyListViewportWidget(
    override val children: List<Widget>,
    val firstItemIndex: Int,
    val itemCount: Int,
    val estimatedItemExtent: Int,
    val state: PixelListState,
    val controller: PixelListController,
    val spacing: Int,
    override val key: Any? = null,
) : MultiChildRenderObjectWidget(
    children = children,
    key = key,
) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderVariableLazyListViewport(
            firstItemIndex = firstItemIndex,
            itemCount = itemCount,
            estimatedItemExtent = estimatedItemExtent,
            state = state,
            controller = controller,
            spacing = spacing,
        )
    }

    override fun updateRenderObject(
        context: BuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderVariableLazyListViewport).updateVariableLazyListViewport(
            firstItemIndex = firstItemIndex,
            itemCount = itemCount,
            estimatedItemExtent = estimatedItemExtent,
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
    val itemExtent: Int?,
    val separatorExtent: Int?,
    val estimatedItemExtent: Int,
    val estimatedSeparatorExtent: Int,
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
            estimatedItemExtent = estimatedItemExtent,
            estimatedSeparatorExtent = estimatedSeparatorExtent,
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
            estimatedItemExtent = estimatedItemExtent,
            estimatedSeparatorExtent = estimatedSeparatorExtent,
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
            state: PixelListState,
            itemExtent: Int?,
            separatorExtent: Int?,
            estimatedItemExtent: Int,
            estimatedSeparatorExtent: Int,
            scrollOffsetPx: Float,
            viewportHeightPx: Int,
            cacheExtent: Int,
        ): LazySeparatedListRange {
            val virtualCount = separatedVirtualCount(itemCount)
            val safeItemExtent = estimatedItemExtent.coerceAtLeast(1)
            val safeSeparatorExtent = estimatedSeparatorExtent.coerceAtLeast(1)
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
            var firstVirtualIndex = 0
            while (
                firstVirtualIndex < virtualCount - 1 &&
                separatedVirtualBottomPx(
                    state,
                    firstVirtualIndex,
                    itemExtent,
                    separatorExtent,
                    safeItemExtent,
                    safeSeparatorExtent,
                ) <= windowTop
            ) {
                firstVirtualIndex += 1
            }

            var lastVirtualIndex = firstVirtualIndex
            while (
                lastVirtualIndex < virtualCount - 1 &&
                separatedVirtualTopPx(
                    state,
                    lastVirtualIndex,
                    itemExtent,
                    separatorExtent,
                    safeItemExtent,
                    safeSeparatorExtent,
                ) < windowBottom
            ) {
                lastVirtualIndex += 1
            }
            if (
                separatedVirtualTopPx(
                    state,
                    lastVirtualIndex,
                    itemExtent,
                    separatorExtent,
                    safeItemExtent,
                    safeSeparatorExtent,
                ) >= windowBottom &&
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

private data class VariableLazyListRange(
    val firstIndex: Int,
    val count: Int,
) {
    companion object {
        fun resolve(
            itemCount: Int,
            state: PixelListState,
            estimatedItemExtent: Int,
            spacing: Int,
            scrollOffsetPx: Float,
            viewportHeightPx: Int,
            cacheExtent: Int,
        ): VariableLazyListRange {
            val safeEstimate = estimatedItemExtent.coerceAtLeast(1)
            if (itemCount <= 0) {
                return VariableLazyListRange(firstIndex = 0, count = 0)
            }
            val safeSpacing = spacing.coerceAtLeast(0)
            val safeCache = cacheExtent.coerceAtLeast(0)
            val effectiveViewportHeight = if (viewportHeightPx > 0) {
                viewportHeightPx
            } else {
                safeEstimate * (safeCache + 8)
            }
            val cachePx = safeEstimate * safeCache
            val windowTop = (scrollOffsetPx.toInt() - cachePx).coerceAtLeast(0)
            val windowBottom = scrollOffsetPx.toInt() + effectiveViewportHeight + cachePx

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
            if (
                variableItemTopPx(state, last, safeEstimate, safeSpacing) >= windowBottom &&
                last > first
            ) {
                last -= 1
            }
            return VariableLazyListRange(
                firstIndex = first,
                count = last - first + 1,
            )
        }
    }
}

internal fun PixelListState.ensureMeasuredItemCapacity(itemCount: Int) {
    val safeItemCount = itemCount.coerceAtLeast(0)
    if (measuredItemHeightsPx.size == safeItemCount) {
        return
    }
    val previous = measuredItemHeightsPx
    measuredItemHeightsPx = IntArray(safeItemCount) { index ->
        previous.getOrNull(index) ?: 0
    }
}

internal fun variableItemTopPx(
    state: PixelListState,
    itemIndex: Int,
    estimatedItemExtent: Int,
    spacing: Int,
): Int {
    val safeSpacing = spacing.coerceAtLeast(0)
    var top = 0
    for (index in 0 until itemIndex.coerceAtLeast(0)) {
        top += variableItemHeightPx(state, index, estimatedItemExtent)
        top += safeSpacing
    }
    return top
}

internal fun variableItemBottomPx(
    state: PixelListState,
    itemIndex: Int,
    estimatedItemExtent: Int,
    spacing: Int,
): Int {
    return variableItemTopPx(state, itemIndex, estimatedItemExtent, spacing) +
        variableItemHeightPx(state, itemIndex, estimatedItemExtent)
}

internal fun variableItemContentHeightPx(
    state: PixelListState,
    itemCount: Int,
    estimatedItemExtent: Int,
    spacing: Int,
): Int {
    if (itemCount <= 0) {
        return 0
    }
    var height = 0
    val safeSpacing = spacing.coerceAtLeast(0)
    repeat(itemCount) { index ->
        height += variableItemHeightPx(state, index, estimatedItemExtent)
        if (index < itemCount - 1) {
            height += safeSpacing
        }
    }
    return height
}

internal fun variableItemHeightPx(
    state: PixelListState,
    itemIndex: Int,
    estimatedItemExtent: Int,
): Int {
    val measured = state.measuredItemHeightsPx.getOrNull(itemIndex) ?: 0
    return if (measured > 0) measured else estimatedItemExtent.coerceAtLeast(1)
}

internal fun separatedVirtualCount(itemCount: Int): Int {
    return if (itemCount <= 0) 0 else (itemCount * 2) - 1
}

internal fun PixelListState.ensureSeparatedVirtualMeasuredCapacity(itemCount: Int) {
    val virtualCount = separatedVirtualCount(itemCount)
    if (measuredSeparatedVirtualHeightsPx.size == virtualCount) return
    val previous = measuredSeparatedVirtualHeightsPx
    measuredSeparatedVirtualHeightsPx = IntArray(virtualCount) { index ->
        previous.getOrNull(index) ?: 0
    }
}

internal fun separatedVirtualExtentPx(
    state: PixelListState,
    virtualIndex: Int,
    itemExtent: Int?,
    separatorExtent: Int?,
    estimatedItemExtent: Int,
    estimatedSeparatorExtent: Int,
): Int {
    val fixedExtent = if (virtualIndex % 2 == 0) itemExtent else separatorExtent
    if (fixedExtent != null) return fixedExtent.coerceAtLeast(1)
    val measured = state.measuredSeparatedVirtualHeightsPx.getOrNull(virtualIndex) ?: 0
    if (measured > 0) return measured
    return if (virtualIndex % 2 == 0) {
        estimatedItemExtent.coerceAtLeast(1)
    } else {
        estimatedSeparatorExtent.coerceAtLeast(1)
    }
}

internal fun separatedVirtualTopPx(
    state: PixelListState,
    virtualIndex: Int,
    itemExtent: Int?,
    separatorExtent: Int?,
    estimatedItemExtent: Int,
    estimatedSeparatorExtent: Int,
): Int {
    var top = 0
    for (index in 0 until virtualIndex.coerceAtLeast(0)) {
        top += separatedVirtualExtentPx(
            state,
            index,
            itemExtent,
            separatorExtent,
            estimatedItemExtent,
            estimatedSeparatorExtent,
        )
    }
    return top
}

internal fun separatedVirtualBottomPx(
    state: PixelListState,
    virtualIndex: Int,
    itemExtent: Int?,
    separatorExtent: Int?,
    estimatedItemExtent: Int,
    estimatedSeparatorExtent: Int,
): Int {
    return separatedVirtualTopPx(
        state,
        virtualIndex,
        itemExtent,
        separatorExtent,
        estimatedItemExtent,
        estimatedSeparatorExtent,
    ) + separatedVirtualExtentPx(
        state,
        virtualIndex,
        itemExtent,
        separatorExtent,
        estimatedItemExtent,
        estimatedSeparatorExtent,
    )
}

internal fun separatedVariableContentHeightPx(
    state: PixelListState,
    itemCount: Int,
    itemExtent: Int?,
    separatorExtent: Int?,
    estimatedItemExtent: Int,
    estimatedSeparatorExtent: Int,
): Int {
    val virtualCount = separatedVirtualCount(itemCount)
    var height = 0
    repeat(virtualCount) { virtualIndex ->
        height += separatedVirtualExtentPx(
            state,
            virtualIndex,
            itemExtent,
            separatorExtent,
            estimatedItemExtent,
            estimatedSeparatorExtent,
        )
    }
    return height
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
