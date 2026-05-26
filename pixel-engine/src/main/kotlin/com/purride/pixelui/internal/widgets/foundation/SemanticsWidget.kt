package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.Widget

internal data class SemanticsWidget(
    val label: String,
    val role: PixelSemanticRole,
    val enabled: Boolean,
    val focused: Boolean,
    override val child: Widget,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderSemantics(
            label = label,
            role = role,
            enabled = enabled,
            focused = focused,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderSemantics).updateSemantics(
            label = label,
            role = role,
            enabled = enabled,
            focused = focused,
        )
    }
}
