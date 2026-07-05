package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsNode
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.TextDirection
import kotlin.math.min

/**
 * 新渲染管线里的文本对象。
 */
internal class RenderText(
    private var text: String,
    private var style: PixelTextStyle,
    private var textAlign: PixelTextAlign,
    private var textDirection: TextDirection,
    private var softWrap: Boolean,
    private var overflow: PixelTextOverflow,
    private var maxLines: Int,
    private var defaultTextRasterizer: PixelTextRasterizer,
    private val explicitWidth: Int? = null,
    private val explicitHeight: Int? = null,
    private val occupyFullWidth: Boolean = false,
    private val fillMaxWidth: Boolean = false,
    private val fillMaxHeight: Boolean = false,
    private var paddingLeft: Int = 0,
    private var paddingTop: Int = 0,
    private var paddingRight: Int = 0,
    private var paddingBottom: Int = 0,
    private val onClick: (() -> Unit)? = null,
) : RenderBox() {
    private var rasterizer: PixelTextRasterizer = style.textRasterizer ?: defaultTextRasterizer
    private var textWidth = 0
    private var textHeight = 0
    private var paragraphLayout: PixelParagraphLayout = PixelParagraphLayout(lines = emptyList())
    private var displayText = text
    private var drawTextX = 0
    private var drawTextY = 0

    fun textRangeRects(start: Int, end: Int): List<PixelTextRangeRect> {
        if (text.isEmpty() || paragraphLayout.lines.isEmpty()) return emptyList()
        val safeStart = start.coerceIn(0, text.length)
        val safeEnd = end.coerceIn(safeStart, text.length)
        if (safeStart >= safeEnd) return emptyList()
        val rects = mutableListOf<PixelTextRangeRect>()
        var y = drawTextY
        paragraphLayout.lines.forEach { line ->
            val lineStart = line.sourceStart.coerceIn(0, text.length)
            val lineEnd = line.sourceEnd.coerceIn(lineStart, text.length)
            val overlapStart = safeStart.coerceAtLeast(lineStart)
            val overlapEnd = safeEnd.coerceAtMost(lineEnd)
            if (overlapStart < overlapEnd) {
                val lineX = lineStartX(line.width)
                val startX = lineX + measureTextRange(lineStart, overlapStart)
                val endX = lineX + measureTextRange(lineStart, overlapEnd)
                rects += PixelTextRangeRect(
                    x = startX,
                    y = y,
                    width = (endX - startX).coerceAtLeast(1),
                    height = line.height,
                )
            }
            y += line.height
        }
        return rects
    }

    fun caretRect(index: Int): PixelTextRangeRect {
        if (text.isEmpty() || paragraphLayout.lines.isEmpty()) {
            return PixelTextRangeRect(x = drawTextX, y = drawTextY, width = 1, height = size.height.coerceAtLeast(1))
        }
        val caretIndex = index.coerceIn(0, text.length)
        var y = drawTextY
        paragraphLayout.lines.forEach { line ->
            val lineStart = line.sourceStart.coerceIn(0, text.length)
            val lineEnd = line.sourceEnd.coerceIn(lineStart, text.length)
            if (caretIndex in lineStart..lineEnd) {
                val lineX = lineStartX(line.width)
                return PixelTextRangeRect(
                    x = lineX + measureTextRange(lineStart, caretIndex.coerceAtMost(lineEnd)),
                    y = y,
                    width = 1,
                    height = line.height,
                )
            }
            y += line.height
        }
        val lastLine = paragraphLayout.lines.last()
        return PixelTextRangeRect(
            x = lineStartX(lastLine.width) + lastLine.width,
            y = (textHeight - lastLine.height).coerceAtLeast(0) + drawTextY,
            width = 1,
            height = lastLine.height,
        )
    }

    fun textInputCaretRect(backingText: String, selectionStart: Int): PixelTextRangeRect {
        if (backingText.isEmpty()) {
            return if (textAlign == PixelTextAlign.END) visibleTextEndCaretRect() else caretRect(0)
        }
        if (textAlign == PixelTextAlign.END && selectionStart >= backingText.length) {
            return visibleTextEndCaretRect()
        }
        return caretRect(selectionStart)
    }

    fun textIndexAt(localX: Int, localY: Int): Int {
        if (text.isEmpty() || paragraphLayout.lines.isEmpty()) return 0
        val lineIndex = lineIndexAt(localY)
        val line = paragraphLayout.lines[lineIndex]
        val lineStart = line.sourceStart.coerceIn(0, text.length)
        val lineEnd = line.sourceEnd.coerceIn(lineStart, text.length)
        if (lineStart >= lineEnd) return lineStart
        val lineX = lineStartX(line.width)
        val xInLine = (localX - lineX).coerceAtLeast(0)
        if (xInLine <= 0) return lineStart
        if (xInLine >= line.width) return lineEnd
        var index = lineStart
        while (index < lineEnd) {
            val left = measureTextRange(lineStart, index)
            val right = measureTextRange(lineStart, index + 1)
            val midpoint = left + ((right - left).coerceAtLeast(1) / 2)
            if (xInLine < midpoint) return index
            index += 1
        }
        return lineEnd
    }

    fun updateText(
        text: String,
        style: PixelTextStyle,
        textAlign: PixelTextAlign,
        textDirection: TextDirection,
        softWrap: Boolean,
        overflow: PixelTextOverflow,
        maxLines: Int,
        defaultTextRasterizer: PixelTextRasterizer = this.defaultTextRasterizer,
        paddingLeft: Int = this.paddingLeft,
        paddingTop: Int = this.paddingTop,
        paddingRight: Int = this.paddingRight,
        paddingBottom: Int = this.paddingBottom,
    ) {
        if (
            this.text == text &&
            this.style == style &&
            this.textAlign == textAlign &&
            this.textDirection == textDirection &&
            this.softWrap == softWrap &&
            this.overflow == overflow &&
            this.maxLines == maxLines &&
            this.defaultTextRasterizer === defaultTextRasterizer &&
            this.paddingLeft == paddingLeft &&
            this.paddingTop == paddingTop &&
            this.paddingRight == paddingRight &&
            this.paddingBottom == paddingBottom
        ) {
            return
        }
        this.text = text
        this.style = style
        this.textAlign = textAlign
        this.textDirection = textDirection
        this.softWrap = softWrap
        this.overflow = overflow
        this.maxLines = maxLines
        this.defaultTextRasterizer = defaultTextRasterizer
        this.paddingLeft = paddingLeft
        this.paddingTop = paddingTop
        this.paddingRight = paddingRight
        this.paddingBottom = paddingBottom
        markNeedsLayout()
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        rasterizer = style.textRasterizer ?: defaultTextRasterizer

        val horizontalPadding = paddingLeft + paddingRight
        val verticalPadding = paddingTop + paddingBottom
        val availableTextWidth = resolveAvailableTextWidth(
            constraints = constraints,
            horizontalPadding = horizontalPadding,
        )
        paragraphLayout = PixelParagraphLayouter.layout(
            input = PixelParagraphInput(
                spans = listOf(PixelTextSpan(text = text, style = style)),
                textAlign = textAlign,
                textDirection = textDirection,
                softWrap = softWrap,
                overflow = overflow,
                maxLines = maxLines,
                defaultTextRasterizer = rasterizer,
            ),
            availableWidth = availableTextWidth,
        )
        textWidth = paragraphLayout.width
        textHeight = paragraphLayout.height
        displayText = paragraphLayout.lines.joinToString(separator = "\n") { line ->
            line.runs.joinToString(separator = "") { run -> run.text }
        }

        val measuredWidth = when {
            explicitWidth != null -> explicitWidth
            fillMaxWidth || occupyFullWidth -> constraints.maxWidth
            else -> textWidth + horizontalPadding
        }
        val measuredHeight = when {
            explicitHeight != null -> explicitHeight
            fillMaxHeight -> constraints.maxHeight
            else -> textHeight + verticalPadding
        }

        size = RenderSize(
            width = constraints.constrainWidth(measuredWidth),
            height = constraints.constrainHeight(measuredHeight),
        )

        val contentWidth = (size.width - horizontalPadding).coerceAtLeast(0)
        drawTextX = paddingLeft + ParagraphLayoutSupport.resolveLineStartX(
            textAlign = textAlign,
            textDirection = textDirection,
            availableWidth = contentWidth,
            lineWidth = textWidth,
        )
        drawTextY = paddingTop
    }

    override fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        if (paragraphLayout.lines.isEmpty()) return
        val contentWidth = (size.width - paddingLeft - paddingRight).coerceAtLeast(0)
        val contentHeight = (size.height - paddingTop - paddingBottom).coerceAtLeast(0)
        if (contentWidth == 0 || contentHeight == 0) return

        val destinationY = offsetY + drawTextY
        val destX = if (style.usesPlainRasterizer()) offsetX + drawTextX else offsetX + paddingLeft

        if (textWidth <= contentWidth && textHeight <= contentHeight) {
            // Fast path: text fits — draw directly into the main buffer.
            drawTextLayout(buffer = context.buffer, x = destX, y = destinationY)
            return
        }

        // Slow path: text needs cropping — use a scratch buffer.
        val scratch = context.bufferPool.acquire(
            width = textWidth.coerceAtLeast(1),
            height = textHeight.coerceAtLeast(1),
        )
        try {
            drawTextLayout(buffer = scratch, x = 0, y = 0)
            blitText(
                source = scratch,
                destination = context.buffer,
                destX = offsetX + drawTextX,
                destY = destinationY,
                copyWidth = min(contentWidth, scratch.width),
                copyHeight = min(contentHeight, scratch.height),
            )
        } finally {
            context.bufferPool.release(scratch)
        }
    }

    private fun drawTextLayout(buffer: PixelBuffer, x: Int, y: Int) {
        val textColor = style.color
        if (style.usesPlainRasterizer()) {
            rasterizer.drawText(buffer = buffer, text = displayText, x = x, y = y, color = textColor)
            return
        }
        var cursorY = y
        paragraphLayout.lines.forEach { line ->
            var cursorX = x + ParagraphLayoutSupport.resolveLineStartX(
                textAlign = textAlign,
                textDirection = textDirection,
                availableWidth = size.width - paddingLeft - paddingRight,
                lineWidth = line.width,
            )
            line.runs.forEach { run ->
                PixelParagraphPainter.drawRun(
                    buffer = buffer,
                    run = run,
                    defaultTextRasterizer = rasterizer,
                    x = cursorX,
                    y = cursorY,
                )
                cursorX += run.width
            }
            cursorY += line.height
        }
    }

    private fun lineIndexAt(localY: Int): Int {
        val lines = paragraphLayout.lines
        if (lines.isEmpty()) return 0
        val yInText = (localY - drawTextY).coerceAtLeast(0)
        var cursorY = 0
        lines.forEachIndexed { index, line ->
            val nextY = cursorY + line.height
            if (yInText < nextY || index == lines.lastIndex) return index
            cursorY = nextY
        }
        return lines.lastIndex
    }

    private fun visibleTextEndCaretRect(): PixelTextRangeRect {
        val caret = caretRect(text.length)
        val minX = paddingLeft.coerceAtLeast(0)
        val maxX = (size.width - caret.width).coerceAtLeast(minX)
        return caret.copy(x = maxX)
    }

    private fun lineStartX(lineWidth: Int): Int {
        if (style.usesPlainRasterizer()) {
            return drawTextX
        }
        return paddingLeft + ParagraphLayoutSupport.resolveLineStartX(
            textAlign = textAlign,
            textDirection = textDirection,
            availableWidth = size.width - paddingLeft - paddingRight,
            lineWidth = lineWidth,
        )
    }

    private fun PixelTextStyle.usesPlainRasterizer(): Boolean {
        return letterSpacing <= 0 && fontScale <= 1 && lineHeight == null && lineSpacing <= 0
    }

    private fun measureTextRange(start: Int, end: Int): Int {
        if (start >= end) return 0
        val slice = text.substring(start.coerceIn(0, text.length), end.coerceIn(start, text.length))
        return if (style.usesPlainRasterizer()) {
            rasterizer.measureText(slice)
        } else {
            val scale = style.fontScale.coerceAtLeast(1)
            val spacing = style.letterSpacing.coerceAtLeast(0)
            slice.sumOf { character ->
                (rasterizer.measureText(character.toString()) * scale) + spacing
            }
        }
    }

    /**
     * 只把 scratch buffer 里非透明像素拷到目标 buffer，避免覆盖底色。
     */
    private fun blitText(
        source: PixelBuffer,
        destination: PixelBuffer,
        destX: Int,
        destY: Int,
        copyWidth: Int,
        copyHeight: Int,
    ) {
        for (row in 0 until copyHeight) {
            for (column in 0 until copyWidth) {
                val pixel = source.getPixel(column, row)
                if (pixel.alpha > 0) {
                    destination.setPixel(x = destX + column, y = destY + row, color = pixel)
                }
            }
        }
    }

    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        if (localX !in 0 until size.width || localY !in 0 until size.height) return
        if (onClick != null) result.add(this)
    }

    override fun collectClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ) {
        onClick ?: return
        targets += PixelClickTarget(
            bounds = PixelRect(left = offsetX, top = offsetY, width = size.width, height = size.height),
            onClick = onClick,
            source = this,
        )
    }

    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        if (text.isBlank()) return
        targets += PixelSemanticsTarget(
            node = PixelSemanticsNode(
                label = text,
                role = PixelSemanticRole.TEXT,
                enabled = true,
                focused = false,
                left = offsetX,
                top = offsetY,
                width = size.width,
                height = size.height,
            ),
            source = this,
        )
    }

    private fun resolveAvailableTextWidth(constraints: RenderConstraints, horizontalPadding: Int): Int {
        val measuredWidth = when {
            explicitWidth != null -> explicitWidth
            fillMaxWidth || occupyFullWidth -> constraints.maxWidth
            else -> constraints.maxWidth
        }
        return (constraints.constrainWidth(measuredWidth) - horizontalPadding).coerceAtLeast(0)
    }
}

internal data class PixelTextRangeRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)
