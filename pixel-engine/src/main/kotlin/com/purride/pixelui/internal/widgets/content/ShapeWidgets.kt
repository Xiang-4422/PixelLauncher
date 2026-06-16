package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelPath
import com.purride.pixelui.PixelPoint
import com.purride.pixelui.PixelShapeStyle

/** [Line] widget 的 render object widget 配置。 */
internal data class LineWidget(
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
    val color: PixelColor,
    val style: PixelShapeStyle = PixelShapeStyle(color = color),
    override val key: Any? = null,
) : LeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderLine(
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            color = style.color,
            strokeWidth = style.strokeWidth,
            blendMode = style.blendMode,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderLine).update(
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            color = style.color,
            strokeWidth = style.strokeWidth,
            blendMode = style.blendMode,
        )
    }
}

/** [Circle] widget 的 render object widget 配置。 */
internal data class CircleWidget(
    val radius: Int,
    val color: PixelColor,
    val filled: Boolean,
    val style: PixelShapeStyle = PixelShapeStyle(color = color, filled = filled),
    override val key: Any? = null,
) : LeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderCircle(
            radius = radius,
            color = style.color,
            filled = style.filled,
            strokeWidth = style.strokeWidth,
            blendMode = style.blendMode,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderCircle).update(
            radius = radius,
            color = style.color,
            filled = style.filled,
            strokeWidth = style.strokeWidth,
            blendMode = style.blendMode,
        )
    }
}

/** [Polygon] widget 的 render object widget 配置。 */
internal data class PolygonWidget(
    val points: List<PixelPoint>,
    val color: PixelColor,
    val filled: Boolean,
    val style: PixelShapeStyle = PixelShapeStyle(color = color, filled = filled),
    override val key: Any? = null,
) : LeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderPolygon(
            points = points,
            color = style.color,
            filled = style.filled,
            strokeWidth = style.strokeWidth,
            blendMode = style.blendMode,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderPolygon).update(
            points = points,
            color = style.color,
            filled = style.filled,
            strokeWidth = style.strokeWidth,
            blendMode = style.blendMode,
        )
    }
}

/** [Path] widget 的 render object widget 配置。 */
internal data class PathWidget(
    val path: PixelPath,
    val color: PixelColor,
    val closed: Boolean,
    val strokeWidth: Int,
    val style: PixelShapeStyle = PixelShapeStyle(color = color, filled = false, strokeWidth = strokeWidth),
    override val key: Any? = null,
) : LeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderPath(
            path = path,
            color = style.color,
            closed = closed,
            strokeWidth = style.strokeWidth,
            blendMode = style.blendMode,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderPath).update(
            path = path,
            color = style.color,
            closed = closed,
            strokeWidth = style.strokeWidth,
            blendMode = style.blendMode,
        )
    }
}
