package com.purride.pixelui.internal

import com.purride.pixelui.internal.legacy.PixelAlignment

/**
 * 新渲染管线里的最小叠放布局对象。
 */
internal class RenderStack(
    children: List<RenderBox> = emptyList(),
    private var alignment: PixelAlignment = PixelAlignment.TOP_START,
) : MultiChildRenderObject() {
    init {
        setRenderObjectChildren(children)
    }

    /**
     * 更新 stack 对齐配置。
     */
    fun updateStack(alignment: PixelAlignment) {
        this.alignment = alignment
        markNeedsLayout()
        markNeedsPaint()
    }

    /**
     * 按父级约束布局所有叠放子节点。
     */
    override fun layout(constraints: RenderConstraints) {
        size = RenderSize(
            width = constraints.maxWidth,
            height = constraints.maxHeight,
        )
        renderChildren.forEach { child ->
            child.layout(
                RenderConstraints(
                    maxWidth = size.width,
                    maxHeight = size.height,
                ),
            )
        }
    }

    /**
     * 绘制所有叠放子节点。
     */
    override fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        renderChildren.forEach { child ->
            val childOffset = resolveChildOffset(child)
            child.paint(
                context = context,
                offsetX = offsetX + childOffset.x,
                offsetY = offsetY + childOffset.y,
            )
        }
    }

    /**
     * 执行 stack 子树命中测试。
     */
    override fun hitTest(
        localX: Int,
        localY: Int,
        result: HitTestResult,
    ) {
        if (localX !in 0 until size.width || localY !in 0 until size.height) {
            return
        }
        renderChildren.forEach { child ->
            val childOffset = resolveChildOffset(child)
            child.hitTest(
                localX = localX - childOffset.x,
                localY = localY - childOffset.y,
                result = result,
            )
        }
    }

    /**
     * 导出 stack 子树点击目标。
     */
    override fun collectClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ) {
        renderChildren.forEach { child ->
            val childOffset = resolveChildOffset(child)
            child.collectClickTargets(
                offsetX = offsetX + childOffset.x,
                offsetY = offsetY + childOffset.y,
                targets = targets,
            )
        }
    }

    /**
     * 导出 stack 子树分页目标。
     */
    override fun collectPagerTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelPagerTarget>,
    ) {
        renderChildren.forEach { child ->
            val childOffset = resolveChildOffset(child)
            child.collectPagerTargets(
                offsetX = offsetX + childOffset.x,
                offsetY = offsetY + childOffset.y,
                targets = targets,
            )
        }
    }

    /**
     * 导出 stack 子树列表滚动目标。
     */
    override fun collectListTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelListTarget>,
    ) {
        renderChildren.forEach { child ->
            val childOffset = resolveChildOffset(child)
            child.collectListTargets(
                offsetX = offsetX + childOffset.x,
                offsetY = offsetY + childOffset.y,
                targets = targets,
            )
        }
    }

    /**
     * 导出 stack 子树文本输入目标。
     */
    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        renderChildren.forEach { child ->
            val childOffset = resolveChildOffset(child)
            child.collectTextInputTargets(
                offsetX = offsetX + childOffset.x,
                offsetY = offsetY + childOffset.y,
                targets = targets,
            )
        }
    }

    /**
     * 解析子节点在 stack 内的偏移。
     */
    private fun resolveChildOffset(child: RenderBox): StackChildOffset {
        if (child is RenderPositioned) {
            return StackChildOffset()
        }
        val freeWidth = (size.width - child.size.width).coerceAtLeast(0)
        val freeHeight = (size.height - child.size.height).coerceAtLeast(0)
        return StackChildOffset(
            x = when (alignment) {
                PixelAlignment.TOP_CENTER,
                PixelAlignment.CENTER,
                PixelAlignment.BOTTOM_CENTER,
                -> freeWidth / 2
                PixelAlignment.TOP_END,
                PixelAlignment.CENTER_END,
                PixelAlignment.BOTTOM_END,
                -> freeWidth
                else -> 0
            },
            y = when (alignment) {
                PixelAlignment.CENTER_START,
                PixelAlignment.CENTER,
                PixelAlignment.CENTER_END,
                -> freeHeight / 2
                PixelAlignment.BOTTOM_START,
                PixelAlignment.BOTTOM_CENTER,
                PixelAlignment.BOTTOM_END,
                -> freeHeight
                else -> 0
            },
        )
    }

    /**
     * 读取当前 stack 可布局的盒模型子节点。
     */
    private val renderChildren: List<RenderBox>
        get() = children.filterIsInstance<RenderBox>()
}

/**
 * 记录 stack 子节点偏移。
 */
private data class StackChildOffset(
    val x: Int = 0,
    val y: Int = 0,
)

/**
 * `Positioned` 对应的透明定位 render object。
 */
internal class RenderPositioned(
    child: RenderBox? = null,
    private var left: Int? = null,
    private var top: Int? = null,
    private var right: Int? = null,
    private var bottom: Int? = null,
    private var width: Int? = null,
    private var height: Int? = null,
) : SingleChildRenderObject() {
    private var childOffsetX = 0
    private var childOffsetY = 0

    init {
        setRenderObjectChild(child)
    }

    /**
     * 更新定位配置。
     */
    fun updatePosition(
        left: Int?,
        top: Int?,
        right: Int?,
        bottom: Int?,
        width: Int?,
        height: Int?,
    ) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
        this.width = width
        this.height = height
        markNeedsLayout()
        markNeedsPaint()
    }

    /**
     * 按父 stack 约束布局定位子节点。
     */
    override fun layout(constraints: RenderConstraints) {
        size = RenderSize(
            width = constraints.maxWidth,
            height = constraints.maxHeight,
        )
        val childWidth = width ?: resolveStretchExtent(
            max = size.width,
            leading = left,
            trailing = right,
        )
        val childHeight = height ?: resolveStretchExtent(
            max = size.height,
            leading = top,
            trailing = bottom,
        )
        val childConstraints = RenderConstraints(
            minWidth = childWidth ?: 0,
            maxWidth = childWidth ?: size.width,
            minHeight = childHeight ?: 0,
            maxHeight = childHeight ?: size.height,
        )
        renderChild?.layout(childConstraints)
        val measuredChild = renderChild?.size ?: RenderSize.Zero
        childOffsetX = left ?: right?.let { size.width - it - measuredChild.width } ?: 0
        childOffsetY = top ?: bottom?.let { size.height - it - measuredChild.height } ?: 0
    }

    /**
     * 绘制定位子节点。
     */
    override fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        renderChild?.paint(
            context = context,
            offsetX = offsetX + childOffsetX,
            offsetY = offsetY + childOffsetY,
        )
    }

    /**
     * 执行定位子节点命中测试。
     */
    override fun hitTest(
        localX: Int,
        localY: Int,
        result: HitTestResult,
    ) {
        renderChild?.hitTest(
            localX = localX - childOffsetX,
            localY = localY - childOffsetY,
            result = result,
        )
    }

    /**
     * 导出定位子节点点击目标。
     */
    override fun collectClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ) {
        renderChild?.collectClickTargets(
            offsetX = offsetX + childOffsetX,
            offsetY = offsetY + childOffsetY,
            targets = targets,
        )
    }

    /**
     * 导出定位子节点分页目标。
     */
    override fun collectPagerTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelPagerTarget>,
    ) {
        renderChild?.collectPagerTargets(
            offsetX = offsetX + childOffsetX,
            offsetY = offsetY + childOffsetY,
            targets = targets,
        )
    }

    /**
     * 导出定位子节点列表滚动目标。
     */
    override fun collectListTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelListTarget>,
    ) {
        renderChild?.collectListTargets(
            offsetX = offsetX + childOffsetX,
            offsetY = offsetY + childOffsetY,
            targets = targets,
        )
    }

    /**
     * 导出定位子节点文本输入目标。
     */
    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        renderChild?.collectTextInputTargets(
            offsetX = offsetX + childOffsetX,
            offsetY = offsetY + childOffsetY,
            targets = targets,
        )
    }

    /**
     * 根据双边定位解析拉伸尺寸。
     */
    private fun resolveStretchExtent(
        max: Int,
        leading: Int?,
        trailing: Int?,
    ): Int? {
        return if (leading != null && trailing != null) {
            (max - leading - trailing).coerceAtLeast(0)
        } else {
            null
        }
    }

    /**
     * 读取唯一盒模型子节点。
     */
    private val renderChild: RenderBox?
        get() = child as? RenderBox
}
