package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelui.BuildContext
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
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderText(
            text = data,
            style = resolveTextStyle(context),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
            defaultTextRasterizer = PixelBitmapFont.Default,
        )
    }

    /**
     * 把新的 widget 配置同步到既有文本 render object。
     */
    override fun updateRenderObject(
        context: BuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderText).updateText(
            text = data,
            style = resolveTextStyle(context),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
        )
    }

    /**
     * 解析当前上下文下的最终文本样式。
     */
    private fun resolveTextStyle(context: BuildContext): PixelTextStyle {
        val resolvedTheme = context.resolveTheme(theme)
        return when (style) {
            PixelTextStyle.Default -> resolvedTheme.resolveTextStyle()
            PixelTextStyle.Accent -> resolvedTheme.resolveAccentTextStyle()
            else -> style
        }
    }
}

/**
 * 富文本 render object widget。
 */
internal data class RichTextWidget(
    val spans: List<PixelTextSpan>,
    val theme: com.purride.pixelui.PixelThemeData?,
    val softWrap: Boolean,
    val maxLines: Int,
    val overflow: PixelTextOverflow,
    val textAlign: TextAlign,
    override val key: Any? = null,
) : RenderObjectWidget(
    key = key,
) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderRichText(
            spans = resolveSpans(context),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
            defaultTextRasterizer = PixelBitmapFont.Default,
        )
    }

    override fun updateRenderObject(
        context: BuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderRichText).updateRichText(
            spans = resolveSpans(context),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            softWrap = softWrap,
            overflow = overflow,
            maxLines = maxLines,
        )
    }

    private fun resolveSpans(context: BuildContext): List<PixelTextSpan> {
        val resolvedTheme = context.resolveTheme(theme)
        return spans.map { span ->
            span.copy(
                style = when (span.style) {
                    PixelTextStyle.Default -> resolvedTheme.resolveTextStyle()
                    PixelTextStyle.Accent -> resolvedTheme.resolveAccentTextStyle()
                    else -> span.style
                },
            )
        }
    }
}
