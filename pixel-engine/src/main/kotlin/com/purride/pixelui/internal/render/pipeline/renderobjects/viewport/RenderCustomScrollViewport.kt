package com.purride.pixelui.internal

import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState

internal class RenderCustomScrollViewport(
    children: List<RenderBox> = emptyList(),
    private var metadata: List<CustomScrollChildEntry>,
    private var state: PixelListState,
    private var controller: PixelListController,
) : MultiChildRenderObject() {
    private val childOffsets = mutableListOf<Int>()

    init {
        setRenderObjectChildren(children)
    }

    fun updateCustomScrollViewport(
        metadata: List<CustomScrollChildEntry>,
        state: PixelListState,
        controller: PixelListController,
    ) {
        if (this.metadata == metadata && this.state === state && this.controller === controller) return
        this.metadata = metadata
        this.state = state
        this.controller = controller
        markNeedsLayout()
        markNeedsPaint()
    }

    override fun setRenderObjectChildren(children: List<RenderObject>) {
        super.setRenderObjectChildren(children)
        resizeChildOffsets(renderChildren.size)
    }

    override fun layout(constraints: RenderConstraints) {
        val children = renderChildren
        val childConstraints = RenderConstraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        var cursorY = 0
        children.forEachIndexed { index, child ->
            val meta = metadata.getOrNull(index)
            val childTop = meta?.contentTop ?: cursorY
            childOffsets[index] = childTop
            val extent = meta?.maxExtent
            child.layout(
                if (extent != null) {
                    RenderConstraints(
                        maxWidth = constraints.maxWidth,
                        minHeight = extent.coerceAtLeast(0),
                        maxHeight = extent.coerceAtLeast(0),
                    )
                } else {
                    childConstraints
                },
            )
            if (meta?.measuredItemCount != null && meta.estimatedExtent != null) {
                state.ensureMeasuredItemCapacity(meta.measuredItemCount)
                if (meta.itemIndex in state.measuredItemHeightsPx.indices) {
                    state.measuredItemHeightsPx[meta.itemIndex] = child.size.height.coerceAtLeast(1)
                }
            }
            cursorY = maxOf(cursorY, childTop + sliverExtent(index, child))
            cursorY += metadata.getOrNull(index)?.spacingAfter?.coerceAtLeast(0) ?: 0
            meta?.contentEnd?.let { contentEnd -> cursorY = maxOf(cursorY, contentEnd) }
        }
        size = RenderSize(width = constraints.maxWidth, height = constraints.maxHeight)
        controller.sync(state = state, viewportHeightPx = size.height, contentHeightPx = cursorY)
        state.itemTopOffsetsPx = childOffsets.toIntArray()
        state.itemHeightsPx = children.mapIndexed { index, child -> sliverExtent(index, child) }.toIntArray()
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val scratch = context.bufferPool.acquire(width = size.width, height = size.height)
        try {
            scratch.clear()
            visibleChildren(includePinned = false).forEach { (index, child) ->
                child.paint(
                    context = PaintContext(buffer = scratch, bufferPool = context.bufferPool),
                    offsetX = 0,
                    offsetY = scrolledChildTop(index),
                )
            }
            pinnedChildren().forEach { (index, child) ->
                child.paint(
                    context = PaintContext(buffer = scratch, bufferPool = context.bufferPool),
                    offsetX = 0,
                    offsetY = pinnedChildTop(index, child),
                )
            }
            context.buffer.blitRegion(
                source = scratch,
                sourceX = 0,
                sourceY = 0,
                copyWidth = size.width,
                copyHeight = size.height,
                destX = offsetX,
                destY = offsetY,
            )
        } finally {
            context.bufferPool.release(scratch)
        }
    }

    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        if (!viewportBounds().contains(localX, localY)) return
        pinnedChildren().asReversed().forEach { (index, child) ->
            val childTop = pinnedChildTop(index, child)
            if (localY in childTop until childTop + child.size.height) {
                child.hitTest(localX = localX, localY = localY - childTop, result = result)
                return
            }
        }
        visibleChildren(includePinned = false).forEach { (index, child) ->
            val childTop = scrolledChildTop(index)
            child.hitTest(localX = localX, localY = localY - childTop, result = result)
        }
    }

    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        collectChildTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectClickTargets(x, y, bucket)
        }
    }

    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) {
        collectChildTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectPagerTargets(x, y, bucket)
        }
    }

    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) {
        targets += PixelListTarget(
            bounds = globalBounds(offsetX, offsetY),
            viewportHeightPx = size.height,
            contentHeightPx = state.contentHeightPx,
            state = state,
            controller = controller,
        )
        collectChildTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectListTargets(x, y, bucket)
        }
    }

    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>) {
        collectChildTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectScrollbarTargets(x, y, bucket)
        }
    }

    override fun collectRefreshTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelRefreshTarget>) {
        collectChildTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectRefreshTargets(x, y, bucket)
        }
    }

    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>) {
        collectChildTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectTextInputTargets(x, y, bucket)
        }
    }

    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) {
        collectChildTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectSliderTargets(x, y, bucket)
        }
    }

    override fun collectSemantics(offsetX: Int, offsetY: Int, nodes: MutableList<com.purride.pixelui.PixelSemanticsNode>) {
        visibleChildren(includePinned = true).forEach { (index, child) ->
            child.collectSemantics(offsetX, offsetY + resolvedChildTop(index), nodes)
        }
    }

    private inline fun <T> collectChildTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<T>,
        collect: (RenderBox, Int, Int, MutableList<T>) -> Unit,
    ) {
        val collected = mutableListOf<T>()
        visibleChildren(includePinned = true).forEach { (index, child) ->
            collect(child, offsetX, offsetY + resolvedChildTop(index), collected)
        }
        val clip = globalBounds(offsetX, offsetY)
        collected.mapNotNullTo(targets) { target -> clipTarget(target, clip) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> clipTarget(target: T, clip: PixelRect): T? {
        return when (target) {
            is PixelClickTarget -> target.bounds.intersect(clip)?.let { target.copy(bounds = it) as T }
            is PixelPagerTarget -> target.bounds.intersect(clip)?.let { target.copy(bounds = it) as T }
            is PixelListTarget -> target.bounds.intersect(clip)?.let { target.copy(bounds = it) as T }
            is PixelScrollbarTarget -> target.bounds.intersect(clip)?.let { target.copy(bounds = it) as T }
            is PixelRefreshTarget -> target.bounds.intersect(clip)?.let { target.copy(bounds = it) as T }
            is PixelTextInputTarget -> target.bounds.intersect(clip)?.let { target.copy(bounds = it) as T }
            is PixelSliderTarget -> target.bounds.intersect(clip)?.let { target.copy(bounds = it) as T }
            else -> target
        }
    }

    private fun visibleChildren(includePinned: Boolean): List<Pair<Int, RenderBox>> {
        val viewportTop = state.scrollOffsetPx.toInt()
        val viewportBottom = viewportTop + size.height
        return renderChildren.mapIndexedNotNull { index, child ->
            val pinned = metadata.getOrNull(index)?.pinned == true
            if (pinned && includePinned) return@mapIndexedNotNull index to child
            if (pinned) return@mapIndexedNotNull null
            val childTop = childOffsets[index]
            val childBottom = childTop + sliverExtent(index, child)
            if (childBottom <= viewportTop || childTop >= viewportBottom) null else index to child
        }
    }

    private fun pinnedChildren(): List<Pair<Int, RenderBox>> {
        return renderChildren.mapIndexedNotNull { index, child ->
            if (metadata.getOrNull(index)?.pinned == true) index to child else null
        }
    }

    private fun resolvedChildTop(index: Int): Int {
        val child = renderChildren.getOrNull(index)
        return if (metadata.getOrNull(index)?.pinned == true && child != null) {
            pinnedChildTop(index, child)
        } else {
            scrolledChildTop(index)
        }
    }

    private fun scrolledChildTop(index: Int): Int = childOffsets[index] - state.scrollOffsetPx.toInt()

    private fun pinnedChildTop(index: Int): Int = scrolledChildTop(index).coerceAtLeast(0)

    private fun sliverExtent(index: Int, child: RenderBox): Int {
        return metadata.getOrNull(index)?.maxExtent ?: child.size.height
    }

    private fun pinnedChildTop(index: Int, child: RenderBox): Int {
        val meta = metadata.getOrNull(index)
        val maxExtent = meta?.maxExtent
        val minExtent = meta?.minExtent
        if (maxExtent != null && minExtent != null) {
            return scrolledChildTop(index).coerceAtLeast(minExtent - maxExtent)
        }
        return pinnedChildTop(index)
    }

    private fun viewportBounds(): PixelRect = PixelRect(0, 0, size.width, size.height)

    private fun globalBounds(offsetX: Int, offsetY: Int): PixelRect = PixelRect(offsetX, offsetY, size.width, size.height)

    private val renderChildren: List<RenderBox>
        get() = children.filterIsInstance<RenderBox>()

    private fun resizeChildOffsets(childCount: Int) {
        while (childOffsets.size < childCount) childOffsets += 0
        while (childOffsets.size > childCount) childOffsets.removeAt(childOffsets.lastIndex)
    }
}
