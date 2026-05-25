package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext

/** [Line] widget 的 render object widget 配置。 */
internal data class LineWidget(
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
    val color: PixelColor,
    override val key: Any? = null,
) : LeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderLine(
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            color = color,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderLine).update(
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            color = color,
        )
    }
}

/** [Circle] widget 的 render object widget 配置。 */
internal data class CircleWidget(
    val radius: Int,
    val color: PixelColor,
    val filled: Boolean,
    override val key: Any? = null,
) : LeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderCircle(radius = radius, color = color, filled = filled)
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderCircle).update(radius = radius, color = color, filled = filled)
    }
}
