package com.purride.pixelui.internal

import com.purride.pixelcore.AxisBufferComposer
import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerSnapshot
import com.purride.pixelui.state.PixelPagerState

/**
 * 新渲染管线里的分页视口。
 */
internal class RenderPagerViewport(
    children: List<RenderBox> = emptyList(),
    private var axis: PixelAxis,
    private var state: PixelPagerState,
    private var controller: PixelPagerController,
    private var onPageChanged: ((Int) -> Unit)?,
) : MultiChildRenderObject() {
    init {
        setRenderObjectChildren(children)
    }

    /**
     * 同步分页视口配置。
     */
    fun updatePagerViewport(
        axis: PixelAxis,
        state: PixelPagerState,
        controller: PixelPagerController,
        onPageChanged: ((Int) -> Unit)?,
    ) {
        this.axis = axis
        this.state = state
        this.controller = controller
        this.onPageChanged = onPageChanged
        markNeedsLayout()
        markNeedsPaint()
    }

    /**
     * 按父约束把每一页布局为完整视口尺寸。
     */
    override fun layout(constraints: RenderConstraints) {
        size = RenderSize(
            width = constraints.maxWidth,
            height = constraints.maxHeight,
        )
        controller.sync(
            state = state,
            axis = axis,
            pageCount = renderChildren.size.coerceAtLeast(1),
        )
        val pageConstraints = RenderConstraints(
            minWidth = size.width,
            maxWidth = size.width,
            minHeight = size.height,
            maxHeight = size.height,
        )
        renderChildren.forEach { child ->
            child.layout(pageConstraints)
        }
    }

    /**
     * 按当前分页快照绘制锚点页和相邻页。
     */
    override fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        val snapshot = controller.snapshot(state)
        val primary = renderPage(snapshot.anchorPage) ?: return
        val secondary = snapshot.adjacentPage?.let(::renderPage)
        val composed = AxisBufferComposer.compose(
            primary = primary,
            secondary = secondary,
            axis = snapshot.axis,
            offsetPx = snapshot.dragOffsetPx,
        )
        context.buffer.blit(
            source = composed,
            destX = offsetX,
            destY = offsetY,
        )
    }

    /**
     * 在当前分页快照的可视页中执行命中测试。
     */
    override fun hitTest(
        localX: Int,
        localY: Int,
        result: HitTestResult,
    ) {
        if (!viewportBounds().contains(localX, localY)) {
            return
        }
        val snapshot = controller.snapshot(state)
        renderChildren.getOrNull(snapshot.anchorPage)?.hitTest(
            localX = localX - anchorShiftX(snapshot),
            localY = localY - anchorShiftY(snapshot),
            result = result,
        )
        snapshot.adjacentPage?.let { page ->
            renderChildren.getOrNull(page)?.hitTest(
                localX = localX - adjacentShiftX(snapshot),
                localY = localY - adjacentShiftY(snapshot),
                result = result,
            )
        }
    }

    /**
     * 导出裁剪后的分页页内点击目标。
     */
    override fun collectClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ) {
        val collected = mutableListOf<PixelClickTarget>()
        collectVisiblePageTargets(
            offsetX = offsetX,
            offsetY = offsetY,
            collect = { child, childOffsetX, childOffsetY ->
                child.collectClickTargets(childOffsetX, childOffsetY, collected)
            },
        )
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds ->
                target.copy(bounds = bounds)
            }
        }
    }

    /**
     * 导出当前分页视口和页内嵌套分页目标。
     */
    override fun collectPagerTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelPagerTarget>,
    ) {
        targets += PixelPagerTarget(
            bounds = globalBounds(offsetX, offsetY),
            axis = axis,
            state = state,
            controller = controller,
            onPageChanged = onPageChanged,
        )
        val collected = mutableListOf<PixelPagerTarget>()
        collectVisiblePageTargets(
            offsetX = offsetX,
            offsetY = offsetY,
            collect = { child, childOffsetX, childOffsetY ->
                child.collectPagerTargets(childOffsetX, childOffsetY, collected)
            },
        )
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds ->
                target.copy(bounds = bounds)
            }
        }
    }

    /**
     * 导出裁剪后的页内列表滚动目标。
     */
    override fun collectListTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelListTarget>,
    ) {
        val collected = mutableListOf<PixelListTarget>()
        collectVisiblePageTargets(
            offsetX = offsetX,
            offsetY = offsetY,
            collect = { child, childOffsetX, childOffsetY ->
                child.collectListTargets(childOffsetX, childOffsetY, collected)
            },
        )
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds ->
                target.copy(bounds = bounds)
            }
        }
    }

    /**
     * 导出裁剪后的页内文本输入目标。
     */
    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        val collected = mutableListOf<PixelTextInputTarget>()
        collectVisiblePageTargets(
            offsetX = offsetX,
            offsetY = offsetY,
            collect = { child, childOffsetX, childOffsetY ->
                child.collectTextInputTargets(childOffsetX, childOffsetY, collected)
            },
        )
        collected.mapNotNullTo(targets) { target ->
            target.bounds.intersect(globalBounds(offsetX, offsetY))?.let { bounds ->
                target.copy(bounds = bounds)
            }
        }
    }

    /**
     * 渲染指定页到独立缓冲。
     */
    private fun renderPage(pageIndex: Int): PixelBuffer? {
        val page = renderChildren.getOrNull(pageIndex) ?: return null
        val pageBuffer = PixelBuffer(width = size.width, height = size.height)
        page.paint(
            context = PaintContext(buffer = pageBuffer),
            offsetX = 0,
            offsetY = 0,
        )
        return pageBuffer
    }

    /**
     * 对当前可视页执行目标收集。
     */
    private fun collectVisiblePageTargets(
        offsetX: Int,
        offsetY: Int,
        collect: (RenderBox, Int, Int) -> Unit,
    ) {
        val snapshot = controller.snapshot(state)
        renderChildren.getOrNull(snapshot.anchorPage)?.let { child ->
            collect(child, offsetX + anchorShiftX(snapshot), offsetY + anchorShiftY(snapshot))
        }
        snapshot.adjacentPage?.let { pageIndex ->
            renderChildren.getOrNull(pageIndex)?.let { child ->
                collect(child, offsetX + adjacentShiftX(snapshot), offsetY + adjacentShiftY(snapshot))
            }
        }
    }

    /**
     * 计算锚点页水平偏移。
     */
    private fun anchorShiftX(snapshot: PixelPagerSnapshot): Int {
        return if (snapshot.axis == PixelAxis.HORIZONTAL) snapshot.dragOffsetPx.toInt() else 0
    }

    /**
     * 计算锚点页垂直偏移。
     */
    private fun anchorShiftY(snapshot: PixelPagerSnapshot): Int {
        return if (snapshot.axis == PixelAxis.VERTICAL) snapshot.dragOffsetPx.toInt() else 0
    }

    /**
     * 计算相邻页水平偏移。
     */
    private fun adjacentShiftX(snapshot: PixelPagerSnapshot): Int {
        val anchorShiftX = anchorShiftX(snapshot)
        return when (snapshot.axis) {
            PixelAxis.HORIZONTAL -> if (snapshot.dragOffsetPx > 0f) anchorShiftX - size.width else anchorShiftX + size.width
            PixelAxis.VERTICAL -> 0
        }
    }

    /**
     * 计算相邻页垂直偏移。
     */
    private fun adjacentShiftY(snapshot: PixelPagerSnapshot): Int {
        val anchorShiftY = anchorShiftY(snapshot)
        return when (snapshot.axis) {
            PixelAxis.HORIZONTAL -> 0
            PixelAxis.VERTICAL -> if (snapshot.dragOffsetPx > 0f) anchorShiftY - size.height else anchorShiftY + size.height
        }
    }

    /**
     * 读取当前分页视口可布局的盒模型页。
     */
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
