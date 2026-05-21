package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.AlignmentDirectional
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Directionality
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.resolve
import com.purride.pixelui.internal.toPixelAlignment

/**
 * Flutter 风格 `Container` 的直接 render object widget。
 */
internal data class ContainerWidget(
    override val child: Widget?,
    val width: Int?,
    val height: Int?,
    val padding: EdgeInsets?,
    val margin: EdgeInsets?,
    val fillColor: PixelColor? = null,
    val borderColor: PixelColor? = null,
    val alignment: Alignment = Alignment.CENTER,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(
    child = child,
    key = key,
) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderSurface(
            fillColor = fillColor,
            borderColor = borderColor,
            alignment = alignment.toPixelAlignment(),
            explicitWidth = width,
            explicitHeight = height,
            outerPaddingLeft = margin?.left ?: 0,
            outerPaddingTop = margin?.top ?: 0,
            outerPaddingRight = margin?.right ?: 0,
            outerPaddingBottom = margin?.bottom ?: 0,
            contentPaddingLeft = padding?.left ?: 0,
            contentPaddingTop = padding?.top ?: 0,
            contentPaddingRight = padding?.right ?: 0,
            contentPaddingBottom = padding?.bottom ?: 0,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderSurface).updateSurface(
            fillColor = fillColor,
            borderColor = borderColor,
            alignment = alignment.toPixelAlignment(),
            explicitWidth = width,
            explicitHeight = height,
            outerPaddingLeft = margin?.left ?: 0,
            outerPaddingTop = margin?.top ?: 0,
            outerPaddingRight = margin?.right ?: 0,
            outerPaddingBottom = margin?.bottom ?: 0,
            contentPaddingLeft = padding?.left ?: 0,
            contentPaddingTop = padding?.top ?: 0,
            contentPaddingRight = padding?.right ?: 0,
            contentPaddingBottom = padding?.bottom ?: 0,
        )
    }
}

/**
 * 方向性感知的 `Container` widget。
 */
internal data class ContainerDirectionalWidget(
    val child: Widget?,
    val width: Int?,
    val height: Int?,
    val padding: EdgeInsets?,
    val paddingDirectional: EdgeInsetsDirectional?,
    val margin: EdgeInsets?,
    val marginDirectional: EdgeInsetsDirectional?,
    val fillColor: PixelColor? = null,
    val borderColor: PixelColor? = null,
    val alignment: AlignmentDirectional = AlignmentDirectional.CENTER,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget {
        val direction = Directionality.of(context)
        val resolvedPadding = paddingDirectional?.resolve(direction) ?: padding
        val resolvedMargin = marginDirectional?.resolve(direction) ?: margin
        return ContainerWidget(
            child = child,
            width = width,
            height = height,
            padding = resolvedPadding,
            margin = resolvedMargin,
            fillColor = fillColor,
            borderColor = borderColor,
            alignment = alignment.toPixelAlignment(direction).toPublicAlignment(),
            key = key,
        )
    }

    private fun PixelAlignment.toPublicAlignment(): Alignment {
        return when (this) {
            PixelAlignment.TOP_START -> Alignment.TOP_START
            PixelAlignment.TOP_CENTER -> Alignment.TOP_CENTER
            PixelAlignment.TOP_END -> Alignment.TOP_END
            PixelAlignment.CENTER_START -> Alignment.CENTER_START
            PixelAlignment.CENTER -> Alignment.CENTER
            PixelAlignment.CENTER_END -> Alignment.CENTER_END
            PixelAlignment.BOTTOM_START -> Alignment.BOTTOM_START
            PixelAlignment.BOTTOM_CENTER -> Alignment.BOTTOM_CENTER
            PixelAlignment.BOTTOM_END -> Alignment.BOTTOM_END
        }
    }
}
