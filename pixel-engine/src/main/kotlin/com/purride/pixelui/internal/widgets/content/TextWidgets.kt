package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.DefaultTextRasterizer
import com.purride.pixelui.Directionality
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.PixelTextStyle
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
    override val key: Any? = null,
) : RenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderText(
            text = data,
            style = resolveStyle(),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
            defaultTextRasterizer = DefaultTextRasterizer.of(context, fallback = PixelBitmapFont.Default),
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderText).updateText(
            text = data,
            style = resolveStyle(),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
            defaultTextRasterizer = DefaultTextRasterizer.of(context, fallback = PixelBitmapFont.Default),
        )
    }

    private fun resolveStyle(): PixelTextStyle {
        return if (color != null) style.copy(color = color) else style
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
            spans = spans,
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
            spans = spans,
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
            defaultTextRasterizer = DefaultTextRasterizer.of(context, fallback = PixelBitmapFont.Default),
        )
    }
}
