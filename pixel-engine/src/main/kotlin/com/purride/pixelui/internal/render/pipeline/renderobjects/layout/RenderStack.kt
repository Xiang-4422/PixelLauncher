package com.purride.pixelui.internal

import java.util.IdentityHashMap

/**
 * 新渲染管线里的最小叠放布局对象。
 */
public class RenderStack(
    /** Initial retained children ordered from back to front. */
    children: List<RenderBox> = emptyList(),
    /** Alignment applied to every non-positioned child. */
    private var alignment: PixelAlignment = PixelAlignment.TOP_START,
) : MultiChildRenderObject() {
    /** Higher siblings deferred after a lower sibling registered a root-lifted presentation. */
    private var deferredPaintChildren: IdentityHashMap<RenderBox, Boolean> = IdentityHashMap()

    init {
        setRenderObjectChildren(children)
    }

    /**
     * 更新 stack 对齐配置。
     */
    public fun updateStack(alignment: PixelAlignment) {
        if (this.alignment == alignment) {
            return
        }
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
        /** Root-plane count before this Stack starts traversing its own ordered children. */
        val layerCheckpoint = RenderOverlayLayerRegistry.layerCheckpoint()
        /** New identity set published after this paint for matching target and hit traversal. */
        val nextDeferredChildren = IdentityHashMap<RenderBox, Boolean>()
        renderChildren.forEach { child ->
            /** Stable local placement shared by immediate and deferred painting. */
            val childOffset = resolveChildOffset(child)
            /** Whether an earlier child inserted a lifted plane that this sibling must cover. */
            val shouldDefer = layerCheckpoint != null &&
                RenderOverlayLayerRegistry.hasLayersAfter(layerCheckpoint)
            /** Deferral mode distinguishing retained root subtrees from scratch raster captures. */
            val deferredMode = if (shouldDefer) {
                RenderOverlayLayerRegistry.registerDeferredSibling(
                    layer = child,
                    context = context,
                    offsetX = offsetX + childOffset.x,
                    offsetY = offsetY + childOffset.y,
                )
            } else {
                RenderDeferredSiblingMode.Rejected
            }
            when (deferredMode) {
                RenderDeferredSiblingMode.DeferredSubtree -> nextDeferredChildren[child] = true
                RenderDeferredSiblingMode.CapturedRaster -> Unit
                RenderDeferredSiblingMode.Rejected -> child.paint(
                    context = context,
                    offsetX = offsetX + childOffset.x,
                    offsetY = offsetY + childOffset.y,
                )
            }
        }
        deferredPaintChildren = nextDeferredChildren
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
        forEachInFlowPaintChild { child ->
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
        forEachInFlowPaintChild { child ->
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
        forEachInFlowPaintChild { child ->
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
        forEachInFlowPaintChild { child ->
            val childOffset = resolveChildOffset(child)
            child.collectListTargets(
                offsetX = offsetX + childOffset.x,
                offsetY = offsetY + childOffset.y,
                targets = targets,
            )
        }
    }

    /** Exports scrollbar targets from children that remain in the in-flow paint plane. */
    override fun collectScrollbarTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelScrollbarTarget>,
    ) {
        forEachInFlowPaintChild { child ->
            val childOffset = resolveChildOffset(child)
            child.collectScrollbarTargets(
                offsetX = offsetX + childOffset.x,
                offsetY = offsetY + childOffset.y,
                targets = targets,
            )
        }
    }

    /** Exports refresh targets from children that remain in the in-flow paint plane. */
    override fun collectRefreshTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelRefreshTarget>,
    ) {
        forEachInFlowPaintChild { child ->
            val childOffset = resolveChildOffset(child)
            child.collectRefreshTargets(
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
        forEachInFlowPaintChild { child ->
            val childOffset = resolveChildOffset(child)
            child.collectTextInputTargets(
                offsetX = offsetX + childOffset.x,
                offsetY = offsetY + childOffset.y,
                targets = targets,
            )
        }
    }

    /** Exports slider targets from children that remain in the in-flow paint plane. */
    override fun collectSliderTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelSliderTarget>,
    ) {
        forEachInFlowPaintChild { child ->
            val childOffset = resolveChildOffset(child)
            child.collectSliderTargets(
                offsetX = offsetX + childOffset.x,
                offsetY = offsetY + childOffset.y,
                targets = targets,
            )
        }
    }

    /** Exports semantics from children that remain in the in-flow paint plane. */
    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        forEachInFlowPaintChild { child ->
            val childOffset = resolveChildOffset(child)
            child.collectSemantics(offsetX + childOffset.x, offsetY + childOffset.y, targets)
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

    /** Visits only children still painted inline; deferred siblings are replayed by PipelineOwner. */
    private inline fun forEachInFlowPaintChild(block: (RenderBox) -> Unit) {
        renderChildren.forEach { child ->
            if (!deferredPaintChildren.containsKey(child)) block(child)
        }
    }
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
public class RenderPositioned(
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
    public fun updatePosition(
        left: Int?,
        top: Int?,
        right: Int?,
        bottom: Int?,
        width: Int?,
        height: Int?,
    ) {
        if (
            this.left == left &&
            this.top == top &&
            this.right == right &&
            this.bottom == bottom &&
            this.width == width &&
            this.height == height
        ) {
            return
        }
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

    override fun collectScrollbarTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelScrollbarTarget>,
    ) {
        renderChild?.collectScrollbarTargets(
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

    override fun collectSliderTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelSliderTarget>,
    ) {
        renderChild?.collectSliderTargets(
            offsetX = offsetX + childOffsetX,
            offsetY = offsetY + childOffsetY,
            targets = targets,
        )
    }

    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        renderChild?.collectSemantics(offsetX + childOffsetX, offsetY + childOffsetY, targets)
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
