package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.DefaultTextRasterizer
import com.purride.pixelui.Directionality
import com.purride.pixelui.HostCapabilities
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.TextAlign
import com.purride.pixelui.internal.toPixelTextAlign

/**
 * 从继承的主题 token 图解析默认正文文本样式。
 *
 * Resolves the inherited body typography role into a concrete [PixelTextStyle].
 */
private fun resolveBodyTextStyle(context: BuildContext): PixelTextStyle {
    /** 最近的完整 token 图，缺少提供者时回落到默认主题。 */
    val tokens = PixelTheme.of(context)
    return tokens.typography.body.resolve(tokens.colors)
}

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
        /** Host-scaled inherited rasterizer shared by layout and paint. */
        val defaultRasterizer = DefaultTextRasterizer.of(context, fallback = PixelBitmapFont.Default)
            .withHostTextScale(HostCapabilities.of(context).textScaleFactor)
        return RenderText(
            text = data,
            style = resolveStyle(context),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
            defaultTextRasterizer = defaultRasterizer,
            paddingRight = paddingRight,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        /** Host-scaled inherited rasterizer refreshed after capability dependency changes. */
        val defaultRasterizer = DefaultTextRasterizer.of(context, fallback = PixelBitmapFont.Default)
            .withHostTextScale(HostCapabilities.of(context).textScaleFactor)
        (renderObject as RenderText).updateText(
            text = data,
            style = resolveStyle(context),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
            defaultTextRasterizer = defaultRasterizer,
            paddingRight = paddingRight,
        )
    }

    private fun resolveStyle(context: BuildContext): PixelTextStyle {
        /** Complete Host scale read through the inherited dependency boundary. */
        val textScaleFactor = HostCapabilities.of(context).textScaleFactor
        /** Theme or explicit style before environment scaling. */
        val themedStyle = if (style == PixelTextStyle.Default) resolveBodyTextStyle(context) else style
        /** Explicit Text color applied before immutable metric/rasterizer scaling. */
        val coloredStyle = if (color != null) themedStyle.copy(color = color) else themedStyle
        return coloredStyle.withHostTextScale(textScaleFactor)
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
        /** Host-scaled inherited rasterizer shared by every span without an override. */
        val defaultRasterizer = DefaultTextRasterizer.of(context, fallback = PixelBitmapFont.Default)
            .withHostTextScale(HostCapabilities.of(context).textScaleFactor)
        return RenderRichText(
            spans = resolveSpans(context),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
            defaultTextRasterizer = defaultRasterizer,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        /** Host-scaled inherited rasterizer refreshed after capability dependency changes. */
        val defaultRasterizer = DefaultTextRasterizer.of(context, fallback = PixelBitmapFont.Default)
            .withHostTextScale(HostCapabilities.of(context).textScaleFactor)
        (renderObject as RenderRichText).updateRichText(
            spans = resolveSpans(context),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
            defaultTextRasterizer = defaultRasterizer,
        )
    }

    private fun resolveSpans(context: BuildContext): List<PixelTextSpan> {
        /** Complete Host scale read through the inherited dependency boundary. */
        val textScaleFactor = HostCapabilities.of(context).textScaleFactor
        /** Theme style substituted only for spans using the public default sentinel. */
        val themeStyle = resolveBodyTextStyle(context)
        return spans.map { span ->
            /** Concrete span style before Host scaling. */
            val resolvedStyle = if (span.style == PixelTextStyle.Default) themeStyle else span.style
            span.copy(style = resolvedStyle.withHostTextScale(textScaleFactor))
        }
    }
}
