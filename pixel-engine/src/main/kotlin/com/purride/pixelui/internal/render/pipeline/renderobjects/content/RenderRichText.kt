package com.purride.pixelui.internal

import com.purride.pixelcore.MonoPixelBuffer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.TextDirection

/**
 * 新渲染管线里的富文本对象。
 *
 * 第一版采用字符级换行和 span 样式切换，保持与 `RenderText` 的基础行为一致。
 */
internal class RenderRichText(
    private var spans: List<PixelTextSpan>,
    private var textAlign: PixelTextAlign,
    private var textDirection: TextDirection,
    private var softWrap: Boolean,
    private var overflow: PixelTextOverflow,
    private var maxLines: Int,
    private var defaultTextRasterizer: PixelTextRasterizer,
) : RenderBox() {
    private var paragraphLayout: PixelParagraphLayout = PixelParagraphLayout(lines = emptyList())

    fun updateRichText(
        spans: List<PixelTextSpan>,
        textAlign: PixelTextAlign,
        textDirection: TextDirection,
        softWrap: Boolean,
        overflow: PixelTextOverflow,
        maxLines: Int,
        defaultTextRasterizer: PixelTextRasterizer = this.defaultTextRasterizer,
    ) {
        if (
            this.spans == spans &&
            this.textAlign == textAlign &&
            this.textDirection == textDirection &&
            this.softWrap == softWrap &&
            this.overflow == overflow &&
            this.maxLines == maxLines &&
            this.defaultTextRasterizer === defaultTextRasterizer
        ) {
            return
        }
        this.spans = spans
        this.textAlign = textAlign
        this.textDirection = textDirection
        this.softWrap = softWrap
        this.overflow = overflow
        this.maxLines = maxLines
        this.defaultTextRasterizer = defaultTextRasterizer
        markNeedsLayout()
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        val availableWidth = constraints.maxWidth.coerceAtLeast(0)
        paragraphLayout = PixelParagraphLayouter.layout(
            input = PixelParagraphInput(
                spans = spans,
                textAlign = textAlign,
                textDirection = textDirection,
                softWrap = softWrap,
                overflow = overflow,
                maxLines = maxLines,
                defaultTextRasterizer = defaultTextRasterizer,
            ),
            availableWidth = availableWidth,
        )
        size = RenderSize(
            width = constraints.constrainWidth(paragraphLayout.width),
            height = constraints.constrainHeight(paragraphLayout.height),
        )
    }

    override fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        val monoBuffer = context.buffer as MonoPixelBuffer
        var cursorY = offsetY
        val contentWidth = size.width
        val contentHeight = size.height
        paragraphLayout.lines.forEach { line ->
            if (cursorY - offsetY >= contentHeight) {
                return
            }
            var cursorX = offsetX + ParagraphLayoutSupport.resolveLineStartX(
                textAlign = textAlign,
                textDirection = textDirection,
                availableWidth = contentWidth,
                lineWidth = line.width,
            )
            line.runs.forEach { run ->
                if (cursorX - offsetX >= contentWidth) {
                    return@forEach
                }
                if (run.style.letterSpacing > 0 || run.style.fontScale > 1 || run.style.lineHeight != null) {
                    PixelParagraphPainter.drawRun(
                        buffer = monoBuffer,
                        run = run,
                        defaultTextRasterizer = defaultTextRasterizer,
                        x = cursorX,
                        y = cursorY,
                    )
                } else {
                    val rasterizer = run.style.textRasterizer ?: defaultTextRasterizer
                    run.text.forEach { character ->
                        val text = character.toString()
                        rasterizer.drawText(
                            buffer = monoBuffer,
                            text = text,
                            x = cursorX,
                            y = cursorY,
                            value = run.style.tone.value,
                        )
                        cursorX += rasterizer.measureText(text)
                    }
                    return@forEach
                }
                cursorX += run.width
            }
            cursorY += line.height
        }
    }
}
