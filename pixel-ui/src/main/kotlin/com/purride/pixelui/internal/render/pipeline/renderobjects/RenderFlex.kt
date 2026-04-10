package com.purride.pixelui.internal

import com.purride.pixelui.internal.legacy.PixelCrossAxisAlignment
import com.purride.pixelui.internal.legacy.PixelMainAxisAlignment
import com.purride.pixelui.internal.legacy.PixelMainAxisSize

/**
 * 新渲染管线里的最小弹性布局对象。
 *
 * 当前覆盖 `Row / Column` 的基础主轴排布、交叉轴对齐、spacing 和基础权重分配，
 * 直接服务 retained render object tree，不再依赖 bridge lowering。
 */
internal class RenderFlex(
    private var direction: FlexDirection,
    children: List<RenderBox>,
    private var spacing: Int = 0,
    private var mainAxisSize: PixelMainAxisSize = PixelMainAxisSize.MIN,
    private var mainAxisAlignment: PixelMainAxisAlignment = PixelMainAxisAlignment.START,
    private var crossAxisAlignment: PixelCrossAxisAlignment = PixelCrossAxisAlignment.START,
    private var explicitWidth: Int? = null,
    private var explicitHeight: Int? = null,
    private var fillMaxWidth: Boolean = false,
    private var fillMaxHeight: Boolean = false,
    private var paddingLeft: Int = 0,
    private var paddingTop: Int = 0,
    private var paddingRight: Int = 0,
    private var paddingBottom: Int = 0,
    private var onClick: (() -> Unit)? = null,
) : MultiChildRenderObject() {
    private val childOffsets = mutableListOf<ChildOffset>()

    init {
        setRenderObjectChildren(children)
    }

    /**
     * 更新 flex 布局配置，并触发布局与绘制刷新。
     */
    fun updateFlex(
        direction: FlexDirection,
        spacing: Int = 0,
        mainAxisSize: PixelMainAxisSize = PixelMainAxisSize.MIN,
        mainAxisAlignment: PixelMainAxisAlignment = PixelMainAxisAlignment.START,
        crossAxisAlignment: PixelCrossAxisAlignment = PixelCrossAxisAlignment.START,
        explicitWidth: Int? = null,
        explicitHeight: Int? = null,
        fillMaxWidth: Boolean = false,
        fillMaxHeight: Boolean = false,
        paddingLeft: Int = 0,
        paddingTop: Int = 0,
        paddingRight: Int = 0,
        paddingBottom: Int = 0,
        onClick: (() -> Unit)? = null,
    ) {
        this.direction = direction
        this.spacing = spacing
        this.mainAxisSize = mainAxisSize
        this.mainAxisAlignment = mainAxisAlignment
        this.crossAxisAlignment = crossAxisAlignment
        this.explicitWidth = explicitWidth
        this.explicitHeight = explicitHeight
        this.fillMaxWidth = fillMaxWidth
        this.fillMaxHeight = fillMaxHeight
        this.paddingLeft = paddingLeft
        this.paddingTop = paddingTop
        this.paddingRight = paddingRight
        this.paddingBottom = paddingBottom
        this.onClick = onClick
        markNeedsLayout()
        markNeedsPaint()
    }

    /**
     * 替换所有 flex 子节点，并同步偏移缓存长度。
     */
    override fun setRenderObjectChildren(children: List<RenderObject>) {
        super.setRenderObjectChildren(children)
        resizeChildOffsets(renderChildren.size)
    }

    /**
     * 在给定约束下完成最小 flex 布局。
     */
    override fun layout(constraints: RenderConstraints) {
        val children = renderChildren
        val innerConstraints = constraints.inset(
            left = paddingLeft,
            top = paddingTop,
            right = paddingRight,
            bottom = paddingBottom,
        )
        val childConstraints = when (direction) {
            FlexDirection.HORIZONTAL -> RenderConstraints(
                maxWidth = innerConstraints.maxWidth,
                minHeight = if (crossAxisAlignment == PixelCrossAxisAlignment.STRETCH) {
                    innerConstraints.maxHeight
                } else {
                    0
                },
                maxHeight = innerConstraints.maxHeight,
            )

            FlexDirection.VERTICAL -> RenderConstraints(
                minWidth = if (crossAxisAlignment == PixelCrossAxisAlignment.STRETCH) {
                    innerConstraints.maxWidth
                } else {
                    0
                },
                maxWidth = innerConstraints.maxWidth,
                maxHeight = innerConstraints.maxHeight,
            )
        }

        val weightedChildren = children.filterIsInstance<RenderFlexChild>()
        val fixedChildren = children.filterNot { child -> child is RenderFlexChild }
        fixedChildren.forEach { child ->
            child.layout(childConstraints)
        }

        val fixedChildrenMainExtent = fixedChildren.sumOf(::mainExtentOf)
        val totalSpacing = (spacing * (children.size - 1).coerceAtLeast(0)).coerceAtLeast(0)
        val availableMainForWeights = when (direction) {
            FlexDirection.HORIZONTAL -> innerConstraints.maxWidth
            FlexDirection.VERTICAL -> innerConstraints.maxHeight
        }
        val remainingForWeights = (availableMainForWeights - fixedChildrenMainExtent - totalSpacing).coerceAtLeast(0)
        val totalFlex = weightedChildren.sumOf { child -> child.flex.coerceAtLeast(1) }
        weightedChildren.forEach { child ->
            val allocatedMainExtent = if (totalFlex == 0) {
                0
            } else {
                (remainingForWeights * child.flex.coerceAtLeast(1)) / totalFlex
            }
            child.layout(
                constraints = createWeightedChildConstraints(
                    base = childConstraints,
                    allocatedMainExtent = allocatedMainExtent,
                    fit = child.fit,
                ),
            )
        }

        val childrenMainExtent = children.sumOf(::mainExtentOf)
        val contentMainExtent = childrenMainExtent + totalSpacing
        val contentCrossExtent = children.maxOfOrNull(::crossExtentOf) ?: 0

        val measuredWidth = when (direction) {
            FlexDirection.HORIZONTAL -> resolveMeasuredMainOrCross(
                explicit = explicitWidth,
                fillMax = fillMaxWidth || mainAxisSize == PixelMainAxisSize.MAX,
                fallback = contentMainExtent + paddingLeft + paddingRight,
                constraints = constraints,
                horizontal = true,
            )

            FlexDirection.VERTICAL -> resolveMeasuredMainOrCross(
                explicit = explicitWidth,
                fillMax = fillMaxWidth,
                fallback = contentCrossExtent + paddingLeft + paddingRight,
                constraints = constraints,
                horizontal = true,
            )
        }
        val measuredHeight = when (direction) {
            FlexDirection.HORIZONTAL -> resolveMeasuredMainOrCross(
                explicit = explicitHeight,
                fillMax = fillMaxHeight,
                fallback = contentCrossExtent + paddingTop + paddingBottom,
                constraints = constraints,
                horizontal = false,
            )

            FlexDirection.VERTICAL -> resolveMeasuredMainOrCross(
                explicit = explicitHeight,
                fillMax = fillMaxHeight || mainAxisSize == PixelMainAxisSize.MAX,
                fallback = contentMainExtent + paddingTop + paddingBottom,
                constraints = constraints,
                horizontal = false,
            )
        }

        size = RenderSize(
            width = measuredWidth,
            height = measuredHeight,
        )

        val availableMainExtent = when (direction) {
            FlexDirection.HORIZONTAL -> (size.width - paddingLeft - paddingRight).coerceAtLeast(0)
            FlexDirection.VERTICAL -> (size.height - paddingTop - paddingBottom).coerceAtLeast(0)
        }
        val availableCrossExtent = when (direction) {
            FlexDirection.HORIZONTAL -> (size.height - paddingTop - paddingBottom).coerceAtLeast(0)
            FlexDirection.VERTICAL -> (size.width - paddingLeft - paddingRight).coerceAtLeast(0)
        }

        val mainAxisArrangement = resolveMainAxisArrangement(
            availableMainExtent = availableMainExtent,
            childrenMainExtent = childrenMainExtent,
            baseSpacing = spacing,
            childCount = children.size,
        )

        var cursor = mainAxisArrangement.start
        children.forEachIndexed { index, child ->
            val crossOffset = resolveCrossOffset(
                availableCrossExtent = availableCrossExtent,
                childCrossExtent = crossExtentOf(child),
            )
            childOffsets[index] = when (direction) {
                FlexDirection.HORIZONTAL -> ChildOffset(
                    x = paddingLeft + cursor,
                    y = paddingTop + crossOffset,
                )

                FlexDirection.VERTICAL -> ChildOffset(
                    x = paddingLeft + crossOffset,
                    y = paddingTop + cursor,
                )
            }
            cursor += mainExtentOf(child) + mainAxisArrangement.spacingAfterChild
        }
    }

    /**
     * 把当前 flex 子树绘制到目标 buffer。
     */
    override fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        val children = renderChildren
        children.forEachIndexed { index, child ->
            val childOffset = childOffsets[index]
            child.paint(
                context = context,
                offsetX = offsetX + childOffset.x,
                offsetY = offsetY + childOffset.y,
            )
        }
    }

    /**
     * 执行当前 flex 子树的命中测试。
     */
    override fun hitTest(
        localX: Int,
        localY: Int,
        result: HitTestResult,
    ) {
        if (localX !in 0 until size.width || localY !in 0 until size.height) {
            return
        }
        val children = renderChildren
        children.forEachIndexed { index, child ->
            val childOffset = childOffsets[index]
            child.hitTest(
                localX = localX - childOffset.x,
                localY = localY - childOffset.y,
                result = result,
            )
        }
        if (onClick != null) {
            result.add(this)
        }
    }

    /**
     * 导出当前 flex 子树里的点击目标。
     */
    override fun collectClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ) {
        onClick?.let { clickHandler ->
            targets += PixelClickTarget(
                bounds = PixelRect(
                    left = offsetX,
                    top = offsetY,
                    width = size.width,
                    height = size.height,
                ),
                onClick = clickHandler,
            )
        }
        val children = renderChildren
        children.forEachIndexed { index, child ->
            val childOffset = childOffsets[index]
            child.collectClickTargets(
                offsetX = offsetX + childOffset.x,
                offsetY = offsetY + childOffset.y,
                targets = targets,
            )
        }
    }

    /**
     * 导出当前 flex 子树里的分页目标。
     */
    override fun collectPagerTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelPagerTarget>,
    ) {
        val children = renderChildren
        children.forEachIndexed { index, child ->
            val childOffset = childOffsets[index]
            child.collectPagerTargets(
                offsetX = offsetX + childOffset.x,
                offsetY = offsetY + childOffset.y,
                targets = targets,
            )
        }
    }

    /**
     * 导出当前 flex 子树里的列表滚动目标。
     */
    override fun collectListTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelListTarget>,
    ) {
        val children = renderChildren
        children.forEachIndexed { index, child ->
            val childOffset = childOffsets[index]
            child.collectListTargets(
                offsetX = offsetX + childOffset.x,
                offsetY = offsetY + childOffset.y,
                targets = targets,
            )
        }
    }

    /**
     * 导出当前 flex 子树里的文本输入目标。
     */
    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        val children = renderChildren
        children.forEachIndexed { index, child ->
            val childOffset = childOffsets[index]
            child.collectTextInputTargets(
                offsetX = offsetX + childOffset.x,
                offsetY = offsetY + childOffset.y,
                targets = targets,
            )
        }
    }

    /**
     * 解析当前主轴尺寸对应的输出宽高。
     */
    private fun resolveMeasuredMainOrCross(
        explicit: Int?,
        fillMax: Boolean,
        fallback: Int,
        constraints: RenderConstraints,
        horizontal: Boolean,
    ): Int {
        return when {
            explicit != null -> if (horizontal) constraints.constrainWidth(explicit) else constraints.constrainHeight(explicit)
            fillMax -> if (horizontal) constraints.maxWidth else constraints.maxHeight
            horizontal -> constraints.constrainWidth(fallback)
            else -> constraints.constrainHeight(fallback)
        }
    }

    /**
     * 解析主轴起始偏移。
     */
    private fun resolveMainAxisArrangement(
        availableMainExtent: Int,
        childrenMainExtent: Int,
        baseSpacing: Int,
        childCount: Int,
    ): FlexMainAxisArrangement {
        val contentExtent = childrenMainExtent + (baseSpacing * (childCount - 1).coerceAtLeast(0))
        val remaining = (availableMainExtent - contentExtent).coerceAtLeast(0)
        return when (mainAxisAlignment) {
            PixelMainAxisAlignment.START -> FlexMainAxisArrangement(start = 0, spacingAfterChild = baseSpacing)
            PixelMainAxisAlignment.CENTER -> FlexMainAxisArrangement(start = remaining / 2, spacingAfterChild = baseSpacing)
            PixelMainAxisAlignment.END -> FlexMainAxisArrangement(start = remaining, spacingAfterChild = baseSpacing)
            PixelMainAxisAlignment.SPACE_BETWEEN -> {
                if (childCount <= 1) {
                    FlexMainAxisArrangement(start = 0, spacingAfterChild = baseSpacing)
                } else {
                    FlexMainAxisArrangement(
                        start = 0,
                        spacingAfterChild = baseSpacing + (remaining / (childCount - 1)),
                    )
                }
            }

            PixelMainAxisAlignment.SPACE_AROUND -> {
                if (childCount <= 0) {
                    FlexMainAxisArrangement(start = 0, spacingAfterChild = baseSpacing)
                } else {
                    val unit = remaining / childCount
                    FlexMainAxisArrangement(
                        start = unit / 2,
                        spacingAfterChild = baseSpacing + unit,
                    )
                }
            }

            PixelMainAxisAlignment.SPACE_EVENLY -> {
                val slotCount = childCount + 1
                val unit = if (slotCount <= 0) 0 else remaining / slotCount
                FlexMainAxisArrangement(
                    start = unit,
                    spacingAfterChild = baseSpacing + unit,
                )
            }
        }
    }

    /**
     * 解析交叉轴偏移。
     */
    private fun resolveCrossOffset(
        availableCrossExtent: Int,
        childCrossExtent: Int,
    ): Int {
        val freeCrossExtent = (availableCrossExtent - childCrossExtent).coerceAtLeast(0)
        return when (crossAxisAlignment) {
            PixelCrossAxisAlignment.START,
            PixelCrossAxisAlignment.STRETCH,
            -> 0

            PixelCrossAxisAlignment.CENTER -> freeCrossExtent / 2
            PixelCrossAxisAlignment.END -> freeCrossExtent
        }
    }

    /**
     * 读取子节点的主轴尺寸。
     */
    private fun mainExtentOf(child: RenderBox): Int {
        return when (direction) {
            FlexDirection.HORIZONTAL -> child.size.width
            FlexDirection.VERTICAL -> child.size.height
        }
    }

    /**
     * 读取子节点的交叉轴尺寸。
     */
    private fun crossExtentOf(child: RenderBox): Int {
        return when (direction) {
            FlexDirection.HORIZONTAL -> child.size.height
            FlexDirection.VERTICAL -> child.size.width
        }
    }

    /**
     * 为带 flex parent data 的子节点创建主轴约束。
     */
    private fun createWeightedChildConstraints(
        base: RenderConstraints,
        allocatedMainExtent: Int,
        fit: com.purride.pixelui.FlexFit,
    ): RenderConstraints {
        return when (direction) {
            FlexDirection.HORIZONTAL -> RenderConstraints(
                minWidth = if (fit == com.purride.pixelui.FlexFit.TIGHT) allocatedMainExtent else 0,
                maxWidth = allocatedMainExtent,
                minHeight = base.minHeight,
                maxHeight = base.maxHeight,
            )

            FlexDirection.VERTICAL -> RenderConstraints(
                minWidth = base.minWidth,
                maxWidth = base.maxWidth,
                minHeight = if (fit == com.purride.pixelui.FlexFit.TIGHT) allocatedMainExtent else 0,
                maxHeight = allocatedMainExtent,
            )
        }
    }

    /**
     * 读取当前 flex 可布局的盒模型子节点。
     */
    private val renderChildren: List<RenderBox>
        get() = children.filterIsInstance<RenderBox>()

    /**
     * 调整子节点偏移缓存长度。
     */
    private fun resizeChildOffsets(childCount: Int) {
        while (childOffsets.size < childCount) {
            childOffsets += ChildOffset()
        }
        while (childOffsets.size > childCount) {
            childOffsets.removeAt(childOffsets.lastIndex)
        }
    }
}

/**
 * 承接 `Expanded / Flexible` parent data 的透明 flex child render object。
 */
internal class RenderFlexChild(
    child: RenderBox? = null,
    var flex: Int,
    var fit: com.purride.pixelui.FlexFit,
) : SingleChildRenderObject() {
    init {
        setRenderObjectChild(child)
    }

    /**
     * 更新 flex parent data。
     */
    fun updateFlexData(
        flex: Int,
        fit: com.purride.pixelui.FlexFit,
    ) {
        this.flex = flex
        this.fit = fit
        markNeedsLayout()
        markNeedsPaint()
    }

    /**
     * 使用父级分配的约束布局唯一子节点。
     */
    override fun layout(constraints: RenderConstraints) {
        val renderChild = child as? RenderBox
        renderChild?.layout(constraints)
        size = RenderSize(
            width = renderChild?.size?.width ?: constraints.minWidth,
            height = renderChild?.size?.height ?: constraints.minHeight,
        )
    }

    /**
     * 绘制唯一子节点。
     */
    override fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        (child as? RenderBox)?.paint(
            context = context,
            offsetX = offsetX,
            offsetY = offsetY,
        )
    }

    /**
     * 执行唯一子节点命中测试。
     */
    override fun hitTest(
        localX: Int,
        localY: Int,
        result: HitTestResult,
    ) {
        (child as? RenderBox)?.hitTest(localX, localY, result)
    }

    /**
     * 导出唯一子节点点击目标。
     */
    override fun collectClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ) {
        (child as? RenderBox)?.collectClickTargets(offsetX, offsetY, targets)
    }

    /**
     * 导出唯一子节点分页目标。
     */
    override fun collectPagerTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelPagerTarget>,
    ) {
        (child as? RenderBox)?.collectPagerTargets(offsetX, offsetY, targets)
    }

    /**
     * 导出唯一子节点列表滚动目标。
     */
    override fun collectListTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelListTarget>,
    ) {
        (child as? RenderBox)?.collectListTargets(offsetX, offsetY, targets)
    }

    /**
     * 导出唯一子节点文本输入目标。
     */
    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        (child as? RenderBox)?.collectTextInputTargets(offsetX, offsetY, targets)
    }
}

/**
 * 最小 flex 方向定义。
 */
internal enum class FlexDirection {
    HORIZONTAL,
    VERTICAL,
}

/**
 * 记录子节点在父 flex 内的绘制偏移。
 */
private data class ChildOffset(
    val x: Int = 0,
    val y: Int = 0,
)

/**
 * 主轴排布结果。
 */
private data class FlexMainAxisArrangement(
    val start: Int,
    val spacingAfterChild: Int,
)
