package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelPopoverAlignment
import com.purride.pixelui.PixelPopoverPlacement
import com.purride.pixelui.PixelWindowInsets
import com.purride.pixelui.TextDirection
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.IntOffset

/**
 * Keeps normal layout ownership on [anchor] while painting [presentation] in Host coordinates.
 *
 * The second branch remains a distinct render ancestry, allowing modal filtering to isolate the
 * barrier and popup without accidentally retaining the anchor's interaction targets.
 */
internal data class AnchoredOverlayPortalWidget(
    /** In-flow widget whose actual layout box defines the popup anchor. */
    val anchor: Widget,
    /** Optional full-viewport overlay branch painted after [anchor]. */
    val presentation: Widget?,
    /** Stable geometry link shared with the anchored follower inside [presentation]. */
    val link: PixelAnchoredOverlayLink,
    /** Current Host logical width used even when this portal is nested in a smaller parent. */
    val viewportWidth: Int,
    /** Current Host logical height used even when this portal is nested in a smaller parent. */
    val viewportHeight: Int,
    /** Stable retained identity for the two-branch portal. */
    override val key: Any? = null,
) : MultiChildRenderObjectWidget(
    children = listOfNotNull(anchor, presentation),
    key = key,
) {
    /** Creates the render portal that separates in-flow size from global presentation paint. */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderAnchoredOverlayPortal(
            link = link,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
    }

    /** Retargets viewport geometry without replacing either retained subtree. */
    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderAnchoredOverlayPortal).updatePortal(
            link = link,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
    }
}

/** Full-viewport follower that positions one measured popup from a painted anchor link. */
internal data class AnchoredOverlayFollowerWidget(
    /** Popup content measured loosely inside the safe viewport. */
    override val child: Widget,
    /** Geometry source updated by the paired [AnchoredOverlayPortalWidget]. */
    val link: PixelAnchoredOverlayLink,
    /** Preferred vertical side with automatic collision flipping by default. */
    val placement: PixelPopoverPlacement,
    /** Horizontal alignment relative to the anchor's actual bounds. */
    val alignment: PixelPopoverAlignment,
    /** Ambient logical direction used to resolve Start and End alignment. */
    val textDirection: TextDirection,
    /** Legacy anchor-origin adjustment applied before collision handling. */
    val contentOffset: IntOffset,
    /** Safe-area and IME exclusion insets in Host logical pixels. */
    val safeInsets: PixelWindowInsets,
    /** Additional non-negative distance retained from every viewport edge. */
    val viewportMargin: Int,
    /** Current Host logical width. */
    val viewportWidth: Int,
    /** Current Host logical height. */
    val viewportHeight: Int,
    /** Stable retained follower identity across open, resize, and flip transitions. */
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    /** Creates one follower whose child keeps normal layout and interaction ownership. */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderAnchoredOverlayFollower(
            link = link,
            placement = placement,
            alignment = alignment,
            textDirection = textDirection,
            contentOffset = contentOffset,
            safeInsets = safeInsets,
            viewportMargin = viewportMargin,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
    }

    /** Updates geometry policy in place so content State survives window changes. */
    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderAnchoredOverlayFollower).updateFollower(
            link = link,
            placement = placement,
            alignment = alignment,
            textDirection = textDirection,
            contentOffset = contentOffset,
            safeInsets = safeInsets,
            viewportMargin = viewportMargin,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
    }
}
