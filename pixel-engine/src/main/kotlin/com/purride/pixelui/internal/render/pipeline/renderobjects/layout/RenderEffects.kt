package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer

/**
 * Applies group opacity while preserving child layout and retained ownership.
 *
 * Effective opacity `0f` skips paint and suppresses every hit/interaction/semantics export.
 * Any positive value keeps those non-paint channels available, even when 8-bit alpha rounding
 * makes an extremely small painted value visually transparent.
 */
internal class RenderOpacity(
    child: RenderBox? = null,
    opacity: Float,
) : SingleChildRenderObject() {
    /** Finite effective opacity shared by paint, hit testing and target collection. */
    private var opacity: Float = normalizeRenderOpacity(opacity)

    /** Internal diagnostic property used to lock normalization without exposing stable API. */
    internal val effectiveOpacity: Float
        get() = opacity

    init {
        setRenderObjectChild(child)
    }

    /** Updates effective opacity and invalidates paint/derived frame targets when it changes. */
    fun updateOpacity(opacity: Float) {
        val safeOpacity = normalizeRenderOpacity(opacity)
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
        if (opacity <= 0f) return
        if (opacity >= 1f) {
            child.paint(context, offsetX, offsetY)
            return
        }
        val scratch = context.bufferPool.acquire(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1))
        scratch.clear()
        try {
            child.paint(context.derive(scratch, offsetX, offsetY), 0, 0)
            blendScratchWithOpacity(context.buffer, scratch, offsetX, offsetY, opacity)
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

    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        if (opacity > 0f) renderChild?.collectSemantics(offsetX, offsetY, targets)
    }

    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

/** Normalizes public/internal opacity input consistently; non-finite input is fully hidden. */
private fun normalizeRenderOpacity(value: Float): Float {
    return if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
}

/**
 * Paint-only retained boundary used after a motion entry has become logically inactive.
 *
 * The child keeps its layout and paint output, but hit testing, gesture targets, scroll targets,
 * text input, sliders, and semantics are intentionally not forwarded.
 */
internal class RenderVisualOnly(
    child: RenderBox? = null,
    /** Whether non-paint channels must be suppressed for the current presentation phase. */
    private var visualOnly: Boolean = true,
) : SingleChildRenderObject() {
    /** Whether lifted descendants must suppress hit testing, targets, and semantics this frame. */
    internal val suppressesLiftedOverlayTargets: Boolean
        get() = visualOnly

    init {
        setRenderObjectChild(child)
    }

    /** Changes logical interactivity while preserving the same render and retained child. */
    fun updateVisualOnly(visualOnly: Boolean) {
        if (this.visualOnly == visualOnly) return
        this.visualOnly = visualOnly
        // Frame target and semantics snapshots are rebuilt on the requested visual update.
        markNeedsPaint()
    }

    /** Gives the visual child normal layout while preserving this wrapper's constrained size. */
    override fun layout(constraints: RenderConstraints) {
        renderChild?.layout(constraints)
        val childSize = renderChild?.size ?: RenderSize.Zero
        size = RenderSize(
            width = constraints.constrainWidth(childSize.width),
            height = constraints.constrainHeight(childSize.height),
        )
    }

    /** Paints the retained exit visual without re-enabling any interaction channel. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        renderChild?.paint(context, offsetX, offsetY)
    }

    /** Forwards hit testing only while the presentation remains logically active. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        if (!visualOnly) renderChild?.hitTest(localX, localY, result)
    }

    /** Forwards click targets only while the presentation remains logically active. */
    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        if (!visualOnly) renderChild?.collectClickTargets(offsetX, offsetY, targets)
    }

    /** Forwards pager targets only while the presentation remains logically active. */
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) {
        if (!visualOnly) renderChild?.collectPagerTargets(offsetX, offsetY, targets)
    }

    /** Forwards list targets only while the presentation remains logically active. */
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) {
        if (!visualOnly) renderChild?.collectListTargets(offsetX, offsetY, targets)
    }

    /** Forwards scrollbar targets only while the presentation remains logically active. */
    override fun collectScrollbarTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelScrollbarTarget>,
    ) {
        if (!visualOnly) renderChild?.collectScrollbarTargets(offsetX, offsetY, targets)
    }

    /** Forwards refresh targets only while the presentation remains logically active. */
    override fun collectRefreshTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelRefreshTarget>) {
        if (!visualOnly) renderChild?.collectRefreshTargets(offsetX, offsetY, targets)
    }

    /** Forwards text-input targets only while the presentation remains logically active. */
    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        if (!visualOnly) renderChild?.collectTextInputTargets(offsetX, offsetY, targets)
    }

    /** Forwards slider targets only while the presentation remains logically active. */
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) {
        if (!visualOnly) renderChild?.collectSliderTargets(offsetX, offsetY, targets)
    }

    /** Forwards semantics only while the presentation remains logically active. */
    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        if (!visualOnly) renderChild?.collectSemantics(offsetX, offsetY, targets)
    }

    /** Current render-box child retained solely for layout and paint. */
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
            child.paint(context.derive(scratch, offsetX, offsetY), 0, 0)
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

    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        /** Descendants are isolated so clipped-out nodes cannot leak into the frame snapshot. */
        val collected = mutableListOf<PixelSemanticsTarget>()
        renderChild?.collectSemantics(offsetX, offsetY, collected)
        targets += clipSemanticTargets(
            collected = collected,
            clip = PixelRect(left = offsetX, top = offsetY, width = size.width, height = size.height),
        )
    }

    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

/**
 * 执行 `RenderEffects` 的 `clipSemanticTargets` 公开行为；具体参数、返回和副作用见下文。
 *
 * Intersects semantic bounds with [clip] and repairs parents when an invisible ancestor is removed.
 *
 * Action callbacks, stable ids, roles, and state are preserved because only the immutable node
 * geometry and, when necessary, its direct parent id are copied.
 */
public fun clipSemanticTargets(
    collected: List<PixelSemanticsTarget>,
    clip: PixelRect,
): List<PixelSemanticsTarget> {
    /** Original nodes provide the complete ancestor chain before visibility filtering. */
    val originalNodesById = collected.associateBy(
        keySelector = { target -> target.node.id },
        valueTransform = { target -> target.node },
    )
    /** Visible entries retain their clipped rectangles in original preorder. */
    val visibleTargets = collected.mapNotNull { target ->
        val node = target.node
        val bounds = PixelRect(left = node.left, top = node.top, width = node.width, height = node.height)
        bounds.intersect(clip)?.let { clipped ->
            target.copy(
                node = node.copy(
                    left = clipped.left,
                    top = clipped.top,
                    width = clipped.width,
                    height = clipped.height,
                ),
            )
        }
    }
    /** Only ids in this set may be referenced as parents in the returned visible tree. */
    val visibleIds = visibleTargets.mapTo(mutableSetOf()) { target -> target.node.id }
    return visibleTargets.map { target ->
        val visibleParentId = nearestVisibleSemanticParent(
            parentId = target.node.parentId,
            originalNodesById = originalNodesById,
            visibleIds = visibleIds,
        )
        if (visibleParentId == target.node.parentId) {
            target
        } else {
            target.copy(node = target.node.copy(parentId = visibleParentId))
        }
    }
}

/** Walks the original semantic ancestry until a visible parent or the Host root is reached. */
private fun nearestVisibleSemanticParent(
    parentId: Long?,
    originalNodesById: Map<Long, com.purride.pixelui.PixelSemanticsNode>,
    visibleIds: Set<Long>,
): Long? {
    /** The visited set guards malformed third-party snapshots from creating an ancestry cycle. */
    val visited = mutableSetOf<Long>()
    var candidateId = parentId
    while (candidateId != null && visited.add(candidateId)) {
        if (candidateId in visibleIds) return candidateId
        candidateId = originalNodesById[candidateId]?.parentId
    }
    return null
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

    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        renderChild?.collectSemantics(offsetX + dx, offsetY + dy, targets)
    }

    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

/** Blends one scratch buffer into [target] with a uniform group [opacity]. */
internal fun blendScratchWithOpacity(
    target: PixelBuffer,
    scratch: PixelBuffer,
    destX: Int,
    destY: Int,
    opacity: Float,
) {
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
