package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.Alignment
import com.purride.pixelui.AlignmentDirectional
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Directionality
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.InternalBuildContext
import com.purride.pixelui.PixelContainerStyle
import com.purride.pixelui.PixelThemeData
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
    val style: PixelContainerStyle?,
    val theme: PixelThemeData?,
    val fillTone: PixelTone,
    val borderTone: PixelTone?,
    val alignment: Alignment,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(
    child = child,
    key = key,
) {
    /**
     * 创建直接承接尺寸、margin、padding、装饰和对齐的 surface render object。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        val resolvedStyle = resolveContainerStyle(context)
        return RenderSurface(
            fillTone = resolvedStyle.fillTone,
            borderTone = resolvedStyle.borderTone,
            alignment = resolvedStyle.alignment.toPixelAlignment(),
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

    /**
     * 同步新的容器配置到既有 surface render object。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        val resolvedStyle = resolveContainerStyle(context)
        (renderObject as RenderSurface).updateSurface(
            fillTone = resolvedStyle.fillTone,
            borderTone = resolvedStyle.borderTone,
            alignment = resolvedStyle.alignment.toPixelAlignment(),
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

    /**
     * 解析当前上下文下的最终容器样式。
     */
    private fun resolveContainerStyle(context: BuildContext): PixelContainerStyle {
        val resolvedTheme = context.resolveTheme(theme)
        val requestedStyle = style ?: PixelContainerStyle(
            fillTone = fillTone,
            borderTone = borderTone,
            alignment = alignment,
        )
        return when (requestedStyle) {
            PixelContainerStyle.Default -> resolvedTheme.containerStyle
            resolvedTheme.accentContainerStyle -> resolvedTheme.accentContainerStyle
            else -> requestedStyle
        }
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
    val style: PixelContainerStyle?,
    val theme: PixelThemeData?,
    val fillTone: PixelTone,
    val borderTone: PixelTone?,
    val alignment: AlignmentDirectional,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 在 build 时解析方向性配置，并交给 direct ContainerWidget。
     */
    override fun build(context: BuildContext): Widget {
        val direction = Directionality.of(context)
        val resolvedPadding = paddingDirectional?.resolve(direction) ?: padding
        val resolvedMargin = marginDirectional?.resolve(direction) ?: margin
        val resolvedStyle = style?.copy(
            alignment = alignment.toPixelAlignment(direction).toPublicAlignment(),
        )
        return ContainerWidget(
            child = child,
            width = width,
            height = height,
            padding = resolvedPadding,
            margin = resolvedMargin,
            style = resolvedStyle,
            theme = theme,
            fillTone = fillTone,
            borderTone = borderTone,
            alignment = alignment.toPixelAlignment(direction).toPublicAlignment(),
            key = key,
        )
    }

    /**
     * 把内部对齐值映射回公开对齐值，避免 directional widget 直接持有 legacy 模型。
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
