package com.purride.pixelui.internal

import com.purride.pixelui.Alignment
import com.purride.pixelui.AlignmentDirectional
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Directionality
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.InternalBuildContext
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.resolve
import com.purride.pixelui.internal.toPixelAlignment

/**
 * Flutter 风格 `Padding` 的直接 render object widget。
 */
internal data class PaddingWidget(
    override val child: Widget,
    val padding: EdgeInsets,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(
    child = child,
    key = key,
) {
    /**
     * 创建承接 padding 的 surface render object。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderSurface(
            fillTone = null,
            borderTone = null,
            alignment = PixelAlignment.TOP_START,
            contentPaddingLeft = padding.left,
            contentPaddingTop = padding.top,
            contentPaddingRight = padding.right,
            contentPaddingBottom = padding.bottom,
        )
    }

    /**
     * 同步新的 padding 配置到既有 surface render object。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderSurface).updateSurface(
            fillTone = null,
            borderTone = null,
            alignment = PixelAlignment.TOP_START,
            contentPaddingLeft = padding.left,
            contentPaddingTop = padding.top,
            contentPaddingRight = padding.right,
            contentPaddingBottom = padding.bottom,
        )
    }
}

/**
 * 方向性感知的 padding bridge widget。
 */
internal data class PaddingDirectionalWidget(
    val child: Widget,
    val padding: EdgeInsetsDirectional,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 在 build 时按当前方向解析 padding。
     */
    override fun build(context: BuildContext): Widget {
        val resolvedPadding = padding.resolve(Directionality.of(context))
        return PaddingWidget(
            child = child,
            padding = resolvedPadding,
            key = key,
        )
    }
}

/**
 * Flutter 风格 `Align` 的直接 render object widget。
 */
internal data class AlignWidget(
    override val child: Widget,
    val alignment: Alignment,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(
    child = child,
    key = key,
) {
    /**
     * 创建承接对齐的 surface render object。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderSurface(
            fillTone = null,
            borderTone = null,
            alignment = alignment.toPixelAlignment(),
            fillMaxWidth = true,
            fillMaxHeight = true,
        )
    }

    /**
     * 同步新的对齐配置到既有 surface render object。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderSurface).updateSurface(
            fillTone = null,
            borderTone = null,
            alignment = alignment.toPixelAlignment(),
            fillMaxWidth = true,
            fillMaxHeight = true,
        )
    }
}

/**
 * 方向性感知的 `Align` bridge widget。
 */
internal data class AlignDirectionalWidget(
    val child: Widget,
    val alignment: AlignmentDirectional,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 在 build 时按当前方向解析对齐，并交给 direct AlignWidget。
     */
    override fun build(context: BuildContext): Widget {
        return AlignWidget(
            child = child,
            alignment = alignment.toPixelAlignment(Directionality.of(context)).toPublicAlignment(),
            key = key,
        )
    }

    /**
     * 把内部对齐值映射回公开对齐值。
     */
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

/**
 * Flutter 风格 `SizedBox` 的直接 render object widget。
 */
internal data class SizedBoxWidget(
    val width: Int?,
    val height: Int?,
    override val child: Widget?,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(
    child = child,
    key = key,
) {
    /**
     * 创建承接固定尺寸的透明 surface。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderSurface(
            fillTone = null,
            borderTone = null,
            alignment = PixelAlignment.TOP_START,
            explicitWidth = width,
            explicitHeight = height,
            tightChildWidth = width != null,
            tightChildHeight = height != null,
        )
    }

    /**
     * 同步新的固定尺寸到既有 surface render object。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderSurface).updateSurface(
            fillTone = null,
            borderTone = null,
            alignment = PixelAlignment.TOP_START,
            explicitWidth = width,
            explicitHeight = height,
            tightChildWidth = width != null,
            tightChildHeight = height != null,
        )
    }
}
