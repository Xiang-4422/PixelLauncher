package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelBoxConstraints
import kotlin.math.min

/** 实现 `RenderConstraintWidgets` 在 retained render pipeline 中的布局、绘制与命中职责。 */
public class RenderWrap(
    children: List<RenderBox> = emptyList(),
    private var spacing: Int = 0,
    private var runSpacing: Int = 0,
) : MultiChildRenderObject() {
    private val childOffsets = mutableListOf<PixelRect>()

    init {
        setRenderObjectChildren(children)
    }

    /** 更新 `RenderConstraintWidgets` 的 `updateWrap` 状态，并保持相关边界与派生状态一致。 */
    public fun updateWrap(spacing: Int, runSpacing: Int) {
        if (this.spacing == spacing && this.runSpacing == runSpacing) return
        this.spacing = spacing
        this.runSpacing = runSpacing
        markNeedsLayout()
        markNeedsPaint()
    }

    override fun setRenderObjectChildren(children: List<RenderObject>) {
        super.setRenderObjectChildren(children)
        while (childOffsets.size < renderChildren.size) childOffsets += PixelRect(0, 0, 0, 0)
        while (childOffsets.size > renderChildren.size) childOffsets.removeAt(childOffsets.lastIndex)
    }

    override fun layout(constraints: RenderConstraints) {
        val safeSpacing = spacing.coerceAtLeast(0)
        val safeRunSpacing = runSpacing.coerceAtLeast(0)
        val maxWidth = constraints.maxWidth.coerceAtLeast(0)
        val childConstraints = RenderConstraints(maxWidth = maxWidth, maxHeight = constraints.maxHeight)
        var x = 0
        var y = 0
        var rowHeight = 0
        var usedWidth = 0
        renderChildren.forEachIndexed { index, child ->
            child.layout(childConstraints)
            if (x > 0 && x + child.size.width > maxWidth) {
                x = 0
                y += rowHeight + safeRunSpacing
                rowHeight = 0
            }
            childOffsets[index] = PixelRect(x, y, child.size.width, child.size.height)
            x += child.size.width + safeSpacing
            rowHeight = rowHeight.coerceAtLeast(child.size.height)
            usedWidth = usedWidth.coerceAtLeast(childOffsets[index].left + child.size.width)
        }
        size = RenderSize(
            width = constraints.constrainWidth(usedWidth),
            height = constraints.constrainHeight(if (renderChildren.isEmpty()) 0 else y + rowHeight),
        )
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        renderChildren.forEachIndexed { index, child ->
            val rect = childOffsets[index]
            child.paint(context, offsetX + rect.left, offsetY + rect.top)
        }
    }

    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        renderChildren.forEachIndexed { index, child ->
            val rect = childOffsets[index]
            child.hitTest(localX - rect.left, localY - rect.top, result)
        }
    }

    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        renderChildren.forEachIndexed { index, child ->
            val rect = childOffsets[index]
            child.collectClickTargets(offsetX + rect.left, offsetY + rect.top, targets)
        }
    }

    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>): Unit = collect(offsetX, offsetY) { child, x, y -> child.collectPagerTargets(x, y, targets) }
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>): Unit = collect(offsetX, offsetY) { child, x, y -> child.collectListTargets(x, y, targets) }
    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>): Unit = collect(offsetX, offsetY) { child, x, y -> child.collectScrollbarTargets(x, y, targets) }
    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>): Unit = collect(offsetX, offsetY) { child, x, y -> child.collectTextInputTargets(x, y, targets) }
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>): Unit = collect(offsetX, offsetY) { child, x, y -> child.collectSliderTargets(x, y, targets) }
    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>): Unit = collect(offsetX, offsetY) { child, x, y -> child.collectSemantics(x, y, targets) }

    private fun collect(offsetX: Int, offsetY: Int, block: (RenderBox, Int, Int) -> Unit) {
        renderChildren.forEachIndexed { index, child ->
            val rect = childOffsets[index]
            block(child, offsetX + rect.left, offsetY + rect.top)
        }
    }

    private val renderChildren: List<RenderBox>
        get() = children.filterIsInstance<RenderBox>()
}

/** 实现 `RenderConstraintWidgets` 在 retained render pipeline 中的布局、绘制与命中职责。 */
public class RenderAspectRatio(
    child: RenderBox? = null,
    private var aspectRatio: Float,
) : SingleChildRenderObject() {
    init {
        require(aspectRatio > 0f) { "aspectRatio must be > 0" }
        setRenderObjectChild(child)
    }

    /** 更新 `RenderConstraintWidgets` 的 `updateAspectRatio` 状态，并保持相关边界与派生状态一致。 */
    public fun updateAspectRatio(aspectRatio: Float) {
        require(aspectRatio > 0f) { "aspectRatio must be > 0" }
        if (this.aspectRatio == aspectRatio) return
        this.aspectRatio = aspectRatio
        markNeedsLayout()
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        var width = constraints.maxWidth
        var height = (width / aspectRatio).toInt().coerceAtLeast(1)
        if (height > constraints.maxHeight) {
            height = constraints.maxHeight
            width = (height * aspectRatio).toInt().coerceAtLeast(1)
        }
        width = constraints.constrainWidth(width)
        height = constraints.constrainHeight(height)
        renderChild?.layout(RenderConstraints(minWidth = width, maxWidth = width, minHeight = height, maxHeight = height))
        size = RenderSize(width, height)
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        renderChild?.paint(context, offsetX, offsetY)
    }

    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        if (localX in 0 until size.width && localY in 0 until size.height) renderChild?.hitTest(localX, localY, result)
    }

    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>): Unit = renderChild?.collectClickTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>): Unit = renderChild?.collectPagerTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>): Unit = renderChild?.collectListTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>): Unit = renderChild?.collectScrollbarTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>): Unit = renderChild?.collectTextInputTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>): Unit = renderChild?.collectSliderTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>): Unit = renderChild?.collectSemantics(offsetX, offsetY, targets) ?: Unit

    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

/** 实现 `RenderConstraintWidgets` 在 retained render pipeline 中的布局、绘制与命中职责。 */
public class RenderConstrainedBox(
    child: RenderBox? = null,
    private var additionalConstraints: PixelBoxConstraints,
) : SingleChildRenderObject() {
    init {
        setRenderObjectChild(child)
    }

    /** 更新 `RenderConstraintWidgets` 的 `updateConstrainedBox` 状态，并保持相关边界与派生状态一致。 */
    public fun updateConstrainedBox(constraints: PixelBoxConstraints) {
        if (additionalConstraints == constraints) return
        additionalConstraints = constraints
        markNeedsLayout()
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        val minWidth = constraints.minWidth.coerceAtLeast(additionalConstraints.minWidth)
            .coerceAtMost(constraints.maxWidth.coerceAtMost(additionalConstraints.maxWidth))
        val minHeight = constraints.minHeight.coerceAtLeast(additionalConstraints.minHeight)
            .coerceAtMost(constraints.maxHeight.coerceAtMost(additionalConstraints.maxHeight))
        val merged = RenderConstraints(
            minWidth = minWidth,
            maxWidth = constraints.maxWidth.coerceAtMost(additionalConstraints.maxWidth),
            minHeight = minHeight,
            maxHeight = constraints.maxHeight.coerceAtMost(additionalConstraints.maxHeight),
        )
        renderChild?.layout(merged)
        size = RenderSize(
            width = constraints.constrainWidth(renderChild?.size?.width ?: merged.minWidth),
            height = constraints.constrainHeight(renderChild?.size?.height ?: merged.minHeight),
        )
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        renderChild?.paint(context, offsetX, offsetY)
    }

    override fun hitTest(localX: Int, localY: Int, result: HitTestResult): Unit =
        renderChild?.hitTest(localX, localY, result) ?: Unit
    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>): Unit = renderChild?.collectClickTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>): Unit = renderChild?.collectPagerTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>): Unit = renderChild?.collectListTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>): Unit = renderChild?.collectScrollbarTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>): Unit = renderChild?.collectTextInputTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>): Unit = renderChild?.collectSliderTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>): Unit = renderChild?.collectSemantics(offsetX, offsetY, targets) ?: Unit

    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

/** 定义 `RenderFittedBox` 在 `RenderConstraintWidgets` 中承担的数据与行为边界。
 *
 * Scales one child with contain fitting while preserving its retained semantic identities.
 */
public class RenderFittedBox(child: RenderBox? = null) : SingleChildRenderObject() {
    /** Fixed-point numerator used by paint and semantic geometry transformation. */
    private var scaleNumerator = 1

    /** Fixed-point denominator used by paint and semantic geometry transformation. */
    private var scaleDenominator = 1

    init {
        setRenderObjectChild(child)
    }

    /** Measures the natural child and resolves one uniform contain scale. */
    override fun layout(constraints: RenderConstraints) {
        val childConstraints = RenderConstraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        renderChild?.layout(childConstraints)
        size = RenderSize(width = constraints.maxWidth, height = constraints.maxHeight)
        val childSize = renderChild?.size ?: RenderSize.Zero
        if (childSize.width <= 0 || childSize.height <= 0) {
            scaleNumerator = 1
            scaleDenominator = 1
            return
        }
        val widthRatio = constraints.maxWidth.toFloat() / childSize.width
        val heightRatio = constraints.maxHeight.toFloat() / childSize.height
        val ratio = min(widthRatio, heightRatio).coerceAtLeast(0f)
        scaleDenominator = 1_000
        scaleNumerator = (ratio * scaleDenominator).toInt().coerceAtLeast(1)
    }

    /** Paints the child into the centered contain-fitted destination rectangle. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val child = renderChild ?: return
        if (child.size.width <= 0 || child.size.height <= 0) return
        val scratch = context.bufferPool.acquire(child.size.width, child.size.height)
        try {
            /** Exact destination extent used by the fixed-point contain transform. */
            val scaledWidth = scaledContentWidth(child)
            /** Exact destination extent used by the fixed-point contain transform. */
            val scaledHeight = scaledContentHeight(child)
            /** Centered destination origin shared with [blitScaledContain]. */
            val childOriginX = offsetX + (size.width - scaledWidth) / 2
            /** Centered destination origin shared with [blitScaledContain]. */
            val childOriginY = offsetY + (size.height - scaledHeight) / 2
            child.paint(
                context.deriveScaled(
                    scratch = scratch,
                    localOriginX = childOriginX,
                    localOriginY = childOriginY,
                    scaleNumeratorX = scaledWidth,
                    scaleDenominatorX = child.size.width,
                    scaleNumeratorY = scaledHeight,
                    scaleDenominatorY = child.size.height,
                ),
                0,
                0,
            )
            blitScaledContain(scratch, context, offsetX, offsetY)
        } finally {
            context.bufferPool.release(scratch)
        }
    }

    /** Maps pointer coordinates back into the child's existing local coordinate system. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        renderChild?.hitTest(localX * scaleDenominator / scaleNumerator, localY * scaleDenominator / scaleNumerator, result)
    }

    /**
     * Transforms semantic rectangles through the same centered contain fit used by paint.
     *
     * Collection starts at child-local zero so nested layout offsets are scaled exactly once.
     * Stable ids, parent ids, state, and executable callbacks are retained by target copies.
     */
    override fun collectSemantics(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelSemanticsTarget>,
    ) {
        val child = renderChild ?: return
        if (child.size.width <= 0 || child.size.height <= 0) return
        /** Integer destination dimensions match the nearest-neighbor paint loop exactly. */
        val scaledWidth = scaledContentWidth(child)
        val scaledHeight = scaledContentHeight(child)
        /** Center offsets are part of the visual transform and therefore part of semantic bounds. */
        val startX = offsetX + (size.width - scaledWidth) / 2
        val startY = offsetY + (size.height - scaledHeight) / 2
        /** Local collection avoids applying the host offset before fixed-point scaling. */
        val collected = mutableListOf<PixelSemanticsTarget>()
        child.collectSemantics(offsetX = 0, offsetY = 0, targets = collected)
        val transformed = collected.mapNotNull { target ->
            transformSemanticTarget(
                target = target,
                sourceWidth = child.size.width,
                sourceHeight = child.size.height,
                destinationLeft = startX,
                destinationTop = startY,
                destinationWidth = scaledWidth,
                destinationHeight = scaledHeight,
            )
        }
        targets += clipSemanticTargets(
            collected = transformed,
            clip = PixelRect(left = startX, top = startY, width = scaledWidth, height = scaledHeight),
        )
    }

    /** Copies scaled pixels into the same centered rectangle used by semantic transformation. */
    private fun blitScaledContain(source: PixelBuffer, context: PaintContext, offsetX: Int, offsetY: Int) {
        val scaledWidth = scaledContentWidth(source.width)
        val scaledHeight = scaledContentHeight(source.height)
        val startX = offsetX + (size.width - scaledWidth) / 2
        val startY = offsetY + (size.height - scaledHeight) / 2
        for (y in 0 until scaledHeight) {
            val srcY = y * source.height / scaledHeight
            for (x in 0 until scaledWidth) {
                val srcX = x * source.width / scaledWidth
                val color = source.getPixel(srcX, srcY)
                if (color != PixelColor.Transparent) context.buffer.setPixel(startX + x, startY + y, color)
            }
        }
    }

    /** Returns the paint width produced for [child] by the resolved fixed-point scale. */
    private fun scaledContentWidth(child: RenderBox): Int = scaledContentWidth(child.size.width)

    /** Returns the paint width produced for [sourceWidth] by the resolved fixed-point scale. */
    private fun scaledContentWidth(sourceWidth: Int): Int {
        return (sourceWidth.toLong() * scaleNumerator.toLong() / scaleDenominator.toLong())
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()
    }

    /** Returns the paint height produced for [child] by the resolved fixed-point scale. */
    private fun scaledContentHeight(child: RenderBox): Int = scaledContentHeight(child.size.height)

    /** Returns the paint height produced for [sourceHeight] by the resolved fixed-point scale. */
    private fun scaledContentHeight(sourceHeight: Int): Int {
        return (sourceHeight.toLong() * scaleNumerator.toLong() / scaleDenominator.toLong())
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()
    }

    /** Current child participating in layout, paint, hit testing, and semantic transformation. */
    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

/** Scales one local semantic rectangle into a fitted destination rectangle. */
@Suppress("LongParameterList")
private fun transformSemanticTarget(
    target: PixelSemanticsTarget,
    sourceWidth: Int,
    sourceHeight: Int,
    destinationLeft: Int,
    destinationTop: Int,
    destinationWidth: Int,
    destinationHeight: Int,
): PixelSemanticsTarget? {
    val node = target.node
    if (node.width <= 0 || node.height <= 0) return null
    /** Floor on leading edges and ceil on trailing edges retain every painted source cell. */
    val transformedLeft = destinationLeft + scaleBoundaryFloor(
        coordinate = node.left.toLong(),
        sourceExtent = sourceWidth,
        destinationExtent = destinationWidth,
    )
    val transformedTop = destinationTop + scaleBoundaryFloor(
        coordinate = node.top.toLong(),
        sourceExtent = sourceHeight,
        destinationExtent = destinationHeight,
    )
    val transformedRight = destinationLeft + scaleBoundaryCeil(
        coordinate = node.left.toLong() + node.width.toLong(),
        sourceExtent = sourceWidth,
        destinationExtent = destinationWidth,
    )
    val transformedBottom = destinationTop + scaleBoundaryCeil(
        coordinate = node.top.toLong() + node.height.toLong(),
        sourceExtent = sourceHeight,
        destinationExtent = destinationHeight,
    )
    if (transformedRight <= transformedLeft || transformedBottom <= transformedTop) return null
    return target.copy(
        node = node.copy(
            left = transformedLeft,
            top = transformedTop,
            width = transformedRight - transformedLeft,
            height = transformedBottom - transformedTop,
        ),
    )
}

/** Maps a leading source boundary using mathematical floor, including negative overflow offsets. */
private fun scaleBoundaryFloor(
    coordinate: Long,
    sourceExtent: Int,
    destinationExtent: Int,
): Int {
    val scaled = coordinate * destinationExtent.toLong()
    return Math.floorDiv(scaled, sourceExtent.coerceAtLeast(1).toLong()).coerceToInt()
}

/** Maps a trailing source boundary using mathematical ceil, including negative overflow offsets. */
private fun scaleBoundaryCeil(
    coordinate: Long,
    sourceExtent: Int,
    destinationExtent: Int,
): Int {
    val scaled = coordinate * destinationExtent.toLong()
    val denominator = sourceExtent.coerceAtLeast(1).toLong()
    return (-Math.floorDiv(-scaled, denominator)).coerceToInt()
}

/** Saturates transformed coordinates before they enter the Int-based render geometry model. */
private fun Long.coerceToInt(): Int {
    return coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}
