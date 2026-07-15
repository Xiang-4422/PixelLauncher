package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.TextDirection

/**
 * 新渲染管线里的富文本对象。
 */
public class RenderRichText(
    /** Ordered styled spans whose text concatenation forms the exact backing paragraph. */
    private var spans: List<PixelTextSpan>,
    /** Logical alignment applied independently to each visible line. */
    private var textAlign: PixelTextAlign,
    /** Explicit paragraph base direction used by Unicode Bidi resolution. */
    private var textDirection: TextDirection,
    /** Whether wrapping may occur between complete grapheme clusters. */
    private var softWrap: Boolean,
    /** Whole-cluster clipping or ellipsis policy. */
    private var overflow: PixelTextOverflow,
    /** Maximum number of visible lines. */
    private var maxLines: Int,
    /** Inherited rasterizer for spans without an explicit style override. */
    private var defaultTextRasterizer: PixelTextRasterizer,
) : RenderBox() {
    /** Shared immutable cluster/Bidi geometry for the current layout. */
    private var paragraphLayout: PixelParagraphLayout = PixelParagraphLayout(lines = emptyList())

    /** 更新 `RenderRichText` 的 `updateRichText` 状态并保持派生数据一致。
 *
 * Replaces rich-text inputs and invalidates layout only when a value actually changes.
 */
    public fun updateRichText(
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
            this.defaultTextRasterizer == defaultTextRasterizer
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

    /** Builds styled cluster/Bidi geometry and resolves the constrained paragraph size. */
    override fun layout(constraints: RenderConstraints) {
        /** Non-negative width available to paragraph layout. */
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

    /** Paints style-homogeneous visual runs using paragraph-owned cluster positions. */
    override fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        /** Destination buffer supplied by the current pipeline frame. */
        val buffer = context.buffer
        /** Absolute top edge of the line currently being painted. */
        var cursorY = offsetY
        /** Horizontal clipping extent of this render box. */
        val contentWidth = size.width
        /** Vertical clipping extent of this render box. */
        val contentHeight = size.height
        paragraphLayout.lines.forEach { line ->
            if (cursorY - offsetY >= contentHeight) return
            /** Absolute left edge of the next visual run on the aligned line. */
            var cursorX = offsetX + ParagraphLayoutSupport.resolveLineStartX(
                textAlign = textAlign,
                textDirection = textDirection,
                availableWidth = contentWidth,
                lineWidth = line.width,
            )
            line.runs.forEach { run ->
                if (cursorX - offsetX >= contentWidth) return@forEach
                PixelParagraphPainter.drawRun(
                    buffer = buffer,
                    bufferPool = context.bufferPool,
                    run = run,
                    defaultTextRasterizer = defaultTextRasterizer,
                    x = cursorX,
                    y = cursorY,
                )
                cursorX += run.width
            }
            cursorY += line.height
        }
    }
}
