package com.purride.pixelui.internal

import com.purride.pixelui.PixelWindowInsets

/**
 * Full-parent overlay layout that constrains, aligns, and clips one presentation to safe bounds.
 *
 * Stable system-bar padding and transient IME insets are merged before they reach this object.
 * Paint, pointer hit testing, every interaction-target channel, and semantics all use the same
 * [safeViewport] so an oversized descendant cannot escape visually or interactively.
 */
internal class RenderSafeOverlayLayout(
    child: RenderBox? = null,
    /** Current per-side exclusion inset in logical pixels. */
    private var safeInsets: PixelWindowInsets,
    /** Current center or bottom-center placement policy. */
    private var alignment: SafeOverlayAlignment,
    /** Whether the child must occupy the full safe width. */
    private var fillSafeWidth: Boolean,
) : SingleChildRenderObject() {
    /** Child x coordinate relative to this full-parent layout. */
    private var childOffsetX: Int = 0

    /** Child y coordinate relative to this full-parent layout. */
    private var childOffsetY: Int = 0

    /** Latest safe rectangle relative to this full-parent layout. */
    private var safeViewport: PixelRect = PixelRect(left = 0, top = 0, width = 0, height = 0)

    init {
        setRenderObjectChild(child)
    }

    /** Retargets safe geometry and alignment while preserving child State and render identity. */
    fun updateSafeOverlayLayout(
        safeInsets: PixelWindowInsets,
        alignment: SafeOverlayAlignment,
        fillSafeWidth: Boolean,
    ) {
        if (
            this.safeInsets == safeInsets &&
            this.alignment == alignment &&
            this.fillSafeWidth == fillSafeWidth
        ) {
            return
        }
        this.safeInsets = safeInsets
        this.alignment = alignment
        this.fillSafeWidth = fillSafeWidth
        markNeedsLayout()
        markNeedsPaint()
    }

    /** Computes the safe viewport, measures the child inside it, and resolves final placement. */
    override fun layout(constraints: RenderConstraints) {
        size = RenderSize(
            width = constraints.constrainWidth(constraints.maxWidth),
            height = constraints.constrainHeight(constraints.maxHeight),
        )
        safeViewport = resolveSafeViewport(width = size.width, height = size.height)
        /** Child constraints prevent ordinary descendants from exceeding the safe viewport. */
        val childConstraints = RenderConstraints(
            minWidth = if (fillSafeWidth) safeViewport.width else 0,
            maxWidth = safeViewport.width,
            minHeight = 0,
            maxHeight = safeViewport.height,
        )
        renderChild?.layout(childConstraints)
        /** Constrained child size shared by paint, hit testing, and target collection. */
        val childSize = renderChild?.size ?: RenderSize.Zero
        childOffsetX = safeViewport.left + ((safeViewport.width - childSize.width) / 2).coerceAtLeast(0)
        childOffsetY = when (alignment) {
            SafeOverlayAlignment.Center -> {
                safeViewport.top + ((safeViewport.height - childSize.height) / 2).coerceAtLeast(0)
            }

            SafeOverlayAlignment.BottomCenter -> safeViewport.bottom - childSize.height
        }.coerceAtLeast(safeViewport.top)
    }

    /** Paints through a safe-sized scratch buffer so misbehaving descendants cannot overflow. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val child = renderChild ?: return
        if (safeViewport.width <= 0 || safeViewport.height <= 0) return
        /** Scratch buffer represents exactly the visible safe viewport. */
        val scratch = context.bufferPool.acquire(safeViewport.width, safeViewport.height)
        scratch.clear()
        try {
            /** Derived origin preserves Host-global geometry for nested anchored overlays. */
            val scratchContext = context.derive(
                scratch = scratch,
                localOriginX = offsetX + safeViewport.left,
                localOriginY = offsetY + safeViewport.top,
            )
            child.paint(
                context = scratchContext,
                offsetX = childOffsetX - safeViewport.left,
                offsetY = childOffsetY - safeViewport.top,
            )
            context.buffer.blitRegion(
                source = scratch,
                sourceX = 0,
                sourceY = 0,
                copyWidth = safeViewport.width,
                copyHeight = safeViewport.height,
                destX = offsetX + safeViewport.left,
                destY = offsetY + safeViewport.top,
            )
        } finally {
            context.bufferPool.release(scratch)
        }
    }

    /** Rejects points outside the safe viewport before routing into the aligned child. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        if (!safeViewport.contains(localX, localY)) return
        renderChild?.hitTest(
            localX = localX - childOffsetX,
            localY = localY - childOffsetY,
            result = result,
        )
    }

    /** Exports click targets intersected with the same safe viewport used for paint. */
    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        collectClippedTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectClickTargets(x, y, bucket)
        }
    }

    /** Exports pager targets intersected with the same safe viewport used for paint. */
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) {
        collectClippedTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectPagerTargets(x, y, bucket)
        }
    }

    /** Exports list targets intersected with the same safe viewport used for paint. */
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) {
        collectClippedTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectListTargets(x, y, bucket)
        }
    }

    /** Exports scrollbar targets intersected with the same safe viewport used for paint. */
    override fun collectScrollbarTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelScrollbarTarget>,
    ) {
        collectClippedTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectScrollbarTargets(x, y, bucket)
        }
    }

    /** Exports refresh targets intersected with the same safe viewport used for paint. */
    override fun collectRefreshTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelRefreshTarget>) {
        collectClippedTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectRefreshTargets(x, y, bucket)
        }
    }

    /** Exports text-input targets intersected with the same safe viewport used for paint. */
    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        collectClippedTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectTextInputTargets(x, y, bucket)
        }
    }

    /** Exports slider targets intersected with the same safe viewport used for paint. */
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) {
        collectClippedTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectSliderTargets(x, y, bucket)
        }
    }

    /** Clips semantic rectangles and repairs ancestry when a fully hidden ancestor is removed. */
    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        val child = renderChild ?: return
        /** Isolated collection keeps invisible semantic descendants out of the frame snapshot. */
        val collected = mutableListOf<PixelSemanticsTarget>()
        child.collectSemantics(offsetX + childOffsetX, offsetY + childOffsetY, collected)
        targets += clipSemanticTargets(collected = collected, clip = globalSafeViewport(offsetX, offsetY))
    }

    /** Collects one interaction channel and intersects every exported target with safe bounds. */
    private inline fun <T> collectClippedTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<T>,
        collect: (RenderBox, Int, Int, MutableList<T>) -> Unit,
    ) {
        val child = renderChild ?: return
        /** Temporary target bucket allows immutable bounds to be replaced after clipping. */
        val collected = mutableListOf<T>()
        collect(child, offsetX + childOffsetX, offsetY + childOffsetY, collected)
        /** Host-global safe rectangle shared by every target category. */
        val clip = globalSafeViewport(offsetX, offsetY)
        collected.mapNotNullTo(targets) { target -> clipSafeOverlayTarget(target, clip) }
    }

    /** Resolves clamped per-side insets without allowing a negative safe dimension. */
    private fun resolveSafeViewport(width: Int, height: Int): PixelRect {
        /** Left edge after clamping malformed oversized inset input. */
        val left = safeInsets.left.coerceIn(0, width)
        /** Top edge after clamping malformed oversized inset input. */
        val top = safeInsets.top.coerceIn(0, height)
        /** Right exclusion clamped against the width remaining after [left]. */
        val right = safeInsets.right.coerceIn(0, width - left)
        /** Bottom exclusion clamped against the height remaining after [top]. */
        val bottom = safeInsets.bottom.coerceIn(0, height - top)
        return PixelRect(
            left = left,
            top = top,
            width = width - left - right,
            height = height - top - bottom,
        )
    }

    /** Converts the retained local safe viewport into Host-global target coordinates. */
    private fun globalSafeViewport(offsetX: Int, offsetY: Int): PixelRect {
        return safeViewport.translate(deltaX = offsetX, deltaY = offsetY)
    }

    /** Current render-box child retained across inset and alignment changes. */
    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

/**
 * Clips the elastic title/body region to the height left after a safe surface measures its footer.
 *
 * This boundary intentionally keeps its child's constrained intrinsic size instead of filling the
 * available height, so ordinary Dialogs remain compact while oversized descendants cannot paint,
 * hit, or expose semantics underneath the fixed action row.
 */
internal class RenderSafeOverlayBodyViewport(
    /** Initially attached title/content render subtree, if already inflated. */
    child: RenderBox? = null,
) : SingleChildRenderObject() {
    init {
        setRenderObjectChild(child)
    }

    /** Measures the body under the flex allocation and retains the child's constrained size. */
    override fun layout(constraints: RenderConstraints) {
        renderChild?.layout(constraints)
        /** Child size already reflects the loose remaining-height constraint. */
        val childSize = renderChild?.size ?: RenderSize.Zero
        size = RenderSize(
            width = constraints.constrainWidth(childSize.width),
            height = constraints.constrainHeight(childSize.height),
        )
    }

    /** Paints into a body-sized buffer so oversized descendants cannot cover footer actions. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val child = renderChild ?: return
        if (size.width <= 0 || size.height <= 0) return
        /** Scratch buffer is exactly the visible elastic body rectangle. */
        val scratch = context.bufferPool.acquire(size.width, size.height)
        scratch.clear()
        try {
            child.paint(
                context = context.derive(
                    scratch = scratch,
                    localOriginX = offsetX,
                    localOriginY = offsetY,
                ),
                offsetX = 0,
                offsetY = 0,
            )
            context.buffer.blitRegion(
                source = scratch,
                sourceX = 0,
                sourceY = 0,
                copyWidth = size.width,
                copyHeight = size.height,
                destX = offsetX,
                destY = offsetY,
            )
        } finally {
            context.bufferPool.release(scratch)
        }
    }

    /** Rejects pointer paths outside the elastic body before visiting descendants. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        if (!localViewport.contains(localX, localY)) return
        renderChild?.hitTest(localX = localX, localY = localY, result = result)
    }

    /** Exports click targets intersected with the elastic body. */
    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        collectClippedTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectClickTargets(x, y, bucket)
        }
    }

    /** Exports pager targets intersected with the elastic body. */
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) {
        collectClippedTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectPagerTargets(x, y, bucket)
        }
    }

    /** Exports list targets intersected with the elastic body. */
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) {
        collectClippedTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectListTargets(x, y, bucket)
        }
    }

    /** Exports scrollbar track and thumb geometry intersected with the elastic body. */
    override fun collectScrollbarTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelScrollbarTarget>,
    ) {
        collectClippedTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectScrollbarTargets(x, y, bucket)
        }
    }

    /** Exports refresh targets intersected with the elastic body. */
    override fun collectRefreshTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelRefreshTarget>) {
        collectClippedTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectRefreshTargets(x, y, bucket)
        }
    }

    /** Exports text-input targets intersected with the elastic body. */
    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        collectClippedTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectTextInputTargets(x, y, bucket)
        }
    }

    /** Exports slider targets intersected with the elastic body. */
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) {
        collectClippedTargets(offsetX, offsetY, targets) { child, x, y, bucket ->
            child.collectSliderTargets(x, y, bucket)
        }
    }

    /** Clips body semantics and repairs ancestry when an oversized ancestor becomes invisible. */
    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        val child = renderChild ?: return
        /** Isolated collection allows every descendant rectangle to be clipped consistently. */
        val collected = mutableListOf<PixelSemanticsTarget>()
        child.collectSemantics(offsetX, offsetY, collected)
        targets += clipSemanticTargets(collected = collected, clip = globalViewport(offsetX, offsetY))
    }

    /** Collects one target channel and clips immutable geometry to the elastic body. */
    private inline fun <T> collectClippedTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<T>,
        collect: (RenderBox, Int, Int, MutableList<T>) -> Unit,
    ) {
        val child = renderChild ?: return
        /** Temporary bucket preserves descendant ordering while replacing clipped target values. */
        val collected = mutableListOf<T>()
        collect(child, offsetX, offsetY, collected)
        /** Global body rectangle used by every interaction category. */
        val clip = globalViewport(offsetX, offsetY)
        collected.mapNotNullTo(targets) { target -> clipSafeOverlayTarget(target, clip) }
    }

    /** Converts the local elastic body bounds to Host-global target coordinates. */
    private fun globalViewport(offsetX: Int, offsetY: Int): PixelRect {
        return PixelRect(left = offsetX, top = offsetY, width = size.width, height = size.height)
    }

    /** Local viewport used for retained hit testing. */
    private val localViewport: PixelRect
        get() = PixelRect(left = 0, top = 0, width = size.width, height = size.height)

    /** Current body render-box child retained across safe-area and IME relayout. */
    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

/** Copies one known target type with all of its geometry intersected against [clip]. */
@Suppress("UNCHECKED_CAST")
private fun <T> clipSafeOverlayTarget(target: T, clip: PixelRect): T? {
    return when (target) {
        is PixelClickTarget -> target.bounds.intersect(clip)?.let { target.copy(bounds = it) as T }
        is PixelPagerTarget -> target.bounds.intersect(clip)?.let { target.copy(bounds = it) as T }
        is PixelListTarget -> target.bounds.intersect(clip)?.let { target.copy(bounds = it) as T }
        is PixelScrollbarTarget -> {
            /** Visible track is the primary scrollbar interaction rectangle. */
            val clippedBounds = target.bounds.intersect(clip) ?: return null
            /** Thumb must remain visible and be a subset of the identically clipped track. */
            val clippedThumbBounds = target.thumbBounds.intersect(clippedBounds) ?: return null
            target.copy(bounds = clippedBounds, thumbBounds = clippedThumbBounds) as T
        }
        is PixelRefreshTarget -> target.bounds.intersect(clip)?.let { target.copy(bounds = it) as T }
        is PixelTextInputTarget -> target.bounds.intersect(clip)?.let { target.copy(bounds = it) as T }
        is PixelSliderTarget -> target.bounds.intersect(clip)?.let { target.copy(bounds = it) as T }
        else -> target
    }
}
