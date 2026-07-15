package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelBufferPool
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelGraphemeBoundaryMap
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsActions
import com.purride.pixelui.PixelSemanticsNode
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.TextDirection
import kotlin.math.min

/**
 * 新渲染管线里的文本对象。
 */
public class RenderText(
    /** Exact non-normalized backing text represented by this render object. */
    private var text: String,
    /** Current style applied to every grapheme in this plain-text object. */
    private var style: PixelTextStyle,
    /** Logical per-line alignment. */
    private var textAlign: PixelTextAlign,
    /** Explicit paragraph base direction. */
    private var textDirection: TextDirection,
    /** Whether layout may wrap between complete grapheme clusters. */
    private var softWrap: Boolean,
    /** Whole-cluster clipping or ellipsis policy. */
    private var overflow: PixelTextOverflow,
    /** Maximum number of visible paragraph lines. */
    private var maxLines: Int,
    /** Inherited rasterizer used when [style] has no explicit rasterizer. */
    private var defaultTextRasterizer: PixelTextRasterizer,
    /** Optional fixed outer width. */
    private val explicitWidth: Int? = null,
    /** Optional fixed outer height. */
    private val explicitHeight: Int? = null,
    /** Whether legacy Text should occupy the full available width. */
    private val occupyFullWidth: Boolean = false,
    /** Whether constraints should force maximum width. */
    private val fillMaxWidth: Boolean = false,
    /** Whether constraints should force maximum height. */
    private val fillMaxHeight: Boolean = false,
    /** Logical left content padding. */
    private var paddingLeft: Int = 0,
    /** Logical top content padding. */
    private var paddingTop: Int = 0,
    /** Logical right content padding. */
    private var paddingRight: Int = 0,
    /** Logical bottom content padding. */
    private var paddingBottom: Int = 0,
    /** Optional direct activation callback exported to hit testing and semantics. */
    private val onClick: (() -> Unit)? = null,
) : RenderBox() {
    /** Rasterizer selected from [style] and [defaultTextRasterizer] for the current frame. */
    private var rasterizer: PixelTextRasterizer = style.textRasterizer ?: defaultTextRasterizer
    /** Maximum laid-out visual line width before outer padding. */
    private var textWidth = 0
    /** Sum of laid-out visible line heights before outer padding. */
    private var textHeight = 0
    /** Shared immutable cluster/Bidi geometry for paint, input and Accessibility. */
    private var paragraphLayout: PixelParagraphLayout = PixelParagraphLayout(lines = emptyList())
    /** Compatibility paint payload reconstructed from visible paragraph runs. */
    private var displayText = text
    /** Local horizontal origin of the aligned paragraph. */
    private var drawTextX = 0
    /** Local vertical origin of the paragraph. */
    private var drawTextY = 0

    /** 执行 `RenderText` 的 `textRangeRects` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns one or more visual rectangles for a logical grapheme-safe UTF-16 range.
 */
    public fun textRangeRects(start: Int, end: Int): List<PixelTextRangeRect> {
        if (text.isEmpty() || paragraphLayout.lines.isEmpty()) return emptyList()
        /** Boundary authority expanding callers that arrive inside a cluster. */
        val boundaries = PixelGraphemeBoundaryMap(text)
        /** Stable outward-normalized selection. */
        val normalized = boundaries.expand(start, end)
        /** Inclusive logical selection boundary. */
        val safeStart = normalized.start
        /** Exclusive logical selection boundary. */
        val safeEnd = normalized.end
        if (safeStart >= safeEnd) return emptyList()
        /** Visual rectangles emitted in line order and left-to-right segment order. */
        val rects = mutableListOf<PixelTextRangeRect>()
        /** Top edge of the current line in local text coordinates. */
        var y = drawTextY
        paragraphLayout.lines.forEach { line ->
            /** Selected backing clusters in final visual order; generated ellipsis has no source range. */
            val selected = line.visualClusters.filter { cluster ->
                !cluster.isSynthetic &&
                    cluster.sourceStart < safeEnd &&
                    cluster.sourceEnd > safeStart
            }
            if (selected.isNotEmpty()) {
                /** Local x origin after line alignment. */
                val lineX = lineStartX(line.width)
                /** Left edge of the contiguous visual segment being accumulated. */
                var segmentStart = selected.first().visualX
                /** Exclusive right edge of the contiguous visual segment being accumulated. */
                var segmentEnd = selected.first().visualX + selected.first().width
                selected.drop(1).forEach { cluster ->
                    if (cluster.visualX <= segmentEnd) {
                        segmentEnd = maxOf(segmentEnd, cluster.visualX + cluster.width)
                    } else {
                        rects += PixelTextRangeRect(
                            x = lineX + segmentStart,
                            y = y,
                            width = (segmentEnd - segmentStart).coerceAtLeast(1),
                            height = line.height,
                        )
                        segmentStart = cluster.visualX
                        segmentEnd = cluster.visualX + cluster.width
                    }
                }
                rects += PixelTextRangeRect(
                    x = lineX + segmentStart,
                    y = y,
                    width = (segmentEnd - segmentStart).coerceAtLeast(1),
                    height = line.height,
                )
            }
            y += line.height
        }
        return rects
    }

    /** 执行 `RenderText` 的 `caretRect` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the requested visual caret for one logical UTF-16 index.
 */
    public fun caretRect(
        index: Int,
        affinity: PixelTextAffinity = PixelTextAffinity.DOWNSTREAM,
    ): PixelTextRangeRect {
        return resolveCaretRects(index = index, affinity = affinity).first()
    }

    /**
 * 执行 `RenderText` 的 `caretRects` 公开行为；具体参数、返回和副作用见下文。
 *
     * Returns all visually distinct carets for a logical boundary.
     *
     * A mixed-direction boundary can have upstream and downstream x positions. The downstream
     * candidate is returned first so [caretRect] preserves the editing layer's documented logical
     * downstream affinity while tests and selection geometry can inspect the dual-caret mapping.
     */
    public fun caretRects(index: Int): List<PixelTextRangeRect> {
        return resolveCaretRects(index = index, affinity = PixelTextAffinity.DOWNSTREAM)
    }

    /**
 * 执行 `RenderText` 的 `textCharacterRects` 公开行为；具体参数、返回和副作用见下文。
 *
     * Maps every requested UTF-16 code unit to its complete visible grapheme rectangle.
     *
     * All code units inside one combining sequence or surrogate-backed grapheme intentionally
     * share one rectangle. Hard line breaks use the upstream line-edge caret, while source text
     * removed by max-lines or ellipsis returns `null` so Android can report it as off screen.
     */
    public fun textCharacterRects(start: Int, length: Int): List<PixelTextRangeRect?> {
        if (length <= 0) return emptyList()
        /** One result slot per Android-compatible requested UTF-16 code unit. */
        val rectangles = MutableList<PixelTextRangeRect?>(length) { null }
        if (text.isEmpty() || start !in text.indices || paragraphLayout.lines.isEmpty()) {
            return rectangles
        }
        /** Exclusive request edge capped without overflowing Int arithmetic. */
        val requestedEnd = minOf(text.length.toLong(), start.toLong() + length.toLong()).toInt()
        /** Top edge of the current line in RenderText-local coordinates. */
        var lineY = drawTextY
        paragraphLayout.lines.forEach { line ->
            /** Aligned x origin shared with paint, caret, hit-test, and range selection. */
            val lineX = lineStartX(line.width)
            line.visualClusters.forEach { cluster ->
                if (cluster.isSynthetic || cluster.sourceEnd <= start || cluster.sourceStart >= requestedEnd) {
                    return@forEach
                }
                /** Atomic visible rectangle shared by every UTF-16 code unit in this cluster. */
                val clusterRect = PixelTextRangeRect(
                    x = lineX + cluster.visualX,
                    y = lineY,
                    width = cluster.width.coerceAtLeast(1),
                    height = line.height,
                )
                /** Requested source portion covered by this complete visual cluster. */
                val coveredStart = maxOf(start, cluster.sourceStart)
                /** Exclusive requested source edge covered by this complete visual cluster. */
                val coveredEnd = minOf(requestedEnd, cluster.sourceEnd)
                for (offset in coveredStart until coveredEnd) {
                    rectangles[offset - start] = clusterRect
                }
            }
            lineY += line.height
        }
        /** Fixed Unicode boundary map used only to identify non-painted hard-break graphemes. */
        val boundaries = PixelGraphemeBoundaryMap(text)
        for (offset in start until requestedEnd) {
            /** Result index corresponding to the current backing UTF-16 code unit. */
            val resultIndex = offset - start
            if (rectangles[resultIndex] != null) continue
            /** Leading boundary shared by both code units of a CRLF grapheme. */
            val clusterStart = boundaries.floor(offset)
            /** Trailing boundary of the grapheme containing the requested code unit. */
            val clusterEnd = boundaries.ceil(offset + 1)
            /** Whether the upstream physical line survived max-lines and ellipsis truncation. */
            val upstreamLineIsVisible = paragraphLayout.lines.any { line -> line.sourceEnd == clusterStart }
            if (
                upstreamLineIsVisible &&
                PixelParagraphClusterSupport.isHardLineBreak(text.substring(clusterStart, clusterEnd))
            ) {
                rectangles[resultIndex] = caretRect(clusterStart, PixelTextAffinity.UPSTREAM)
            }
        }
        return rectangles
    }

    /**
 * 执行 `RenderText` 的 `textCharacterRects` 公开行为；具体参数、返回和副作用见下文。
 *
     * Resolves character rectangles only when this object paints the exact editable backing text.
     *
     * TextField may paint placeholder text while its semantic value is empty; rejecting that
     * mismatch prevents placeholder glyphs from being exposed as editable character locations.
     */
    public fun textCharacterRects(
        backingText: String,
        start: Int,
        length: Int,
    ): List<PixelTextRangeRect?> {
        return if (backingText == text) {
            textCharacterRects(start = start, length = length)
        } else {
            MutableList(length.coerceAtLeast(0)) { null }
        }
    }

    /** Resolves carets on the line selected by [affinity] and orders that affinity first. */
    private fun resolveCaretRects(
        index: Int,
        affinity: PixelTextAffinity,
    ): List<PixelTextRangeRect> {
        if (text.isEmpty() || paragraphLayout.lines.isEmpty()) {
            return listOf(
                PixelTextRangeRect(
                    x = drawTextX,
                    y = drawTextY,
                    width = 1,
                    height = size.height.coerceAtLeast(1),
                ),
            )
        }
        /** Grapheme boundary nearest the requested legacy or platform offset. */
        val caretIndex = PixelGraphemeBoundaryMap(text).nearest(index)
        /** Exact soft/hard line chosen from the requested logical side of the boundary. */
        val exactAffinityLine = when (affinity) {
            PixelTextAffinity.DOWNSTREAM ->
                paragraphLayout.lines.indexOfLast { line -> line.sourceStart == caretIndex }
            PixelTextAffinity.UPSTREAM ->
                paragraphLayout.lines.indexOfFirst { line -> line.sourceEnd == caretIndex }
        }
        /** Line containing the logical boundary when no affinity-specific line edge exists. */
        val containingLine = paragraphLayout.lines.indexOfFirst { line ->
            caretIndex in line.sourceStart..line.sourceEnd
        }
        /** Selected line index, falling back to the final visible line after ellipsis truncation. */
        val lineIndex = when {
            exactAffinityLine >= 0 -> exactAffinityLine
            containingLine >= 0 -> containingLine
            else -> paragraphLayout.lines.lastIndex
        }
        /** Line supplying Bidi cluster geometry. */
        val line = paragraphLayout.lines[lineIndex]
        /** Local top edge obtained from preceding line heights. */
        val y = drawTextY + paragraphLayout.lines.take(lineIndex).sumOf { item -> item.height }
        /** Aligned x origin of the selected line. */
        val lineX = lineStartX(line.width)
        /** Upstream/downstream visual edges contributed by adjacent logical clusters. */
        val candidates = mutableListOf<TextCaretCandidate>()
        line.visualClusters.forEach { cluster ->
            if (cluster.isSynthetic) return@forEach
            if (cluster.sourceStart == caretIndex) {
                candidates += TextCaretCandidate(
                    x = if (cluster.isRightToLeft) {
                        cluster.visualX + cluster.width
                    } else {
                        cluster.visualX
                    },
                    downstream = true,
                )
            }
            if (cluster.sourceEnd == caretIndex) {
                candidates += TextCaretCandidate(
                    x = if (cluster.isRightToLeft) {
                        cluster.visualX
                    } else {
                        cluster.visualX + cluster.width
                    },
                    downstream = false,
                )
            }
        }
        if (candidates.isEmpty()) {
            /** Empty/truncated line edge selected from paragraph base direction. */
            val fallbackX = if (textDirection == TextDirection.RTL) line.width else 0
            candidates += TextCaretCandidate(
                x = fallbackX,
                downstream = affinity == PixelTextAffinity.DOWNSTREAM,
            )
        }
        return candidates
            .distinctBy { candidate -> candidate.x }
            .sortedByDescending { candidate ->
                candidate.downstream == (affinity == PixelTextAffinity.DOWNSTREAM)
            }
            .map { candidate ->
                PixelTextRangeRect(
                    x = lineX + candidate.x,
                    y = y,
                    width = 1,
                    height = line.height,
                )
            }
    }

    /** 执行 `RenderText` 的 `textInputCaretRect` 公开行为；具体参数、返回和副作用见下文。
 *
 * Resolves a TextField caret without exposing placeholder geometry as editable text.
 */
    public fun textInputCaretRect(backingText: String, selectionStart: Int): PixelTextRangeRect {
        if (backingText.isEmpty()) {
            return if (textAlign == PixelTextAlign.END) visibleTextEndCaretRect() else caretRect(0)
        }
        if (textAlign == PixelTextAlign.END && selectionStart >= backingText.length) {
            return visibleTextEndCaretRect()
        }
        return caretRect(selectionStart)
    }

    /** 执行 `RenderText` 的 `textIndexAt` 公开行为；具体参数、返回和副作用见下文。
 *
 * Maps a visual point to the nearest logical grapheme boundary.
 */
    public fun textIndexAt(localX: Int, localY: Int): Int {
        if (text.isEmpty() || paragraphLayout.lines.isEmpty()) return 0
        /** Physical/soft line containing the requested y coordinate. */
        val lineIndex = lineIndexAt(localY)
        /** Visual cluster geometry for the selected line. */
        val line = paragraphLayout.lines[lineIndex]
        /** Non-zero visual clusters including generated ellipsis endpoints. */
        val clusters = line.visualClusters.filter { cluster -> cluster.width > 0 }
        if (clusters.isEmpty()) return line.sourceStart
        /** Aligned x origin of this exact line. */
        val lineX = lineStartX(line.width)
        /** Point expressed in the line's left-to-right visual coordinate space. */
        val xInLine = (localX - lineX).coerceAtLeast(0)
        clusters.forEach { cluster ->
            /** Exclusive right edge including pair spacing owned by this cluster. */
            val right = cluster.visualX + cluster.width
            if (xInLine < right) {
                /** Midpoint deciding the closest visual edge of the atomic cluster. */
                val midpoint = cluster.visualX + cluster.width / 2
                return if (xInLine < midpoint) {
                    if (cluster.isRightToLeft) cluster.sourceEnd else cluster.sourceStart
                } else {
                    if (cluster.isRightToLeft) cluster.sourceStart else cluster.sourceEnd
                }
            }
        }
        /** Logical boundary represented by the line's rightmost visual edge. */
        val rightmost = clusters.last()
        return if (rightmost.isRightToLeft) rightmost.sourceStart else rightmost.sourceEnd
    }

    /** 更新 `RenderText` 的 `updateText` 状态并保持派生数据一致。
 *
 * Replaces mutable text inputs and invalidates layout only when a value actually changes.
 */
    public fun updateText(
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
            this.defaultTextRasterizer == defaultTextRasterizer &&
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

    /** Builds cluster/Bidi paragraph geometry and resolves this render object's constrained size. */
    override fun layout(constraints: RenderConstraints) {
        rasterizer = style.textRasterizer ?: defaultTextRasterizer

        /** Total horizontal content padding. */
        val horizontalPadding = paddingLeft + paddingRight
        /** Total vertical content padding. */
        val verticalPadding = paddingTop + paddingBottom
        /** Width available to paragraph measurement after constraints and padding. */
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

        /** Requested outer width before final constraint clamping. */
        val measuredWidth = when {
            explicitWidth != null -> explicitWidth
            fillMaxWidth || occupyFullWidth -> constraints.maxWidth
            else -> textWidth + horizontalPadding
        }
        /** Requested outer height before final constraint clamping. */
        val measuredHeight = when {
            explicitHeight != null -> explicitHeight
            fillMaxHeight -> constraints.maxHeight
            else -> textHeight + verticalPadding
        }

        size = RenderSize(
            width = constraints.constrainWidth(measuredWidth),
            height = constraints.constrainHeight(measuredHeight),
        )

        /** Final inner width used by line alignment. */
        val contentWidth = (size.width - horizontalPadding).coerceAtLeast(0)
        drawTextX = paddingLeft + ParagraphLayoutSupport.resolveLineStartX(
            textAlign = textAlign,
            textDirection = textDirection,
            availableWidth = contentWidth,
            lineWidth = textWidth,
        )
        drawTextY = paddingTop
    }

    /** Paints the visible paragraph directly or through a clipped scratch buffer. */
    override fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        if (paragraphLayout.lines.isEmpty()) return
        /** Final horizontal content extent. */
        val contentWidth = (size.width - paddingLeft - paddingRight).coerceAtLeast(0)
        /** Final vertical content extent. */
        val contentHeight = (size.height - paddingTop - paddingBottom).coerceAtLeast(0)
        if (contentWidth == 0 || contentHeight == 0) return

        /** Absolute vertical paragraph origin. */
        val destinationY = offsetY + drawTextY
        /** Absolute horizontal origin; styled runs resolve alignment independently. */
        val destX = if (style.usesPlainRasterizer()) offsetX + drawTextX else offsetX + paddingLeft

        if (textWidth <= contentWidth && textHeight <= contentHeight) {
            // Fast path: text fits — draw directly into the main buffer.
            drawTextLayout(
                buffer = context.buffer,
                bufferPool = context.bufferPool,
                x = destX,
                y = destinationY,
            )
            return
        }

        // Slow path: text needs cropping — use a scratch buffer.
        /** Pooled paragraph-sized buffer used only when outer clipping is required. */
        val scratch = context.bufferPool.acquire(
            width = textWidth.coerceAtLeast(1),
            height = textHeight.coerceAtLeast(1),
        )
        try {
            drawTextLayout(buffer = scratch, bufferPool = context.bufferPool, x = 0, y = 0)
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

    /** 在已经解析的目标原点绘制全部可见段落运行段。 */
    private fun drawTextLayout(
        /** 当前绘制目标。 */
        buffer: PixelBuffer,
        /** 当前帧共享的临时缓冲池。 */
        bufferPool: PixelBufferPool,
        /** 段落绘制原点横坐标。 */
        x: Int,
        /** 段落绘制原点纵坐标。 */
        y: Int,
    ) {
        /** Foreground color of this plain Text object. */
        val textColor = style.color
        if (style.usesPlainRasterizer()) {
            rasterizer.drawText(buffer = buffer, text = displayText, x = x, y = y, color = textColor)
            return
        }
        /** Top edge of the line currently being painted. */
        var cursorY = y
        paragraphLayout.lines.forEach { line ->
            /** Horizontal origin of the next run on this aligned visual line. */
            var cursorX = x + ParagraphLayoutSupport.resolveLineStartX(
                textAlign = textAlign,
                textDirection = textDirection,
                availableWidth = size.width - paddingLeft - paddingRight,
                lineWidth = line.width,
            )
            line.runs.forEach { run ->
                PixelParagraphPainter.drawRun(
                    buffer = buffer,
                    bufferPool = bufferPool,
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

    /** Maps a local y coordinate to the nearest visible paragraph line. */
    private fun lineIndexAt(localY: Int): Int {
        /** Visible paragraph lines searched in paint order. */
        val lines = paragraphLayout.lines
        if (lines.isEmpty()) return 0
        /** Non-negative vertical coordinate relative to the paragraph origin. */
        val yInText = (localY - drawTextY).coerceAtLeast(0)
        /** Accumulated top edge of the candidate line. */
        var cursorY = 0
        lines.forEachIndexed { index, line ->
            /** Exclusive bottom edge of the candidate line. */
            val nextY = cursorY + line.height
            if (yInText < nextY || index == lines.lastIndex) return index
            cursorY = nextY
        }
        return lines.lastIndex
    }

    /** Pins an end-aligned TextField caret to the visible content right edge. */
    private fun visibleTextEndCaretRect(): PixelTextRangeRect {
        /** Paragraph-derived logical end caret. */
        val caret = caretRect(text.length)
        /** Smallest legal local caret x after left padding. */
        val minX = paddingLeft.coerceAtLeast(0)
        /** Rightmost legal local caret x inside this render box. */
        val maxX = (size.width - caret.width).coerceAtLeast(minX)
        return caret.copy(x = maxX)
    }

    /** Resolves one line's aligned local x origin from final render size. */
    private fun lineStartX(lineWidth: Int): Int {
        return paddingLeft + ParagraphLayoutSupport.resolveLineStartX(
            textAlign = textAlign,
            textDirection = textDirection,
            availableWidth = size.width - paddingLeft - paddingRight,
            lineWidth = lineWidth,
        )
    }

    /** Returns whether a single compatibility draw call preserves this style's metrics. */
    private fun PixelTextStyle.usesPlainRasterizer(): Boolean {
        return letterSpacing <= 0 && fontScale <= 1 && lineHeight == null && lineSpacing <= 0
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
        /** Scratch-buffer row copied into the destination. */
        for (row in 0 until copyHeight) {
            /** Scratch-buffer column copied into the destination. */
            for (column in 0 until copyWidth) {
                /** Source color copied only when it contains visible alpha. */
                val pixel = source.getPixel(column, row)
                if (pixel.alpha > 0) {
                    destination.setPixel(x = destX + column, y = destY + row, color = pixel)
                }
            }
        }
    }

    /** Adds this render object to hit-test results only when it owns a click callback. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        if (localX !in 0 until size.width || localY !in 0 until size.height) return
        if (onClick != null) result.add(this)
    }

    /** Exports an exact click target for non-semantic pointer routing. */
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

    /** Exports spoken text, activation, and exact UTF-16 character geometry. */
    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        if (text.isBlank()) return
        /** Text owns its click callback directly so accessibility never re-resolves by geometry. */
        val semanticActions = PixelSemanticsActions(
            onClick = onClick?.let { callback ->
                {
                    callback()
                    true
                }
            },
        )
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
                id = semanticNodeId(),
                actions = semanticActions.capabilitySet(),
            ),
            source = this,
            actions = semanticActions,
            characterBoundsForRange = { start, length ->
                textCharacterRects(start = start, length = length).map { rect ->
                    rect?.let { characterRect ->
                        PixelRect(
                            left = offsetX + characterRect.x,
                            top = offsetY + characterRect.y,
                            width = characterRect.width,
                            height = characterRect.height,
                        )
                    }
                }
            },
        )
    }

    /** Resolves the constrained inner paragraph width without allowing negative padding results. */
    private fun resolveAvailableTextWidth(constraints: RenderConstraints, horizontalPadding: Int): Int {
        /** Outer width selected from explicit, fill, and ordinary constraint modes. */
        val measuredWidth = when {
            explicitWidth != null -> explicitWidth
            fillMaxWidth || occupyFullWidth -> constraints.maxWidth
            else -> constraints.maxWidth
        }
        return (constraints.constrainWidth(measuredWidth) - horizontalPadding).coerceAtLeast(0)
    }
}

/** 定义 `PixelTextRangeRect` 在 `RenderText` 中承担的数据与行为边界。
 *
 * Local paragraph rectangle used by caret, selection, composition and accessibility geometry.
 */
public data class PixelTextRangeRect(
    /** Left edge in RenderText-local coordinates. */
    val x: Int,
    /** Top edge in RenderText-local coordinates. */
    val y: Int,
    /** Positive rectangle width. */
    val width: Int,
    /** Positive line-derived rectangle height. */
    val height: Int,
)

/** One logical caret edge before duplicate visual x positions are coalesced. */
private data class TextCaretCandidate(
    /** Local x coordinate within the selected paragraph line. */
    val x: Int,
    /** Whether this edge belongs to the cluster logically following the boundary. */
    val downstream: Boolean,
)

/** 定义 `PixelTextAffinity` 在 `RenderText` 中承担的数据与行为边界。
 *
 * Logical side used when one UTF-16 boundary has two visual Bidi caret positions.
 */
public enum class PixelTextAffinity {
    /** Edge owned by the grapheme logically following the boundary. */
    DOWNSTREAM,

    /** Edge owned by the grapheme logically preceding the boundary. */
    UPSTREAM,
}
