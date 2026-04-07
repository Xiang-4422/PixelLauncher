package com.purride.pixelui.internal

import com.purride.pixelui.internal.legacy.PixelCrossAxisAlignment
import com.purride.pixelui.internal.legacy.PixelMainAxisAlignment
import com.purride.pixelui.internal.legacy.PixelMainAxisSize

/**
 * 新渲染管线里的最小弹性布局对象。
 *
 * 第一版只覆盖 `Row / Column` 的基础主轴排布、交叉轴对齐和 spacing，
 * 不支持权重布局；一旦子节点里出现权重，lowering 会整树回退。
 */
internal class RenderFlex(
    private val direction: FlexDirection,
    private val children: List<RenderBox>,
    private val spacing: Int = 0,
    private val mainAxisSize: PixelMainAxisSize = PixelMainAxisSize.MIN,
    private val mainAxisAlignment: PixelMainAxisAlignment = PixelMainAxisAlignment.START,
    private val crossAxisAlignment: PixelCrossAxisAlignment = PixelCrossAxisAlignment.START,
    private val explicitWidth: Int? = null,
    private val explicitHeight: Int? = null,
    private val fillMaxWidth: Boolean = false,
    private val fillMaxHeight: Boolean = false,
    private val paddingLeft: Int = 0,
    private val paddingTop: Int = 0,
    private val paddingRight: Int = 0,
    private val paddingBottom: Int = 0,
    private val onClick: (() -> Unit)? = null,
) : RenderBox() {
    private val childOffsets = MutableList(children.size) { ChildOffset() }

    init {
        children.forEach { child ->
            child.parent = this
        }
    }

    /**
     * 遍历所有直接子节点。
     */
    override fun visitChildren(visitor: (RenderObject) -> Unit) {
        children.forEach(visitor)
    }

    /**
     * 在给定约束下完成最小 flex 布局。
     */
    override fun layout(constraints: RenderConstraints) {
        val innerConstraints = constraints.inset(
            left = paddingLeft,
            top = paddingTop,
            right = paddingRight,
            bottom = paddingBottom,
        )
        val childConstraints = when (direction) {
            FlexDirection.HORIZONTAL -> RenderConstraints(
                maxWidth = innerConstraints.maxWidth,
                maxHeight = resolvedCrossAxisMax(innerConstraints.maxHeight),
            )

            FlexDirection.VERTICAL -> RenderConstraints(
                maxWidth = resolvedCrossAxisMax(innerConstraints.maxWidth),
                maxHeight = innerConstraints.maxHeight,
            )
        }

        children.forEach { child ->
            child.layout(childConstraints)
        }

        val childrenMainExtent = children.sumOf(::mainExtentOf)
        val totalSpacing = (spacing * (children.size - 1).coerceAtLeast(0)).coerceAtLeast(0)
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

        val mainSpacing = resolveMainSpacing(
            availableMainExtent = availableMainExtent,
            childrenMainExtent = childrenMainExtent,
            baseSpacing = totalSpacing,
            childCount = children.size,
        )
        val startOffset = resolveMainStartOffset(
            availableMainExtent = availableMainExtent,
            childrenMainExtent = childrenMainExtent,
            appliedSpacing = mainSpacing,
            childCount = children.size,
        )

        var cursor = startOffset
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
            cursor += mainExtentOf(child) + mainSpacing
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
     * 解析交叉轴在 stretch 情况下对子节点允许的最大尺寸。
     */
    private fun resolvedCrossAxisMax(currentMax: Int): Int {
        return currentMax.coerceAtLeast(0)
    }

    /**
     * 解析主轴起始偏移。
     */
    private fun resolveMainStartOffset(
        availableMainExtent: Int,
        childrenMainExtent: Int,
        appliedSpacing: Int,
        childCount: Int,
    ): Int {
        val totalContentExtent = childrenMainExtent + (appliedSpacing * (childCount - 1).coerceAtLeast(0))
        val freeMainExtent = (availableMainExtent - totalContentExtent).coerceAtLeast(0)
        return when (mainAxisAlignment) {
            PixelMainAxisAlignment.START,
            PixelMainAxisAlignment.SPACE_BETWEEN,
            -> 0

            PixelMainAxisAlignment.CENTER,
            PixelMainAxisAlignment.SPACE_AROUND,
            PixelMainAxisAlignment.SPACE_EVENLY,
            -> freeMainExtent / 2

            PixelMainAxisAlignment.END -> freeMainExtent
        }
    }

    /**
     * 解析主轴间距。
     */
    private fun resolveMainSpacing(
        availableMainExtent: Int,
        childrenMainExtent: Int,
        baseSpacing: Int,
        childCount: Int,
    ): Int {
        if (childCount <= 1) {
            return 0
        }
        val freeMainExtent = (availableMainExtent - childrenMainExtent).coerceAtLeast(0)
        return when (mainAxisAlignment) {
            PixelMainAxisAlignment.START,
            PixelMainAxisAlignment.CENTER,
            PixelMainAxisAlignment.END,
            -> spacing

            PixelMainAxisAlignment.SPACE_BETWEEN -> freeMainExtent / (childCount - 1)
            PixelMainAxisAlignment.SPACE_AROUND -> freeMainExtent / childCount
            PixelMainAxisAlignment.SPACE_EVENLY -> freeMainExtent / (childCount + 1)
        }.coerceAtLeast(baseSpacing.takeIf { mainAxisAlignment == PixelMainAxisAlignment.START || mainAxisAlignment == PixelMainAxisAlignment.CENTER || mainAxisAlignment == PixelMainAxisAlignment.END } ?: 0)
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
