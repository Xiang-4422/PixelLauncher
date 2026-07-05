package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.DefaultTextRasterizer
import com.purride.pixelui.Directionality
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.TextAlign
import com.purride.pixelui.internal.toPixelTextAlign

/**
 * Flutter 风格 `Text` 的直接 render object widget。
 */
internal data class TextWidget(
    val data: String,
    val style: PixelTextStyle,
    val color: PixelColor? = null,
    val softWrap: Boolean,
    val maxLines: Int,
    val overflow: PixelTextOverflow,
    val textAlign: TextAlign,
    val paddingRight: Int = 0,
    override val key: Any? = null,
) : RenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderText(
            text = data,
            style = resolveStyle(context),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
            defaultTextRasterizer = DefaultTextRasterizer.of(context, fallback = PixelBitmapFont.Default),
            paddingRight = paddingRight,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderText).updateText(
            text = data,
            style = resolveStyle(context),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
            defaultTextRasterizer = DefaultTextRasterizer.of(context, fallback = PixelBitmapFont.Default),
            paddingRight = paddingRight,
        )
    }

    private fun resolveStyle(context: BuildContext): PixelTextStyle {
        val themedStyle = if (style == PixelTextStyle.Default) PixelTheme.of(context).textStyle else style
        return if (color != null) themedStyle.copy(color = color) else themedStyle
    }
}

/**
 * 富文本 render object widget。
 */
internal data class RichTextWidget(
    val spans: List<PixelTextSpan>,
    val softWrap: Boolean,
    val maxLines: Int,
    val overflow: PixelTextOverflow,
    val textAlign: TextAlign,
    override val key: Any? = null,
) : RenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderRichText(
            spans = resolveSpans(context),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
            defaultTextRasterizer = DefaultTextRasterizer.of(context, fallback = PixelBitmapFont.Default),
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderRichText).updateRichText(
            spans = resolveSpans(context),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
            defaultTextRasterizer = DefaultTextRasterizer.of(context, fallback = PixelBitmapFont.Default),
        )
    }

    private fun resolveSpans(context: BuildContext): List<PixelTextSpan> {
        val themeStyle = PixelTheme.of(context).textStyle
        return spans.map { span ->
            if (span.style == PixelTextStyle.Default) span.copy(style = themeStyle) else span
        }
    }
}
