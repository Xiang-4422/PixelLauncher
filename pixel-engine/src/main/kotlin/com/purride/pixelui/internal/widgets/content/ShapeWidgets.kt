package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelPath
import com.purride.pixelui.PixelPoint

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

/** [Polygon] widget 的 render object widget 配置。 */
internal data class PolygonWidget(
    val points: List<PixelPoint>,
    val color: PixelColor,
    val filled: Boolean,
    override val key: Any? = null,
) : LeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderPolygon(points = points, color = color, filled = filled)
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderPolygon).update(points = points, color = color, filled = filled)
    }
}

/** [Path] widget 的 render object widget 配置。 */
internal data class PathWidget(
    val path: PixelPath,
    val color: PixelColor,
    val closed: Boolean,
    val strokeWidth: Int,
    override val key: Any? = null,
) : LeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderPath(path = path, color = color, closed = closed, strokeWidth = strokeWidth)
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderPath).update(path = path, color = color, closed = closed, strokeWidth = strokeWidth)
    }
}
