package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelBoxConstraints
import kotlin.math.min

internal class RenderWrap(
    children: List<RenderBox> = emptyList(),
    private var spacing: Int = 0,
    private var runSpacing: Int = 0,
) : MultiChildRenderObject() {
    private val childOffsets = mutableListOf<PixelRect>()

    init {
        setRenderObjectChildren(children)
    }

    fun updateWrap(spacing: Int, runSpacing: Int) {
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

    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) = collect(offsetX, offsetY) { child, x, y -> child.collectPagerTargets(x, y, targets) }
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) = collect(offsetX, offsetY) { child, x, y -> child.collectListTargets(x, y, targets) }
    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>) = collect(offsetX, offsetY) { child, x, y -> child.collectScrollbarTargets(x, y, targets) }
    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>) = collect(offsetX, offsetY) { child, x, y -> child.collectTextInputTargets(x, y, targets) }
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) = collect(offsetX, offsetY) { child, x, y -> child.collectSliderTargets(x, y, targets) }
    override fun collectSemantics(offsetX: Int, offsetY: Int, nodes: MutableList<com.purride.pixelui.PixelSemanticsNode>) = collect(offsetX, offsetY) { child, x, y -> child.collectSemantics(x, y, nodes) }

    private fun collect(offsetX: Int, offsetY: Int, block: (RenderBox, Int, Int) -> Unit) {
        renderChildren.forEachIndexed { index, child ->
            val rect = childOffsets[index]
            block(child, offsetX + rect.left, offsetY + rect.top)
        }
    }

    private val renderChildren: List<RenderBox>
        get() = children.filterIsInstance<RenderBox>()
}

internal class RenderAspectRatio(
    child: RenderBox? = null,
    private var aspectRatio: Float,
) : SingleChildRenderObject() {
    init {
        require(aspectRatio > 0f) { "aspectRatio must be > 0" }
        setRenderObjectChild(child)
    }

    fun updateAspectRatio(aspectRatio: Float) {
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

    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) = renderChild?.collectClickTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) = renderChild?.collectPagerTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) = renderChild?.collectListTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>) = renderChild?.collectScrollbarTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>) = renderChild?.collectTextInputTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) = renderChild?.collectSliderTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectSemantics(offsetX: Int, offsetY: Int, nodes: MutableList<com.purride.pixelui.PixelSemanticsNode>) = renderChild?.collectSemantics(offsetX, offsetY, nodes) ?: Unit

    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

internal class RenderConstrainedBox(
    child: RenderBox? = null,
    private var additionalConstraints: PixelBoxConstraints,
) : SingleChildRenderObject() {
    init {
        setRenderObjectChild(child)
    }

    fun updateConstrainedBox(constraints: PixelBoxConstraints) {
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

    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) = renderChild?.hitTest(localX, localY, result) ?: Unit
    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) = renderChild?.collectClickTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) = renderChild?.collectPagerTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) = renderChild?.collectListTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>) = renderChild?.collectScrollbarTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>) = renderChild?.collectTextInputTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) = renderChild?.collectSliderTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectSemantics(offsetX: Int, offsetY: Int, nodes: MutableList<com.purride.pixelui.PixelSemanticsNode>) = renderChild?.collectSemantics(offsetX, offsetY, nodes) ?: Unit

    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

internal class RenderFittedBox(child: RenderBox? = null) : SingleChildRenderObject() {
    private var scaleNumerator = 1
    private var scaleDenominator = 1

    init {
        setRenderObjectChild(child)
    }

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

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val child = renderChild ?: return
        if (child.size.width <= 0 || child.size.height <= 0) return
        val scratch = context.bufferPool.acquire(child.size.width, child.size.height)
        try {
            child.paint(PaintContext(scratch, context.bufferPool), 0, 0)
            blitScaledContain(scratch, context, offsetX, offsetY)
        } finally {
            context.bufferPool.release(scratch)
        }
    }

    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        renderChild?.hitTest(localX * scaleDenominator / scaleNumerator, localY * scaleDenominator / scaleNumerator, result)
    }

    private fun blitScaledContain(source: PixelBuffer, context: PaintContext, offsetX: Int, offsetY: Int) {
        val scaledWidth = (source.width * scaleNumerator / scaleDenominator).coerceAtLeast(1)
        val scaledHeight = (source.height * scaleNumerator / scaleDenominator).coerceAtLeast(1)
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

    private val renderChild: RenderBox?
        get() = child as? RenderBox
}
