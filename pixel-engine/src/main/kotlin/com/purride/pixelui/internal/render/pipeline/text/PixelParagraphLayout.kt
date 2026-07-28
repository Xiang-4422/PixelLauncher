package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelGraphemeBoundaryMap
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.TextDirection
import com.purride.pixelui.internal.text.bidi.UnicodeBidiResolver

/** Immutable paragraph request shared by plain Text and RichText render objects. */
internal data class PixelParagraphInput(
    /** Ordered styled spans whose concatenation forms the exact backing text. */
    val spans: List<PixelTextSpan>,
    /** Logical alignment applied independently to every visible line. */
    val textAlign: PixelTextAlign,
    /** Explicit paragraph base direction used by UAX #9 resolution. */
    val textDirection: TextDirection,
    /** Whether width overflow may create additional soft lines. */
    val softWrap: Boolean,
    /** Paint overflow policy applied only at whole-grapheme boundaries. */
    val overflow: PixelTextOverflow,
    /** Maximum visible line count. */
    val maxLines: Int,
    /** Inherited rasterizer used when a span has no explicit override. */
    val defaultTextRasterizer: PixelTextRasterizer,
)

/** Complete immutable layout consumed by paint, caret, hit-test, selection and semantics geometry. */
internal data class PixelParagraphLayout(
    /** Visible physical and soft-wrapped lines in source order. */
    val lines: List<PixelParagraphLine>,
) {
    /** Maximum visual line advance. */
    val width: Int = lines.maxOfOrNull { line -> line.width } ?: 0

    /** Sum of visible line heights including configured inter-line spacing. */
    val height: Int = lines.sumOf { line -> line.height }
}

/** One paragraph line with logical source bounds and visual cluster geometry. */
internal data class PixelParagraphLine(
    /** Adjacent visual clusters coalesced by style for painting. */
    val runs: List<PixelParagraphRun>,
    /** Clusters in final left-to-right visual order with stable x coordinates. */
    val visualClusters: List<PixelParagraphCluster>,
    /** Total visual advance. */
    val width: Int,
    /** Line height shared by caret and selection rectangles. */
    val height: Int,
    /** First logical UTF-16 source boundary represented by this line. */
    val sourceStart: Int,
    /** Final logical UTF-16 source boundary before any hard line-break cluster. */
    val sourceEnd: Int,
)

/** One paint run retaining its constituent clusters instead of flattening back to Char units. */
internal data class PixelParagraphRun(
    /** Visual display text after unsupported-cluster fallback and Bidi mirroring. */
    val text: String,
    /** Style selected at the leading UTF-16 unit of every cluster in this run. */
    val style: PixelTextStyle,
    /** Sum of cluster advances, including cluster-level letter spacing. */
    val width: Int,
    /** Visual clusters painted atomically by this run. */
    val clusters: List<PixelParagraphCluster>,
)

/** Atomic extended-grapheme layout unit with logical and visual mappings. */
internal data class PixelParagraphCluster(
    /** Exact non-normalized backing substring. */
    val sourceText: String,
    /** Exact supported cluster, one fallback scalar, mirrored punctuation, or an empty ignorable. */
    val renderText: String,
    /** Style owned by the cluster's leading UTF-16 source unit. */
    val style: PixelTextStyle,
    /** Inclusive logical UTF-16 source boundary. */
    val sourceStart: Int,
    /** Exclusive logical UTF-16 source boundary. */
    val sourceEnd: Int,
    /** Measured visual advance after font scale and cluster-level letter spacing. */
    val width: Int,
    /** 不含相邻字形 pair 修正的独立宽度，供换行和视觉重排复用。 */
    val standaloneWidth: Int = width,
    /** Resolved UAX #9 embedding level; odd values paint right-to-left. */
    val bidiLevel: Int = 0,
    /** Left edge in the line's final visual coordinate space. */
    val visualX: Int = 0,
    /** Whether this cluster is generated ellipsis rather than a backing-text range. */
    val isSynthetic: Boolean = false,
) {
    /** Whether logical start is the visual right edge. */
    val isRightToLeft: Boolean
        get() = bidiLevel % 2 != 0
}

/** Grapheme-safe paragraph layout, UAX #9 visual ordering and source/visual geometry builder. */
internal object PixelParagraphLayouter {
    /** Lays out [input] without ever splitting a Unicode 17 extended grapheme cluster. */
    fun layout(
        input: PixelParagraphInput,
        availableWidth: Int,
    ): PixelParagraphLayout {
        if (input.maxLines <= 0 || availableWidth <= 0) {
            return PixelParagraphLayout(lines = emptyList())
        }
        /** Exact concatenated text and one style entry per UTF-16 source unit. */
        val flattened = flattenSpans(input.spans)
        if (flattened.text.isEmpty()) {
            return PixelParagraphLayout(lines = emptyList())
        }
        /** Unicode 17 clusters measured atomically before wrapping or Bidi reordering. */
        val clusters = buildClusters(
            flattened = flattened,
            defaultTextRasterizer = input.defaultTextRasterizer,
        )
        /** Hard-line segmentation preserving leading, trailing and consecutive empty lines. */
        val physicalLines = splitHardLines(
            clusters = clusters,
            textLength = flattened.text.length,
            fallbackStyle = flattened.fallbackStyle,
        )
        /** Physical lines whose UAX #9 levels are resolved before line wrapping, as required by L1. */
        val leveledPhysicalLines = physicalLines.map { physicalLine ->
            physicalLine.copy(
                clusters = resolveBidiLevels(physicalLine.clusters, input.textDirection),
            )
        }
        /** Logical soft lines before max-lines truncation and visual ordering. */
        val wrappedLines = leveledPhysicalLines.flatMap { physicalLine ->
            if (input.softWrap) {
                wrapLogicalLine(
                    line = physicalLine,
                    availableWidth = availableWidth,
                    defaultTextRasterizer = input.defaultTextRasterizer,
                )
            } else if (input.overflow == PixelTextOverflow.ELLIPSIS) {
                // 单行省略号必须先取得 pair 修正后的完整宽度，才能判断是否需要截断。
                listOf(
                    physicalLine.copy(
                        clusters = remeasureClusterSequence(
                            clusters = physicalLine.clusters,
                            defaultTextRasterizer = input.defaultTextRasterizer,
                        ),
                    ),
                )
            } else {
                // CLIP 单行会在视觉行转换时统一重测一次，避免对同一相邻 pair 重复测量。
                listOf(physicalLine)
            }
        }.map { line ->
            if (
                !input.softWrap &&
                input.overflow == PixelTextOverflow.ELLIPSIS &&
                line.width > availableWidth
            ) {
                ellipsize(
                    line = line,
                    availableWidth = availableWidth,
                    defaultTextRasterizer = input.defaultTextRasterizer,
                    textDirection = input.textDirection,
                )
            } else {
                line
            }
        }
        if (wrappedLines.isEmpty()) {
            return PixelParagraphLayout(lines = emptyList())
        }

        /** Whether hidden logical lines require an ellipsis on the final visible line. */
        val truncated = wrappedLines.size > input.maxLines
        /** 可见前缀直接写入一个可变列表，避免 `take` 后再复制一次。 */
        val visibleLineCount = minOf(wrappedLines.size, input.maxLines)
        /** Mutable visible prefix allowing the final logical line to be replaced atomically. */
        val visibleLines = ArrayList<LogicalParagraphLine>(visibleLineCount)
        for (lineIndex in 0 until visibleLineCount) {
            visibleLines += wrappedLines[lineIndex]
        }
        if (truncated && input.overflow == PixelTextOverflow.ELLIPSIS && visibleLines.isNotEmpty()) {
            visibleLines[visibleLines.lastIndex] = ellipsize(
                line = visibleLines.last(),
                availableWidth = availableWidth,
                defaultTextRasterizer = input.defaultTextRasterizer,
                textDirection = input.textDirection,
            )
        }

        return PixelParagraphLayout(
            lines = visibleLines.mapIndexed { lineIndex, logicalLine ->
                toVisualLine(
                    line = logicalLine,
                    defaultTextRasterizer = input.defaultTextRasterizer,
                    includeTrailingLineSpacing = lineIndex < visibleLines.lastIndex,
                )
            },
        )
    }

    /** Builds exact source clusters and assigns one deterministic paint payload to each cluster. */
    private fun buildClusters(
        flattened: FlattenedParagraph,
        defaultTextRasterizer: PixelTextRasterizer,
    ): List<PixelParagraphCluster> {
        /** Engine-owned boundary authority for the complete cross-span source. */
        val boundaries = PixelGraphemeBoundaryMap(flattened.text)
        /** Logical clusters in monotonically increasing UTF-16 order. */
        val clusters = mutableListOf<PixelParagraphCluster>()
        /** 当前簇首单元所属的原始 span 下标。 */
        var spanIndex = -1
        /** 当前 span 在拼接文本中的排他结束边界。 */
        var spanEnd = 0
        /** Leading UTF-16 boundary of the next cluster. */
        var start = 0
        while (start < flattened.text.length) {
            /** Trailing UTF-16 boundary returned by strict Unicode grapheme movement. */
            val end = boundaries.next(start)
            /** Exact cluster source substring, potentially spanning RichText style boundaries. */
            val sourceText = flattened.text.substring(start, end)
            while (spanIndex + 1 < flattened.spans.size && start >= spanEnd) {
                spanIndex += 1
                spanEnd += flattened.spans[spanIndex].text.length
            }
            /** Leading-unit style; a mid-cluster span change is deferred to the next cluster. */
            val style = flattened.spans.getOrNull(spanIndex)?.style ?: flattened.fallbackStyle
            /** Rasterizer selected before support and fallback resolution. */
            val rasterizer = style.textRasterizer ?: defaultTextRasterizer
            /** Stable paint payload preventing one unsupported cluster from producing many tofu. */
            val renderText = PixelParagraphClusterSupport.resolveRenderableText(sourceText, rasterizer)
            clusters += PixelParagraphCluster(
                sourceText = sourceText,
                renderText = renderText,
                style = style,
                sourceStart = start,
                sourceEnd = end,
                width = measureStyledCluster(renderText, style, defaultTextRasterizer),
            )
            start = end
        }
        return clusters
    }

    /** Splits hard breaks while retaining both source boundaries around CRLF/NEL/LS/PS. */
    private fun splitHardLines(
        clusters: List<PixelParagraphCluster>,
        textLength: Int,
        fallbackStyle: PixelTextStyle,
    ): List<LogicalParagraphLine> {
        /** Physical lines including empty leading, consecutive and trailing lines. */
        val lines = mutableListOf<LogicalParagraphLine>()
        /** Non-break clusters accumulated for the current physical line. */
        val current = mutableListOf<PixelParagraphCluster>()
        /** UTF-16 boundary immediately after the previous hard break. */
        var lineStart = 0
        /** Style inherited by an empty line at the current boundary. */
        var emptyLineStyle = fallbackStyle
        clusters.forEach { cluster ->
            if (PixelParagraphClusterSupport.isHardLineBreak(cluster.sourceText)) {
                lines += LogicalParagraphLine(
                    clusters = current.toList(),
                    sourceStart = lineStart,
                    sourceEnd = cluster.sourceStart,
                    fallbackStyle = current.firstOrNull()?.style ?: cluster.style,
                )
                current.clear()
                lineStart = cluster.sourceEnd
                emptyLineStyle = cluster.style
            } else {
                current += cluster
                emptyLineStyle = cluster.style
            }
        }
        lines += LogicalParagraphLine(
            clusters = current.toList(),
            sourceStart = lineStart,
            sourceEnd = textLength,
            fallbackStyle = current.firstOrNull()?.style ?: emptyLineStyle,
        )
        return lines
    }

    /** Wraps one physical line only between complete grapheme clusters. */
    private fun wrapLogicalLine(
        line: LogicalParagraphLine,
        availableWidth: Int,
        defaultTextRasterizer: PixelTextRasterizer,
    ): List<LogicalParagraphLine> {
        if (line.clusters.isEmpty()) return listOf(line)
        /** Soft lines emitted from this physical line. */
        val lines = mutableListOf<LogicalParagraphLine>()
        /** Clusters accumulated for the current soft line. */
        val current = ArrayList<PixelParagraphCluster>()
        /** 当前软行包含全部 pair 修正后的精确宽度。 */
        var currentWidth = 0
        line.clusters.forEach { cluster ->
            /** 新簇在任何相邻关系形成前只携带独立宽度。 */
            val standaloneCluster = cluster.copy(width = cluster.standaloneWidth)
            /** 当前末簇接入新右邻居后应使用的宽度。 */
            val adjustedLeftWidth = current.lastOrNull()?.let { left ->
                measurePairAdjustedLeftWidth(
                    left = left,
                    right = standaloneCluster,
                    defaultTextRasterizer = defaultTextRasterizer,
                )
            }
            /** 候选宽度只增加一个新簇和一个新 pair，避免重复扫描整个软行。 */
            val candidateWidth = currentWidth + standaloneCluster.width +
                if (adjustedLeftWidth == null) 0 else adjustedLeftWidth - current.last().width
            if (current.isNotEmpty() && candidateWidth > availableWidth) {
                lines += logicalLineFromClusters(
                    clusters = current,
                    fallbackStyle = line.fallbackStyle,
                )
                current.clear()
                current += standaloneCluster
                currentWidth = standaloneCluster.width
            } else {
                if (adjustedLeftWidth != null) {
                    /** pair 修正归属左簇，右簇保留独立宽度等待后续邻居。 */
                    current[current.lastIndex] = current.last().copy(width = adjustedLeftWidth)
                }
                current += standaloneCluster
                currentWidth = candidateWidth
            }
        }
        if (current.isNotEmpty()) {
            lines += logicalLineFromClusters(
                clusters = current,
                fallbackStyle = line.fallbackStyle,
            )
        }
        return lines
    }

    /** 只按完整 cluster 删除，直到三点省略号可以放入。 */
    private fun ellipsize(
        line: LogicalParagraphLine,
        availableWidth: Int,
        defaultTextRasterizer: PixelTextRasterizer,
        textDirection: TextDirection,
    ): LogicalParagraphLine {
        /** Style inherited by the generated ellipsis. */
        val ellipsisStyle = line.clusters.lastOrNull()?.style ?: line.fallbackStyle
        /** Display-generated dots inherit the resolved logical-end level or paragraph base level. */
        val ellipsisLevel = line.clusters.lastOrNull()?.bidiLevel
            ?: if (textDirection == TextDirection.RTL) 1 else 0
        /** Three synthetic dot clusters preserving the historical visible ellipsis string. */
        val ellipsisClusters = ParagraphLayoutSupport.Ellipsis.map { dot ->
            /** Renderable one-scalar dot text. */
            val dotText = dot.toString()
            PixelParagraphCluster(
                sourceText = "",
                renderText = dotText,
                style = ellipsisStyle,
                sourceStart = line.sourceEnd,
                sourceEnd = line.sourceEnd,
                width = measureStyledCluster(dotText, ellipsisStyle, defaultTextRasterizer),
                bidiLevel = ellipsisLevel,
                isSynthetic = true,
            )
        }
        /** Total ellipsis width measured at cluster granularity. */
        val ellipsisWidth = ellipsisClusters.sumOf { cluster -> cluster.width }
        if (ellipsisWidth > availableWidth) {
            return line.copy(clusters = emptyList())
        }
        /** Existing generated ellipsis is removed before re-ellipsizing a max-lines truncation. */
        val result = line.clusters.filterNot { cluster -> cluster.isSynthetic }.toMutableList()
        /** Current source-cluster width reduced one complete grapheme at a time. */
        var resultWidth = result.sumOf { cluster -> cluster.width }
        while (result.isNotEmpty() && resultWidth + ellipsisWidth > availableWidth) {
            /** Whole cluster removed from the logical end. */
            val removed = result.removeAt(result.lastIndex)
            resultWidth -= removed.width
        }
        return line.copy(
            clusters = remeasureClusterSequence(result + ellipsisClusters, defaultTextRasterizer),
        )
    }

    /** Resolves UAX #9 levels, reorders clusters visually, assigns x coordinates and paint runs. */
    private fun toVisualLine(
        line: LogicalParagraphLine,
        defaultTextRasterizer: PixelTextRasterizer,
        includeTrailingLineSpacing: Boolean,
    ): PixelParagraphLine {
        /** Left-to-right visual cluster order produced by UAX #9 L2 level reversal. */
        val reordered = remeasureClusterSequence(
            clusters = reorderVisually(line.clusters),
            defaultTextRasterizer = defaultTextRasterizer,
        )
        /** Final visual clusters with stable x and mirrored paint payload. */
        val visualClusters = mutableListOf<PixelParagraphCluster>()
        /** Left edge assigned to the next visual cluster. */
        var cursorX = 0
        reordered.forEach { cluster ->
            /** Mirrored glyph payload for odd levels; backing source remains untouched. */
            val rendered = PixelParagraphClusterSupport.mirrorForOddLevel(
                text = cluster.renderText,
                bidiLevel = cluster.bidiLevel,
            )
            visualClusters += cluster.copy(renderText = rendered, visualX = cursorX)
            cursorX += cluster.width
        }
        /** Height measured from logical content because visual order cannot change vertical metrics. */
        val height = measureLineHeight(
            clusters = line.clusters,
            fallbackStyle = line.fallbackStyle,
            defaultTextRasterizer = defaultTextRasterizer,
            includeTrailingLineSpacing = includeTrailingLineSpacing,
        )
        return PixelParagraphLine(
            runs = buildRuns(visualClusters),
            visualClusters = visualClusters,
            width = cursorX,
            height = height,
            sourceStart = line.sourceStart,
            sourceEnd = line.sourceEnd,
        )
    }

    /** Resolves one Unicode 17 embedding level per grapheme before soft line wrapping. */
    private fun resolveBidiLevels(
        clusters: List<PixelParagraphCluster>,
        textDirection: TextDirection,
    ): List<PixelParagraphCluster> {
        if (clusters.isEmpty()) return emptyList()
        /** UAX #9 输入所需的精确 scalar 数量，避免装箱列表和二次数组复制。 */
        val codePointCount = clusters.sumOf { cluster ->
            /** 普通簇使用原始 source，synthetic ellipsis 使用可见 payload。 */
            val bidiInput = if (cluster.isSynthetic) cluster.renderText else cluster.sourceText
            Character.codePointCount(bidiInput, 0, bidiInput.length)
        }
        /** Complete fixed-data UAX #9 input in scalar order. */
        val codePoints = IntArray(codePointCount)
        /** Leading code-point position aligned with each logical grapheme cluster. */
        val clusterStarts = IntArray(clusters.size)
        /** 下一个 scalar 写入 [codePoints] 的位置。 */
        var codePointIndex = 0
        clusters.forEachIndexed { clusterIndex, cluster ->
            clusterStarts[clusterIndex] = codePointIndex
            /** Exact source for ordinary clusters and visible payload for synthetic ellipsis. */
            val bidiInput = if (cluster.isSynthetic) cluster.renderText else cluster.sourceText
            /** UTF-16 offset advanced across complete scalar values. */
            var offset = 0
            while (offset < bidiInput.length) {
                /** Scalar appended to the engine-owned Bidi input. */
                val codePoint = Character.codePointAt(bidiInput, offset)
                codePoints[codePointIndex] = codePoint
                codePointIndex += 1
                offset += Character.charCount(codePoint)
            }
        }
        /** Explicit paragraph direction preserving the public Directionality contract. */
        val paragraphLevel: Byte = if (textDirection == TextDirection.RTL) 1 else 0
        /** Unicode 17/UAX #9 revision 51 result independent of JVM or Android ICU versions. */
        val resolution = UnicodeBidiResolver.resolveCodePoints(
            codePoints = codePoints,
            paragraphLevel = paragraphLevel,
        )
        return clusters.mapIndexed { clusterIndex, cluster ->
            /** Leading scalar level; UAX rules keep combining members in the same grapheme run. */
            val level = resolution.levels[clusterStarts[clusterIndex]].toInt()
            cluster.copy(bidiLevel = level)
        }
    }

    /** Applies UAX #9 L2 reversal to whole cluster objects rather than UTF-16 units. */
    private fun reorderVisually(clusters: List<PixelParagraphCluster>): List<PixelParagraphCluster> {
        if (clusters.size <= 1) return clusters
        /** Highest resolved level present on this physical line. */
        val highestLevel = clusters.maxOf { cluster -> cluster.bidiLevel }
        /** Lowest odd level at which UAX #9 L2 begins reversing runs. */
        var lowestOddLevel = Int.MAX_VALUE
        clusters.forEach { cluster ->
            if (cluster.isRightToLeft && cluster.bidiLevel < lowestOddLevel) {
                lowestOddLevel = cluster.bidiLevel
            }
        }
        if (lowestOddLevel == Int.MAX_VALUE) return clusters
        /** Mutable cluster sequence reversed in place one embedding threshold at a time. */
        val reordered = clusters.toMutableList()
        for (level in highestLevel downTo lowestOddLevel) {
            /** Logical/visual position examined for the next at-or-above-level run. */
            var index = 0
            while (index < reordered.size) {
                if (reordered[index].bidiLevel < level) {
                    index += 1
                    continue
                }
                /** First cluster after the contiguous at-or-above-level run. */
                var limit = index + 1
                while (limit < reordered.size && reordered[limit].bidiLevel >= level) {
                    limit += 1
                }
                reordered.subList(index, limit).reverse()
                index = limit
            }
        }
        return reordered
    }

    /** Coalesces adjacent visual clusters with the same style into painter runs. */
    private fun buildRuns(clusters: List<PixelParagraphCluster>): List<PixelParagraphRun> {
        if (clusters.isEmpty()) return emptyList()
        /** Completed paint runs in visual order. */
        val runs = mutableListOf<PixelParagraphRun>()
        /** Clusters accumulated under one style. */
        val current = mutableListOf<PixelParagraphCluster>()
        /** Style owning [current]. */
        var currentStyle: PixelTextStyle? = null
        clusters.forEach { cluster ->
            if (current.isNotEmpty() && currentStyle != cluster.style) {
                runs += current.toRun(checkNotNull(currentStyle))
                current.clear()
            }
            currentStyle = cluster.style
            current += cluster
        }
        if (current.isNotEmpty()) {
            runs += current.toRun(checkNotNull(currentStyle))
        }
        return runs
    }

    /** Converts one style-homogeneous visual cluster list into an immutable paint run. */
    private fun List<PixelParagraphCluster>.toRun(style: PixelTextStyle): PixelParagraphRun {
        return PixelParagraphRun(
            text = joinToString(separator = "") { cluster -> cluster.renderText },
            style = style,
            width = sumOf { cluster -> cluster.width },
            clusters = toList(),
        )
    }

    /** Measures one cluster payload with font scale and one letter-spacing unit. */
    private fun measureStyledCluster(
        renderText: String,
        style: PixelTextStyle,
        defaultTextRasterizer: PixelTextRasterizer,
    ): Int {
        if (renderText.isEmpty()) return 0
        /** Rasterizer that accepted the exact cluster or receives one replacement scalar. */
        val rasterizer = style.textRasterizer ?: defaultTextRasterizer
        /** Positive integer font scale retained by the existing pixel style contract. */
        val scale = style.fontScale.coerceAtLeast(1)
        /** Non-negative spacing applied once after the complete cluster. */
        val spacing = style.letterSpacing.coerceAtLeast(0)
        return (rasterizer.measureText(renderText) * scale) + spacing
    }

    /** Measures line height without inspecting or splitting code units inside a cluster. */
    private fun measureLineHeight(
        clusters: List<PixelParagraphCluster>,
        fallbackStyle: PixelTextStyle,
        defaultTextRasterizer: PixelTextRasterizer,
        includeTrailingLineSpacing: Boolean,
    ): Int {
        if (clusters.isEmpty()) {
            return measureLineSampleHeight(
                sample = " ",
                style = fallbackStyle,
                defaultTextRasterizer = defaultTextRasterizer,
                includeTrailingLineSpacing = includeTrailingLineSpacing,
            )
        }
        /** 扫描期间已观察到的最大行高，不创建 sample/样式 Pair 集合。 */
        var maximumHeight = 1
        clusters.forEach { cluster ->
            /** 空的 default-ignorable 仍以空格取得稳定字体高度。 */
            val sample = cluster.renderText.ifEmpty { " " }
            /** 当前簇样本在相同行距语义下的完整高度。 */
            val sampleHeight = measureLineSampleHeight(
                sample = sample,
                style = cluster.style,
                defaultTextRasterizer = defaultTextRasterizer,
                includeTrailingLineSpacing = includeTrailingLineSpacing,
            )
            if (sampleHeight > maximumHeight) {
                maximumHeight = sampleHeight
            }
        }
        return maximumHeight
    }

    /** 测量单个簇样本的行高，保留显式 lineHeight 和旧栅格器内建行距语义。 */
    private fun measureLineSampleHeight(
        sample: String,
        style: PixelTextStyle,
        defaultTextRasterizer: PixelTextRasterizer,
        includeTrailingLineSpacing: Boolean,
    ): Int {
        style.lineHeight?.let { explicit -> return explicit.coerceAtLeast(1) }
        /** 当前簇首样式选择的实际栅格器。 */
        val rasterizer = style.textRasterizer ?: defaultTextRasterizer
        /** 可选尾部行距加入前的缩放字形高度。 */
        val glyphHeight =
            (rasterizer.measureHeight(sample).coerceAtLeast(1) * style.fontScale.coerceAtLeast(1))
                .coerceAtLeast(1)
        if (!includeTrailingLineSpacing) return glyphHeight
        if (style.letterSpacing > 0 || style.fontScale > 1 || style.lineSpacing > 0) {
            return glyphHeight + style.lineSpacing.coerceAtLeast(0)
        }
        /** 两行样本用于保留旧栅格器自带的行间距。 */
        val twoLineHeight = rasterizer.measureHeight("$sample\n$sample")
            .coerceAtLeast(glyphHeight)
        return (twoLineHeight - glyphHeight).coerceAtLeast(glyphHeight)
    }

    /**
     * Remeasures visual adjacency without flattening source ranges.
     *
     * Each cluster starts with its standalone advance. A same-style adjacent pair may introduce
     * kerning or the existing wide-glyph protection gap; that delta is added to the left cluster so
     * the right cluster's [PixelParagraphCluster.visualX] begins after the actual gap. Hard breaks,
     * empty ignorables and style changes reset pair context.
     */
    private fun remeasureClusterSequence(
        clusters: List<PixelParagraphCluster>,
        defaultTextRasterizer: PixelTextRasterizer,
    ): List<PixelParagraphCluster> {
        if (clusters.isEmpty()) return emptyList()
        /** Mutable standalone widths that receive pair deltas on their left entries. */
        val measured = ArrayList<PixelParagraphCluster>(clusters.size)
        clusters.forEach { cluster ->
            measured += cluster.copy(width = cluster.standaloneWidth)
        }
        for (index in 0 until measured.lastIndex) {
            /** Left visual neighbor that owns any pair-gap delta. */
            val left = measured[index]
            /** Right visual neighbor whose glyph origin follows the pair gap. */
            val right = measured[index + 1]
            measured[index] = left.copy(
                width = measurePairAdjustedLeftWidth(
                    left = left,
                    right = right,
                    defaultTextRasterizer = defaultTextRasterizer,
                ),
            )
        }
        return measured
    }

    /** 计算左簇接入一个右邻居后的宽度，并在不可配对时保持独立宽度。 */
    private fun measurePairAdjustedLeftWidth(
        left: PixelParagraphCluster,
        right: PixelParagraphCluster,
        defaultTextRasterizer: PixelTextRasterizer,
    ): Int {
        if (
            left.style != right.style ||
            left.renderText.isEmpty() ||
            right.renderText.isEmpty() ||
            PixelParagraphClusterSupport.isHardLineBreak(left.sourceText) ||
            PixelParagraphClusterSupport.isHardLineBreak(right.sourceText)
        ) {
            return left.standaloneWidth
        }
        /** 相同样式相邻簇共享的栅格器。 */
        val rasterizer = left.style.textRasterizer ?: defaultTextRasterizer
        /** 相同样式相邻簇共享的整数缩放。 */
        val scale = left.style.fontScale.coerceAtLeast(1)
        /** pair 和两个独立簇都各自包含一次的外部字距。 */
        val spacing = left.style.letterSpacing.coerceAtLeast(0)
        /** 栅格器报告的完整 pair 宽度。 */
        val pairWidth =
            (rasterizer.measureAdjacentText(left.renderText, right.renderText) * scale) + spacing * 2
        /** 相对于两个独立宽度的正负邻接修正。 */
        val pairDelta = pairWidth - left.standaloneWidth - right.standaloneWidth
        return (left.standaloneWidth + pairDelta).coerceAtLeast(0)
    }

    /** Concatenates spans and records the leading style for every UTF-16 source unit. */
    private fun flattenSpans(spans: List<PixelTextSpan>): FlattenedParagraph {
        /** Exact backing text builder preserving all source code units. */
        val text = StringBuilder()
        /** Most recent style used when a trailing empty line has no source unit. */
        var fallbackStyle = PixelTextStyle.Default
        spans.forEach { span ->
            text.append(span.text)
            fallbackStyle = span.style
        }
        return FlattenedParagraph(
            text = text.toString(),
            spans = spans,
            fallbackStyle = fallbackStyle,
        )
    }

    /** Creates a logical line whose source range is derived from complete clusters. */
    private fun logicalLineFromClusters(
        clusters: List<PixelParagraphCluster>,
        fallbackStyle: PixelTextStyle,
    ): LogicalParagraphLine {
        return LogicalParagraphLine(
            clusters = clusters.toList(),
            sourceStart = clusters.first().sourceStart,
            sourceEnd = clusters.last().sourceEnd,
            fallbackStyle = clusters.firstOrNull()?.style ?: fallbackStyle,
        )
    }

    /** Exact flattened source and UTF-16 style lookup. */
    private data class FlattenedParagraph(
        /** Concatenated non-normalized span text. */
        val text: String,
        /** 原始有序 span；布局按单调 source offset 游标读取 leading style。 */
        val spans: List<PixelTextSpan>,
        /** Style used to measure a trailing or otherwise empty physical line. */
        val fallbackStyle: PixelTextStyle,
    )

    /** Logical pre-Bidi line retaining empty-line source boundaries. */
    private data class LogicalParagraphLine(
        /** Grapheme clusters in logical source order. */
        val clusters: List<PixelParagraphCluster>,
        /** Leading logical UTF-16 boundary. */
        val sourceStart: Int,
        /** Trailing logical UTF-16 boundary before a hard break. */
        val sourceEnd: Int,
        /** Style used when [clusters] is empty. */
        val fallbackStyle: PixelTextStyle,
    ) {
        /** Logical width, independent of later Bidi visual order. */
        val width: Int = clusters.sumOf { cluster -> cluster.width }
    }
}
