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
            height = state.contentHeightPx.coerceAtLeast(size.height),
        )
        renderChildren.forEachIndexed { index, child ->
            child.paint(
                context = PaintContext(buffer = scratch),
                offsetX = 0,
                offsetY = childOffsets[index],
            )
        }
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
        renderChildren.forEachIndexed { index, child ->
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
        renderChildren.forEachIndexed { index, child ->
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
        renderChildren.forEachIndexed { index, child ->
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
        renderChildren.forEachIndexed { index, child ->
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
        renderChildren.forEachIndexed { index, child ->
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
