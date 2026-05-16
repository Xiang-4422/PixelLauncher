package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState

/**
 * 新渲染管线里的单子节点垂直滚动视口。
 */
internal class RenderSingleChildScrollViewport(
    child: RenderBox? = null,
    private var state: PixelListState,
    private var controller: PixelListController,
) : SingleChildRenderObject() {
    init {
        setRenderObjectChild(child)
    }

    /**
     * 同步滚动状态和控制器引用。
     */
    fun updateScrollViewport(
        state: PixelListState,
        controller: PixelListController,
    ) {
        this.state = state
        this.controller = controller
        markNeedsLayout()
        markNeedsPaint()
    }

    /**
     * 按父约束布局视口，并把子内容高度同步给 controller。
     */
    override fun layout(constraints: RenderConstraints) {
        val child = renderChild
        child?.layout(
            constraints = RenderConstraints(
                maxWidth = constraints.maxWidth,
                maxHeight = safeScrollableMaxHeight(constraints.maxHeight),
            ),
        )
        val contentHeight = child?.size?.height ?: 0
        size = RenderSize(
            width = constraints.maxWidth,
            height = constraints.maxHeight,
        )
        controller.sync(
            state = state,
            viewportHeightPx = size.height,
            contentHeightPx = contentHeight,
        )
        state.itemTopOffsetsPx = intArrayOf(0)
        state.itemHeightsPx = intArrayOf(contentHeight)
    }

    /**
     * 把滚动后的子内容裁剪绘制到当前视口。
     */
    override fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        val child = renderChild ?: return
        val scratch = PixelBuffer(
            width = size.width,
            height = child.size.height.coerceAtLeast(size.height),
        )
        child.paint(
            context = PaintContext(buffer = scratch),
            offsetX = 0,
            offsetY = 0,
        )
        context.buffer.blit(
            source = scratch,
            destX = offsetX,
            destY = offsetY,
            sourceY = state.scrollOffsetPx.toInt(),
            copyWidth = size.width,
            copyHeight = size.height,
        )
    }

    /**
     * 在滚动后的子内容坐标中执行命中测试。
     */
    override fun hitTest(
        localX: Int,
        localY: Int,
        result: HitTestResult,
    ) {
        if (!viewportBounds().contains(localX, localY)) {
            return
        }
        renderChild?.hitTest(
            localX = localX,
            localY = localY + state.scrollOffsetPx.toInt(),
            result = result,
        )
    }

    /**
     * 导出裁剪后的点击目标。
     */
    override fun collectClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ) {
        val collected = mutableListOf<PixelClickTarget>()
        renderChild?.collectClickTargets(
            offsetX = offsetX,
            offsetY = offsetY - state.scrollOffsetPx.toInt(),
            targets = collected,
        )
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds ->
                target.copy(bounds = bounds)
            }
        }
    }

    /**
     * 导出裁剪后的分页目标。
     */
    override fun collectPagerTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelPagerTarget>,
    ) {
        val collected = mutableListOf<PixelPagerTarget>()
        renderChild?.collectPagerTargets(
            offsetX = offsetX,
            offsetY = offsetY - state.scrollOffsetPx.toInt(),
            targets = collected,
        )
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds ->
                target.copy(bounds = bounds)
            }
        }
    }

    /**
     * 导出当前视口的列表滚动目标。
     */
    override fun collectListTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelListTarget>,
    ) {
        targets += PixelListTarget(
            bounds = globalBounds(offsetX, offsetY),
            viewportHeightPx = size.height,
            contentHeightPx = state.contentHeightPx,
            state = state,
            controller = controller,
        )
        val collected = mutableListOf<PixelListTarget>()
        renderChild?.collectListTargets(
            offsetX = offsetX,
            offsetY = offsetY - state.scrollOffsetPx.toInt(),
            targets = collected,
        )
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds ->
                target.copy(bounds = bounds)
            }
        }
    }

    /**
     * 导出裁剪后的文本输入目标。
     */
    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        val collected = mutableListOf<PixelTextInputTarget>()
        renderChild?.collectTextInputTargets(
            offsetX = offsetX,
            offsetY = offsetY - state.scrollOffsetPx.toInt(),
            targets = collected,
        )
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds ->
                target.copy(bounds = bounds)
            }
        }
    }

    /**
     * 读取当前可绘制的盒模型子节点。
     */
    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

/**
 * 新渲染管线里的垂直列表滚动视口。
 */
internal class RenderListViewport(
    children: List<RenderBox> = emptyList(),
    private var state: PixelListState,
    private var controller: PixelListController,
    private var spacing: Int = 0,
) : MultiChildRenderObject() {
    private val childOffsets = mutableListOf<Int>()

    init {
        setRenderObjectChildren(children)
    }

    /**
     * 同步列表视口配置。
     */
    fun updateListViewport(
        state: PixelListState,
        controller: PixelListController,
        spacing: Int,
    ) {
        this.state = state
        this.controller = controller
        this.spacing = spacing
        markNeedsLayout()
        markNeedsPaint()
    }

    /**
     * 替换列表子节点并维护偏移缓存。
     */
    override fun setRenderObjectChildren(children: List<RenderObject>) {
        super.setRenderObjectChildren(children)
        resizeChildOffsets(renderChildren.size)
    }

    /**
     * 按父约束布局所有列表项，并把内容高度同步给 controller。
     */
    override fun layout(constraints: RenderConstraints) {
        val children = renderChildren
        val childConstraints = RenderConstraints(
            maxWidth = constraints.maxWidth,
            maxHeight = constraints.maxHeight,
        )
        var cursorY = 0
        children.forEachIndexed { index, child ->
            childOffsets[index] = cursorY
            child.layout(childConstraints)
            cursorY += child.size.height
            if (index < children.lastIndex) {
                cursorY += spacing.coerceAtLeast(0)
            }
        }
        size = RenderSize(
            width = constraints.maxWidth,
            height = constraints.maxHeight,
        )
        controller.sync(
            state = state,
            viewportHeightPx = size.height,
            contentHeightPx = cursorY,
        )
        state.itemTopOffsetsPx = childOffsets.toIntArray()
        state.itemHeightsPx = children.map { child -> child.size.height }.toIntArray()
    }

    /**
     * 把滚动后的列表内容裁剪绘制到当前视口。
     */
    override fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        val scratch = PixelBuffer(
            width = size.width,
            height = size.height,
        )
        visibleRenderChildren().forEach { (index, child) ->
            child.paint(
                context = PaintContext(buffer = scratch),
                offsetX = 0,
                offsetY = childOffsets[index] - state.scrollOffsetPx.toInt(),
            )
        }
        context.buffer.blit(
            source = scratch,
            destX = offsetX,
            destY = offsetY,
            sourceY = 0,
            copyWidth = size.width,
            copyHeight = size.height,
        )
    }

    /**
     * 在滚动后的列表内容坐标中执行命中测试。
     */
    override fun hitTest(
        localX: Int,
        localY: Int,
        result: HitTestResult,
    ) {
        if (!viewportBounds().contains(localX, localY)) {
            return
        }
        val contentY = localY + state.scrollOffsetPx.toInt()
        visibleRenderChildren().forEach { (index, child) ->
            child.hitTest(
                localX = localX,
                localY = contentY - childOffsets[index],
                result = result,
            )
        }
    }

    /**
     * 导出裁剪后的列表项点击目标。
     */
    override fun collectClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ) {
        val collected = mutableListOf<PixelClickTarget>()
        visibleRenderChildren().forEach { (index, child) ->
            child.collectClickTargets(
                offsetX = offsetX,
                offsetY = offsetY + childOffsets[index] - state.scrollOffsetPx.toInt(),
                targets = collected,
            )
        }
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds ->
                target.copy(bounds = bounds)
            }
        }
    }

    /**
     * 导出裁剪后的列表项分页目标。
     */
    override fun collectPagerTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelPagerTarget>,
    ) {
        val collected = mutableListOf<PixelPagerTarget>()
        visibleRenderChildren().forEach { (index, child) ->
            child.collectPagerTargets(
                offsetX = offsetX,
                offsetY = offsetY + childOffsets[index] - state.scrollOffsetPx.toInt(),
                targets = collected,
            )
        }
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds ->
                target.copy(bounds = bounds)
            }
        }
    }

    /**
     * 导出当前列表视口的滚动目标。
     */
    override fun collectListTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelListTarget>,
    ) {
        targets += PixelListTarget(
            bounds = globalBounds(offsetX, offsetY),
            viewportHeightPx = size.height,
            contentHeightPx = state.contentHeightPx,
            state = state,
            controller = controller,
        )
        val collected = mutableListOf<PixelListTarget>()
        visibleRenderChildren().forEach { (index, child) ->
            child.collectListTargets(
                offsetX = offsetX,
                offsetY = offsetY + childOffsets[index] - state.scrollOffsetPx.toInt(),
                targets = collected,
            )
        }
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds ->
                target.copy(bounds = bounds)
            }
        }
    }

    /**
     * 导出裁剪后的列表项文本输入目标。
     */
    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        val collected = mutableListOf<PixelTextInputTarget>()
        visibleRenderChildren().forEach { (index, child) ->
            child.collectTextInputTargets(
                offsetX = offsetX,
                offsetY = offsetY + childOffsets[index] - state.scrollOffsetPx.toInt(),
                targets = collected,
            )
        }
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds ->
                target.copy(bounds = bounds)
            }
        }
    }

    /**
     * 读取当前列表里可布局的盒模型子节点。
     */
    private val renderChildren: List<RenderBox>
        get() = children.filterIsInstance<RenderBox>()

    private fun visibleRenderChildren(): List<Pair<Int, RenderBox>> {
        val viewportTop = state.scrollOffsetPx.toInt()
        val viewportBottom = viewportTop + size.height
        return renderChildren.mapIndexedNotNull { index, child ->
            val childTop = childOffsets[index]
            val childBottom = childTop + child.size.height
            if (childBottom <= viewportTop || childTop >= viewportBottom) {
                null
            } else {
                index to child
            }
        }
    }

    /**
     * 调整列表项偏移缓存长度。
     */
    private fun resizeChildOffsets(childCount: Int) {
        while (childOffsets.size < childCount) {
            childOffsets += 0
        }
        while (childOffsets.size > childCount) {
            childOffsets.removeAt(childOffsets.lastIndex)
        }
    }
}

/**
 * 固定 item 高度的 lazy 垂直列表视口。
 *
 * 只布局当前 build 阶段提供的可见窗口 children，但向 controller 同步完整列表高度。
 */
internal class RenderLazyListViewport(
    children: List<RenderBox> = emptyList(),
    private var firstItemIndex: Int,
    private var itemCount: Int,
    private var itemExtent: Int,
    private var state: PixelListState,
    private var controller: PixelListController,
    private var spacing: Int = 0,
) : MultiChildRenderObject() {
    init {
        setRenderObjectChildren(children)
    }

    fun updateLazyListViewport(
        firstItemIndex: Int,
        itemCount: Int,
        itemExtent: Int,
        state: PixelListState,
        controller: PixelListController,
        spacing: Int,
    ) {
        this.firstItemIndex = firstItemIndex
        this.itemCount = itemCount
        this.itemExtent = itemExtent
        this.state = state
        this.controller = controller
        this.spacing = spacing
        markNeedsLayout()
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        val safeItemExtent = itemExtent.coerceAtLeast(0)
        val safeSpacing = spacing.coerceAtLeast(0)
        val childConstraints = RenderConstraints(
            minWidth = 0,
            maxWidth = constraints.maxWidth,
            minHeight = safeItemExtent,
            maxHeight = safeItemExtent,
        )
        renderChildren.forEach { child ->
            child.layout(childConstraints)
        }
        size = RenderSize(
            width = constraints.maxWidth,
            height = constraints.maxHeight,
        )
        val contentHeight = contentHeightPx(
            itemCount = itemCount,
            itemExtent = safeItemExtent,
            spacing = safeSpacing,
        )
        controller.sync(
            state = state,
            viewportHeightPx = size.height,
            contentHeightPx = contentHeight,
        )
        state.itemTopOffsetsPx = IntArray(itemCount.coerceAtLeast(0)) { index ->
            itemTopPx(index)
        }
        state.itemHeightsPx = IntArray(itemCount.coerceAtLeast(0)) {
            safeItemExtent
        }
    }

    override fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        val scratch = PixelBuffer(
            width = size.width,
            height = size.height,
        )
        renderChildren.forEachIndexed { localIndex, child ->
            val itemIndex = firstItemIndex + localIndex
            child.paint(
                context = PaintContext(buffer = scratch),
                offsetX = 0,
                offsetY = itemTopPx(itemIndex) - state.scrollOffsetPx.toInt(),
            )
        }
        context.buffer.blit(
            source = scratch,
            destX = offsetX,
            destY = offsetY,
            copyWidth = size.width,
            copyHeight = size.height,
        )
    }

    override fun hitTest(
        localX: Int,
        localY: Int,
        result: HitTestResult,
    ) {
        if (!viewportBounds().contains(localX, localY)) {
            return
        }
        val contentY = localY + state.scrollOffsetPx.toInt()
        renderChildren.forEachIndexed { localIndex, child ->
            val itemIndex = firstItemIndex + localIndex
            child.hitTest(
                localX = localX,
                localY = contentY - itemTopPx(itemIndex),
                result = result,
            )
        }
    }

    override fun collectClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ) {
        val collected = mutableListOf<PixelClickTarget>()
        renderChildren.forEachIndexed { localIndex, child ->
            val itemIndex = firstItemIndex + localIndex
            child.collectClickTargets(
                offsetX = offsetX,
                offsetY = offsetY + itemTopPx(itemIndex) - state.scrollOffsetPx.toInt(),
                targets = collected,
            )
        }
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds ->
                target.copy(bounds = bounds)
            }
        }
    }

    override fun collectPagerTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelPagerTarget>,
    ) {
        val collected = mutableListOf<PixelPagerTarget>()
        renderChildren.forEachIndexed { localIndex, child ->
            val itemIndex = firstItemIndex + localIndex
            child.collectPagerTargets(
                offsetX = offsetX,
                offsetY = offsetY + itemTopPx(itemIndex) - state.scrollOffsetPx.toInt(),
                targets = collected,
            )
        }
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds ->
                target.copy(bounds = bounds)
            }
        }
    }

    override fun collectListTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelListTarget>,
    ) {
        targets += PixelListTarget(
            bounds = globalBounds(offsetX, offsetY),
            viewportHeightPx = size.height,
            contentHeightPx = state.contentHeightPx,
            state = state,
            controller = controller,
        )
        val collected = mutableListOf<PixelListTarget>()
        renderChildren.forEachIndexed { localIndex, child ->
            val itemIndex = firstItemIndex + localIndex
            child.collectListTargets(
                offsetX = offsetX,
                offsetY = offsetY + itemTopPx(itemIndex) - state.scrollOffsetPx.toInt(),
                targets = collected,
            )
        }
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds ->
                target.copy(bounds = bounds)
            }
        }
    }

    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        val collected = mutableListOf<PixelTextInputTarget>()
        renderChildren.forEachIndexed { localIndex, child ->
            val itemIndex = firstItemIndex + localIndex
            child.collectTextInputTargets(
                offsetX = offsetX,
                offsetY = offsetY + itemTopPx(itemIndex) - state.scrollOffsetPx.toInt(),
                targets = collected,
            )
        }
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds ->
                target.copy(bounds = bounds)
            }
        }
    }

    private fun itemTopPx(index: Int): Int {
        return index * (itemExtent.coerceAtLeast(0) + spacing.coerceAtLeast(0))
    }

    private val renderChildren: List<RenderBox>
        get() = children.filterIsInstance<RenderBox>()
}

/**
 * 生成当前 render object 的局部视口矩形。
 */
private fun RenderBox.viewportBounds(): PixelRect {
    return PixelRect(left = 0, top = 0, width = size.width, height = size.height)
}

/**
 * 生成当前 render object 的全局视口矩形。
 */
private fun RenderBox.globalBounds(
    offsetX: Int,
    offsetY: Int,
): PixelRect {
    return PixelRect(left = offsetX, top = offsetY, width = size.width, height = size.height)
}

/**
 * 给单子节点滚动内容一个足够大的自然高度上限，避免被视口高度过早截断。
 */
private fun safeScrollableMaxHeight(viewportHeight: Int): Int {
    return (viewportHeight.coerceAtLeast(1) * 64).coerceAtLeast(viewportHeight)
}

private fun contentHeightPx(
    itemCount: Int,
    itemExtent: Int,
    spacing: Int,
): Int {
    if (itemCount <= 0 || itemExtent <= 0) {
        return 0
    }
    return (itemCount * itemExtent) + ((itemCount - 1).coerceAtLeast(0) * spacing.coerceAtLeast(0))
}
