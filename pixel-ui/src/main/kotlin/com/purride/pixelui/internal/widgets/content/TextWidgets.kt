package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Directionality
import com.purride.pixelui.InternalBuildContext
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.TextAlign
import com.purride.pixelui.internal.toPixelTextAlign

/**
 * Flutter 风格 `Text` 的直接 render object widget。
 */
internal data class TextWidget(
    val data: String,
    val style: PixelTextStyle,
    val theme: com.purride.pixelui.PixelThemeData?,
    val softWrap: Boolean,
    val maxLines: Int,
    val overflow: PixelTextOverflow,
    val textAlign: TextAlign,
    override val key: Any? = null,
) : RenderObjectWidget(
    key = key,
) {
    /**
     * 创建文本 render object，直接接入新 pipeline。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderText(
            text = data,
            style = resolveTextStyle(context),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            defaultTextRasterizer = PixelBitmapFont.Default,
        )
    }

    /**
     * 把新的 widget 配置同步到既有文本 render object。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderText).updateText(
            text = data,
            style = resolveTextStyle(context),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
        )
    }

    /**
     * 解析当前上下文下的最终文本样式。
     */
    private fun resolveTextStyle(context: BuildContext): PixelTextStyle {
        val resolvedTheme = context.resolveTheme(theme)
        return when (style) {
            PixelTextStyle.Default -> resolvedTheme.textStyle
            PixelTextStyle.Accent -> resolvedTheme.accentTextStyle
            else -> style
        }
    }
}
