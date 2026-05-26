package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelBoxConstraints
import com.purride.pixelui.Widget

internal data class WrapWidget(
    override val children: List<Widget>,
    val spacing: Int,
    val runSpacing: Int,
    override val key: Any? = null,
) : MultiChildRenderObjectWidget(children = children, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderWrap(spacing = spacing, runSpacing = runSpacing)
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderWrap).updateWrap(spacing = spacing, runSpacing = runSpacing)
    }
}

internal data class AspectRatioWidget(
    override val child: Widget,
    val aspectRatio: Float,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderAspectRatio(aspectRatio = aspectRatio)
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderAspectRatio).updateAspectRatio(aspectRatio)
    }
}

internal data class ConstrainedBoxWidget(
    override val child: Widget,
    val constraints: PixelBoxConstraints,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderConstrainedBox(additionalConstraints = constraints)
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderConstrainedBox).updateConstrainedBox(constraints)
    }
}

internal data class FittedBoxWidget(
    override val child: Widget,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject = RenderFittedBox()

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) = Unit
}
