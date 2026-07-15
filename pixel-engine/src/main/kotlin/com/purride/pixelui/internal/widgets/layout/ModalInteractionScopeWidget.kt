package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Widget

/**
 * 定义 `ModalInteractionScopeWidget` 在 `ModalInteractionScopeWidget` 中承担的数据与行为边界。
 *
 * Internal widget that grants one logically active modal subtree exclusive render interactions.
 *
 * Focus ownership is intentionally handled by the per-runtime focus owner. This render boundary
 * covers pointer targets, text/slider owners, hit testing, and accessibility semantics only.
 */
public data class ModalInteractionScopeWidget(
    /** Whether [child] currently owns the modal interaction channels. */
    val active: Boolean,
    /** Canonical OverlayHost presentation order, or `null` outside a hosted route. */
    val overlayOrder: Long? = null,
    /** Modal presentation rendered above the background it isolates. */
    override val child: Widget,
    /** Stable identity retained across enter and exit motion. */
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    /** Creates the retained modal interaction boundary. */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderModalInteractionScope(active = active, overlayOrder = overlayOrder)
    }

    /** Retargets logical ownership and route order without replacing the presentation subtree. */
    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderModalInteractionScope).update(
            active = active,
            overlayOrder = overlayOrder,
        )
    }
}
