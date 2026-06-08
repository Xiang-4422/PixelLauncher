package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer

internal class RenderOpacity(
    child: RenderBox? = null,
    private var opacity: Float,
) : SingleChildRenderObject() {
    init {
        setRenderObjectChild(child)
    }

    fun updateOpacity(opacity: Float) {
        val safeOpacity = opacity.coerceIn(0f, 1f)
        if (this.opacity == safeOpacity) return
        this.opacity = safeOpacity
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        renderChild?.layout(constraints)
        val childSize = renderChild?.size ?: RenderSize.Zero
        size = RenderSize(
            width = constraints.constrainWidth(childSize.width),
            height = constraints.constrainHeight(childSize.height),
        )
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val child = renderChild ?: return
        val safeOpacity = opacity.coerceIn(0f, 1f)
        if (safeOpacity <= 0f) return
        if (safeOpacity >= 1f) {
            child.paint(context, offsetX, offsetY)
            return
        }
        val scratch = context.bufferPool.acquire(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1))
        scratch.clear()
        try {
            child.paint(PaintContext(scratch, context.bufferPool), 0, 0)
            blendScratch(context.buffer, scratch, offsetX, offsetY, safeOpacity)
        } finally {
            context.bufferPool.release(scratch)
        }
    }

    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        if (opacity <= 0f) return
        renderChild?.hitTest(localX, localY, result)
    }

    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        if (opacity > 0f) renderChild?.collectClickTargets(offsetX, offsetY, targets)
    }

    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) {
        if (opacity > 0f) renderChild?.collectPagerTargets(offsetX, offsetY, targets)
    }

    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) {
        if (opacity > 0f) renderChild?.collectListTargets(offsetX, offsetY, targets)
    }

    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>) {
        if (opacity > 0f) renderChild?.collectScrollbarTargets(offsetX, offsetY, targets)
    }

    override fun collectRefreshTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelRefreshTarget>) {
        if (opacity > 0f) renderChild?.collectRefreshTargets(offsetX, offsetY, targets)
    }

    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>) {
        if (opacity > 0f) renderChild?.collectTextInputTargets(offsetX, offsetY, targets)
    }

    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) {
        if (opacity > 0f) renderChild?.collectSliderTargets(offsetX, offsetY, targets)
    }

    override fun collectSemantics(offsetX: Int, offsetY: Int, nodes: MutableList<com.purride.pixelui.PixelSemanticsNode>) {
        if (opacity > 0f) renderChild?.collectSemantics(offsetX, offsetY, nodes)
    }

    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

internal class RenderClipRect(
    child: RenderBox? = null,
) : SingleChildRenderObject() {
    init {
        setRenderObjectChild(child)
    }

    override fun layout(constraints: RenderConstraints) {
        renderChild?.layout(constraints)
        val childSize = renderChild?.size ?: RenderSize.Zero
        size = RenderSize(
            width = constraints.constrainWidth(childSize.width),
            height = constraints.constrainHeight(childSize.height),
        )
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val child = renderChild ?: return
        val scratch = context.bufferPool.acquire(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1))
        scratch.clear()
        try {
            child.paint(PaintContext(scratch, context.bufferPool), 0, 0)
            context.buffer.blitRegion(scratch, 0, 0, size.width, size.height, offsetX, offsetY)
        } finally {
            context.bufferPool.release(scratch)
        }
    }

    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        if (localX !in 0 until size.width || localY !in 0 until size.height) return
        renderChild?.hitTest(localX, localY, result)
    }

    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        val before = targets.size
        renderChild?.collectClickTargets(offsetX, offsetY, targets)
        trimClickTargetsOutsideClip(targets, before, offsetX, offsetY, size.width, size.height)
    }

    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) {
        val before = targets.size
        renderChild?.collectPagerTargets(offsetX, offsetY, targets)
        trimPagerTargetsOutsideClip(targets, before, offsetX, offsetY, size.width, size.height)
    }

    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) {
        val before = targets.size
        renderChild?.collectListTargets(offsetX, offsetY, targets)
        trimListTargetsOutsideClip(targets, before, offsetX, offsetY, size.width, size.height)
    }

    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>) {
        val before = targets.size
        renderChild?.collectScrollbarTargets(offsetX, offsetY, targets)
        trimScrollbarTargetsOutsideClip(targets, before, offsetX, offsetY, size.width, size.height)
    }

    override fun collectRefreshTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelRefreshTarget>) {
        val before = targets.size
        renderChild?.collectRefreshTargets(offsetX, offsetY, targets)
        trimRefreshTargetsOutsideClip(targets, before, offsetX, offsetY, size.width, size.height)
    }

    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>) {
        val before = targets.size
        renderChild?.collectTextInputTargets(offsetX, offsetY, targets)
        trimTextInputTargetsOutsideClip(targets, before, offsetX, offsetY, size.width, size.height)
    }

    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) {
        val before = targets.size
        renderChild?.collectSliderTargets(offsetX, offsetY, targets)
        trimSliderTargetsOutsideClip(targets, before, offsetX, offsetY, size.width, size.height)
    }

    override fun collectSemantics(offsetX: Int, offsetY: Int, nodes: MutableList<com.purride.pixelui.PixelSemanticsNode>) {
        renderChild?.collectSemantics(offsetX, offsetY, nodes)
    }

    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

internal class RenderTranslate(
    child: RenderBox? = null,
    private var dx: Int,
    private var dy: Int,
) : SingleChildRenderObject() {
    init {
        setRenderObjectChild(child)
    }

    fun updateOffset(dx: Int, dy: Int) {
        if (this.dx == dx && this.dy == dy) return
        this.dx = dx
        this.dy = dy
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        renderChild?.layout(constraints)
        val childSize = renderChild?.size ?: RenderSize.Zero
        size = RenderSize(
            width = constraints.constrainWidth(childSize.width),
            height = constraints.constrainHeight(childSize.height),
        )
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        renderChild?.paint(context, offsetX + dx, offsetY + dy)
    }

    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        renderChild?.hitTest(localX - dx, localY - dy, result)
    }

    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        renderChild?.collectClickTargets(offsetX + dx, offsetY + dy, targets)
    }

    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) {
        renderChild?.collectPagerTargets(offsetX + dx, offsetY + dy, targets)
    }

    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) {
        renderChild?.collectListTargets(offsetX + dx, offsetY + dy, targets)
    }

    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>) {
        renderChild?.collectScrollbarTargets(offsetX + dx, offsetY + dy, targets)
    }

    override fun collectRefreshTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelRefreshTarget>) {
        renderChild?.collectRefreshTargets(offsetX + dx, offsetY + dy, targets)
    }

    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>) {
        renderChild?.collectTextInputTargets(offsetX + dx, offsetY + dy, targets)
    }

    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) {
        renderChild?.collectSliderTargets(offsetX + dx, offsetY + dy, targets)
    }

    override fun collectSemantics(offsetX: Int, offsetY: Int, nodes: MutableList<com.purride.pixelui.PixelSemanticsNode>) {
        renderChild?.collectSemantics(offsetX + dx, offsetY + dy, nodes)
    }

    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

private fun blendScratch(target: PixelBuffer, scratch: PixelBuffer, destX: Int, destY: Int, opacity: Float) {
    val alphaScale = (opacity.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
    if (alphaScale <= 0) return
    for (y in 0 until scratch.height) {
        val targetY = destY + y
        if (targetY !in 0 until target.height) continue
        for (x in 0 until scratch.width) {
            val targetX = destX + x
            if (targetX !in 0 until target.width) continue
            val src = scratch.pixels[y * scratch.width + x]
            val srcAlpha = (src ushr 24) and 0xFF
            if (srcAlpha == 0) continue
            val scaledAlpha = (srcAlpha * alphaScale + 127) / 255
            val scaled = (src and 0x00FFFFFF) or (scaledAlpha shl 24)
            val dstIndex = targetY * target.width + targetX
            target.pixels[dstIndex] = PixelBuffer.blendSrcOver(src = scaled, dst = target.pixels[dstIndex])
        }
    }
}

private fun trimClickTargetsOutsideClip(targets: MutableList<PixelClickTarget>, startIndex: Int, left: Int, top: Int, width: Int, height: Int) {
    val clip = PixelRect(left, top, width, height)
    var index = targets.size - 1
    while (index >= startIndex) {
        if (!targets[index].bounds.intersects(clip)) targets.removeAt(index)
        index -= 1
    }
}

private fun trimPagerTargetsOutsideClip(targets: MutableList<PixelPagerTarget>, startIndex: Int, left: Int, top: Int, width: Int, height: Int) {
    val clip = PixelRect(left, top, width, height)
    var index = targets.size - 1
    while (index >= startIndex) {
        if (!targets[index].bounds.intersects(clip)) targets.removeAt(index)
        index -= 1
    }
}

private fun trimListTargetsOutsideClip(targets: MutableList<PixelListTarget>, startIndex: Int, left: Int, top: Int, width: Int, height: Int) {
    val clip = PixelRect(left, top, width, height)
    var index = targets.size - 1
    while (index >= startIndex) {
        if (!targets[index].bounds.intersects(clip)) targets.removeAt(index)
        index -= 1
    }
}

private fun trimScrollbarTargetsOutsideClip(targets: MutableList<PixelScrollbarTarget>, startIndex: Int, left: Int, top: Int, width: Int, height: Int) {
    val clip = PixelRect(left, top, width, height)
    var index = targets.size - 1
    while (index >= startIndex) {
        if (!targets[index].bounds.intersects(clip)) targets.removeAt(index)
        index -= 1
    }
}

private fun trimRefreshTargetsOutsideClip(targets: MutableList<PixelRefreshTarget>, startIndex: Int, left: Int, top: Int, width: Int, height: Int) {
    val clip = PixelRect(left, top, width, height)
    var index = targets.lastIndex
    while (index >= startIndex) {
        val target = targets[index]
        val clipped = target.bounds.intersect(clip)
        if (clipped == null) {
            targets.removeAt(index)
        } else if (clipped != target.bounds) {
            targets[index] = target.copy(bounds = clipped)
        }
        index -= 1
    }
}

private fun trimTextInputTargetsOutsideClip(targets: MutableList<PixelTextInputTarget>, startIndex: Int, left: Int, top: Int, width: Int, height: Int) {
    val clip = PixelRect(left, top, width, height)
    var index = targets.size - 1
    while (index >= startIndex) {
        if (!targets[index].bounds.intersects(clip)) targets.removeAt(index)
        index -= 1
    }
}

private fun trimSliderTargetsOutsideClip(targets: MutableList<PixelSliderTarget>, startIndex: Int, left: Int, top: Int, width: Int, height: Int) {
    val clip = PixelRect(left, top, width, height)
    var index = targets.size - 1
    while (index >= startIndex) {
        if (!targets[index].bounds.intersects(clip)) targets.removeAt(index)
        index -= 1
    }
}

private fun PixelRect.intersects(other: PixelRect): Boolean {
    return left < other.left + other.width &&
        left + width > other.left &&
        top < other.top + other.height &&
        top + height > other.top
}
