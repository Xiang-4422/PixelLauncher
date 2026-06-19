package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.toPixelAlignment

/**
 * Flutter 风格 `GestureDetector` 的直接 render object widget。
 */
internal data class GestureDetectorWidget(
    override val child: Widget,
    val onTap: () -> Unit,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderSurface(
            alignment = PixelAlignment.TOP_START,
            onClick = onTap,
            preserveChildMinConstraints = true,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderSurface).updateSurface(
            alignment = PixelAlignment.TOP_START,
            onClick = onTap,
            preserveChildMinConstraints = true,
        )
    }
}

/**
 * Flutter 风格 `DecoratedBox` 的直接 render object widget。
 */
internal data class DecoratedBoxWidget(
    override val child: Widget?,
    val fillColor: PixelColor? = null,
    val borderColor: PixelColor? = null,
    val padding: Int,
    val alignment: Alignment,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderSurface(
            fillColor = fillColor,
            borderColor = borderColor,
            alignment = alignment.toPixelAlignment(),
            fillMaxWidth = child == null,
            fillMaxHeight = child == null,
            contentPaddingLeft = padding,
            contentPaddingTop = padding,
            contentPaddingRight = padding,
            contentPaddingBottom = padding,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderSurface).updateSurface(
            fillColor = fillColor,
            borderColor = borderColor,
            alignment = alignment.toPixelAlignment(),
            fillMaxWidth = child == null,
            fillMaxHeight = child == null,
            contentPaddingLeft = padding,
            contentPaddingTop = padding,
            contentPaddingRight = padding,
            contentPaddingBottom = padding,
        )
    }
}
