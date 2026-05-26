package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.IntOffset

internal data class OpacityWidget(
    override val child: Widget,
    val opacity: Float,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderOpacity(opacity = opacity.coerceIn(0f, 1f))
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderOpacity).updateOpacity(opacity)
    }
}

internal data class ClipRectWidget(
    override val child: Widget,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderClipRect()
    }
}

internal data class TransformTranslateWidget(
    override val child: Widget,
    val offset: IntOffset,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderTranslate(dx = offset.x, dy = offset.y)
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderTranslate).updateOffset(dx = offset.x, dy = offset.y)
    }
}
