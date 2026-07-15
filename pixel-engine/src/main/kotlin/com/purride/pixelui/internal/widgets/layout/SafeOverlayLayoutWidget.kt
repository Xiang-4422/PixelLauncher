package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.PixelWindowInsets
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget

/** Vertical placement supported by the reusable safe overlay viewport. */
internal enum class SafeOverlayAlignment {
    /** Centers the measured overlay inside the safe viewport. */
    Center,

    /** Pins the measured overlay to the safe viewport's bottom edge. */
    BottomCenter,
}

/**
 * Resolves stable window padding and transient IME insets for one safe overlay presentation.
 *
 * The per-side maximum keeps a bottom navigation inset from being added twice when the IME is
 * visible, while still honoring whichever exclusion is larger on every edge.
 */
internal data class SafeOverlayViewportWidget(
    /** Overlay surface constrained and positioned inside resolved safe bounds. */
    val child: Widget,
    /** Center or bottom-center placement policy applied after measurement. */
    val alignment: SafeOverlayAlignment,
    /** Whether the surface must fill the resolved safe viewport width. */
    val fillSafeWidth: Boolean,
    /** Stable retained identity for the inherited-inset resolver. */
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    /** Rebuilds the render primitive whenever inherited window geometry changes. */
    override fun build(context: BuildContext): Widget {
        /** Optional Host geometry; direct render tests and embedded runtimes may omit MediaQuery. */
        val media = MediaQuery.maybeOf(context)
        /** Per-side maximum of stable system bounds and transient occlusion, or no exclusion. */
        val safeInsets = media
            ?.let { data -> data.viewPadding.atLeast(data.viewInsets) }
            ?: PixelWindowInsets.Zero
        return SafeOverlayLayoutWidget(
            child = child,
            safeInsets = safeInsets,
            alignment = alignment,
            fillSafeWidth = fillSafeWidth,
            key = key?.let { "$it-render" },
        )
    }
}

/**
 * Retained widget that constrains and clips one overlay to system-bar and IME-safe bounds.
 *
 * [fillSafeWidth] is reserved for sheet-style surfaces; dialog-style surfaces keep their
 * measured width while sharing the same clipping and interaction geometry.
 */
internal data class SafeOverlayLayoutWidget(
    /** Overlay surface positioned inside the safe viewport. */
    override val child: Widget,
    /** Per-side exclusion inset already merged from stable and transient window insets. */
    val safeInsets: PixelWindowInsets,
    /** Vertical placement policy for the measured surface. */
    val alignment: SafeOverlayAlignment,
    /** Whether the surface receives a tight safe-viewport width constraint. */
    val fillSafeWidth: Boolean,
    /** Stable retained identity for the layout and clipping boundary. */
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    /** Creates the render object that owns safe geometry, clipping, and target forwarding. */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderSafeOverlayLayout(
            safeInsets = safeInsets,
            alignment = alignment,
            fillSafeWidth = fillSafeWidth,
        )
    }

    /** Updates safe geometry without replacing the retained overlay subtree. */
    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderSafeOverlayLayout).updateSafeOverlayLayout(
            safeInsets = safeInsets,
            alignment = alignment,
            fillSafeWidth = fillSafeWidth,
        )
    }
}

/**
 * Elastic overlay body boundary that clips oversized title/content output before the fixed footer.
 *
 * The surrounding `Flexible(fit = loose)` supplies only the height left after actions are measured.
 * This widget keeps short content intrinsic while giving long content a consistent visual,
 * interaction, and semantics viewport.
 */
internal data class SafeOverlayBodyViewportWidget(
    /** Title and content subtree allowed to consume the remaining overlay height. */
    override val child: Widget,
    /** Stable retained identity derived from the owning Dialog or BottomSheet. */
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    /** Creates the clipped elastic body render boundary. */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderSafeOverlayBodyViewport()
    }

    /** The body viewport has no mutable configuration beyond its retained child. */
    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject): Unit = Unit
}
