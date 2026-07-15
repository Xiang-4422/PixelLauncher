package com.purride.pixelui.internal

import com.purride.pixelui.PixelSemanticsCollectionItemInfo
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState

internal class RenderGridViewport(
    children: List<RenderBox> = emptyList(),
    private var firstItemIndex: Int,
    private var itemCount: Int,
    private var cellWidth: Int,
    private var cellHeight: Int,
    private var state: PixelListState,
    private var controller: PixelListController,
    private var spacing: Int = 0,
    private var runSpacing: Int = 0,
) : MultiChildRenderObject() {
    init {
        setRenderObjectChildren(children)
    }

    fun updateGridViewport(
        firstItemIndex: Int,
        itemCount: Int,
        cellWidth: Int,
        cellHeight: Int,
        state: PixelListState,
        controller: PixelListController,
        spacing: Int,
        runSpacing: Int,
    ) {
        if (
            this.firstItemIndex == firstItemIndex &&
            this.itemCount == itemCount &&
            this.cellWidth == cellWidth &&
            this.cellHeight == cellHeight &&
            this.state === state &&
            this.controller === controller &&
            this.spacing == spacing &&
            this.runSpacing == runSpacing
        ) {
            return
        }
        this.firstItemIndex = firstItemIndex
        this.itemCount = itemCount
        this.cellWidth = cellWidth
        this.cellHeight = cellHeight
        this.state = state
        this.controller = controller
        this.spacing = spacing
        this.runSpacing = runSpacing
        markNeedsLayout()
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        val safeCellWidth = cellWidth.coerceAtLeast(1)
        val safeCellHeight = cellHeight.coerceAtLeast(1)
        val columns = gridColumnCount(constraints.maxWidth, safeCellWidth, spacing)
        val childConstraints = RenderConstraints(
            minWidth = safeCellWidth,
            maxWidth = safeCellWidth,
            minHeight = safeCellHeight,
            maxHeight = safeCellHeight,
        )
        renderChildren.forEach { child -> child.layout(childConstraints) }
        size = RenderSize(width = constraints.maxWidth, height = constraints.maxHeight)
        val contentHeight = contentHeightPx(itemCount, columns, safeCellHeight, runSpacing)
        state.viewportWidthPx = size.width
        controller.sync(
            state = state,
            viewportHeightPx = size.height,
            contentHeightPx = contentHeight,
        )
        state.itemTopOffsetsPx = IntArray(itemCount.coerceAtLeast(0)) { index ->
            itemOffset(index, columns).y
        }
        state.itemHeightsPx = IntArray(itemCount.coerceAtLeast(0)) { safeCellHeight }
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val scratch = context.bufferPool.acquire(width = size.width, height = size.height)
        try {
            val columns = currentColumns()
            renderChildren.forEachIndexed { localIndex, child ->
                val itemIndex = firstItemIndex + localIndex
                val position = itemOffset(itemIndex, columns)
                child.paint(
                    context = context.derive(scratch, offsetX, offsetY),
                    offsetX = position.x,
                    offsetY = position.y - state.scrollOffsetPx.toInt(),
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
        val contentY = localY + state.scrollOffsetPx.toInt()
        val columns = currentColumns()
        renderChildren.forEachIndexed { localIndex, child ->
            val itemIndex = firstItemIndex + localIndex
            val position = itemOffset(itemIndex, columns)
            child.hitTest(localX = localX - position.x, localY = contentY - position.y, result = result)
        }
    }

    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        val collected = mutableListOf<PixelClickTarget>()
        collectChildTargets(offsetX, offsetY) { child, childOffsetX, childOffsetY ->
            child.collectClickTargets(childOffsetX, childOffsetY, collected)
        }
        appendClippedTargets(collected, targets, offsetX, offsetY) { it.bounds }
    }

    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) {
        val collected = mutableListOf<PixelPagerTarget>()
        collectChildTargets(offsetX, offsetY) { child, childOffsetX, childOffsetY ->
            child.collectPagerTargets(childOffsetX, childOffsetY, collected)
        }
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds -> target.copy(bounds = bounds) }
        }
    }

    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) {
        targets += PixelListTarget(
            bounds = globalBounds(offsetX, offsetY),
            viewportHeightPx = size.height,
            contentHeightPx = state.contentHeightPx,
            state = state,
            controller = controller,
            source = this,
        )
        val collected = mutableListOf<PixelListTarget>()
        collectChildTargets(offsetX, offsetY) { child, childOffsetX, childOffsetY ->
            child.collectListTargets(childOffsetX, childOffsetY, collected)
        }
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds -> target.copy(bounds = bounds) }
        }
    }

    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>) {
        val collected = mutableListOf<PixelTextInputTarget>()
        collectChildTargets(offsetX, offsetY) { child, childOffsetX, childOffsetY ->
            child.collectTextInputTargets(childOffsetX, childOffsetY, collected)
        }
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds -> target.copy(bounds = bounds) }
        }
    }

    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) {
        val collected = mutableListOf<PixelSliderTarget>()
        collectChildTargets(offsetX, offsetY) { child, childOffsetX, childOffsetY ->
            child.collectSliderTargets(childOffsetX, childOffsetY, collected)
        }
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds -> target.copy(bounds = bounds) }
        }
    }

    /** Exports semantics only for mounted grid cells intersecting the viewport. */
    override fun collectSemantics(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelSemanticsTarget>,
    ) {
        val collected = mutableListOf<PixelSemanticsTarget>()
        val columns = currentColumns()
        renderChildren.forEachIndexed { localIndex, child ->
            val itemIndex = firstItemIndex + localIndex
            val position = itemOffset(itemIndex, columns)
            val itemTargets = mutableListOf<PixelSemanticsTarget>()
            child.collectSemantics(
                offsetX = offsetX + position.x,
                offsetY = offsetY + position.y - state.scrollOffsetPx.toInt(),
                targets = itemTargets,
            )
            collected += itemTargets.withGridItemInfo(
                rowIndex = itemIndex / columns.coerceAtLeast(1),
                columnIndex = itemIndex % columns.coerceAtLeast(1),
            )
        }
        val clip = globalBounds(offsetX, offsetY)
        targets += clipSemanticTargets(collected, clip)
    }

    private fun collectChildTargets(
        offsetX: Int,
        offsetY: Int,
        collect: (RenderBox, Int, Int) -> Unit,
    ) {
        val columns = currentColumns()
        renderChildren.forEachIndexed { localIndex, child ->
            val itemIndex = firstItemIndex + localIndex
            val position = itemOffset(itemIndex, columns)
            collect(child, offsetX + position.x, offsetY + position.y - state.scrollOffsetPx.toInt())
        }
    }

    private fun appendClippedTargets(
        collected: List<PixelClickTarget>,
        targets: MutableList<PixelClickTarget>,
        offsetX: Int,
        offsetY: Int,
        bounds: (PixelClickTarget) -> PixelRect,
    ) {
        collected.mapNotNullTo(targets) { target ->
            bounds(target).intersect(globalBounds(offsetX, offsetY))?.let { clipped -> target.copy(bounds = clipped) }
        }
    }

    private fun viewportBounds(): PixelRect {
        return PixelRect(left = 0, top = 0, width = size.width, height = size.height)
    }

    private fun globalBounds(offsetX: Int, offsetY: Int): PixelRect {
        return PixelRect(left = offsetX, top = offsetY, width = size.width, height = size.height)
    }

    private fun currentColumns(): Int = gridColumnCount(size.width, cellWidth, spacing)

    private fun itemOffset(index: Int, columns: Int): GridItemOffset {
        val safeColumns = columns.coerceAtLeast(1)
        val safeSpacing = spacing.coerceAtLeast(0)
        val safeRunSpacing = runSpacing.coerceAtLeast(0)
        val safeCellWidth = cellWidth.coerceAtLeast(1)
        val safeCellHeight = cellHeight.coerceAtLeast(1)
        val row = index / safeColumns
        val column = index % safeColumns
        return GridItemOffset(
            x = column * (safeCellWidth + safeSpacing),
            y = row * (safeCellHeight + safeRunSpacing),
        )
    }

    private val renderChildren: List<RenderBox>
        get() = children.filterIsInstance<RenderBox>()
}

private fun contentHeightPx(itemCount: Int, columns: Int, cellHeight: Int, runSpacing: Int): Int {
    if (itemCount <= 0) return 0
    val rows = (itemCount + columns.coerceAtLeast(1) - 1) / columns.coerceAtLeast(1)
    return rows * cellHeight.coerceAtLeast(1) + (rows - 1).coerceAtLeast(0) * runSpacing.coerceAtLeast(0)
}

private data class GridItemOffset(val x: Int, val y: Int)

/** Adds grid coordinates only to roots so keyed descendant identities remain untouched. */
private fun List<PixelSemanticsTarget>.withGridItemInfo(
    rowIndex: Int,
    columnIndex: Int,
): List<PixelSemanticsTarget> {
    val localIds = mapTo(mutableSetOf()) { target -> target.node.id }
    return map { target ->
        if (target.node.parentId == null || target.node.parentId !in localIds) {
            target.copy(
                node = target.node.copy(
                    collectionItemInfo = PixelSemanticsCollectionItemInfo(
                        rowIndex = rowIndex,
                        columnIndex = columnIndex,
                        selected = target.node.selected,
                    ),
                ),
            )
        } else {
            target
        }
    }
}
