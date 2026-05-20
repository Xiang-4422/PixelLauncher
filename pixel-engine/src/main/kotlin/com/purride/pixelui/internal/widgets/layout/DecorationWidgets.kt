package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelTone

import com.purride.pixelui.Alignment

import com.purride.pixelui.Widget

import com.purride.pixelui.internal.toPixelAlignment

/**
 * Flutter 风格 `GestureDetector` 的直接 render object widget。
 */
internal data class GestureDetectorWidget(
    override val child: Widget,
    val onTap: () -> Unit,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(
    child = child,
    key = key,
) {
    /**
     * 创建只承接点击语义的透明 surface。
     */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderSurface(
            fillTone = null,
            borderTone = null,
            alignment = PixelAlignment.TOP_START,
            onClick = onTap,
        )
    }

    /**
     * 同步新的点击回调到既有 surface render object。
     */
    override fun updateRenderObject(
        context: BuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderSurface).updateSurface(
            fillTone = null,
            borderTone = null,
            alignment = PixelAlignment.TOP_START,
            onClick = onTap,
        )
    }
}

/**
 * Flutter 风格 `DecoratedBox` 的直接 render object widget。
 */
internal data class DecoratedBoxWidget(
    override val child: Widget?,
    val fillTone: PixelTone,
    val borderTone: PixelTone?,
    val fillColor: PixelColor? = null,
    val borderColor: PixelColor? = null,
    val padding: Int,
    val alignment: Alignment,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(
    child = child,
    key = key,
) {
    /**
     * 创建直接接入 pipeline 的 surface render object。
     */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderSurface(
            fillTone = fillTone,
            borderTone = borderTone,
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

    /**
     * 同步新的装饰配置到既有 surface render object。
     */
    override fun updateRenderObject(
        context: BuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderSurface).updateSurface(
            fillTone = fillTone,
            borderTone = borderTone,
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
