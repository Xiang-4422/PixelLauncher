package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelBufferPool
import com.purride.pixelcore.PixelClusterTextRasterizer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelFontMetrics
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelGraphemeBoundaryMap
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.TextDirection
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies grapheme-safe paragraph layout and logical-to-visual Bidi geometry. */
class PixelParagraphUnicodeLayoutTest {
    /** Cluster-aware rasterizer assigning exactly one pixel to every supported non-empty cluster. */
    private val clusterRasterizer = UnitClusterRasterizer()

    /** Legacy rasterizer that does not claim multi-code-point cluster support. */
    private val legacyRasterizer = LegacyUnitRasterizer()

    /** Proves a grapheme crossing a RichText span boundary keeps one layout unit and leading style. */
    @Test
    fun richTextSpanBoundaryCannotSplitACombiningCluster() {
        /** Leading style that must own the complete cross-span cluster. */
        val leadingStyle = PixelTextStyle(
            color = PixelColor.White,
            textRasterizer = clusterRasterizer,
        )
        /** Trailing style that starts inside the cluster and applies only from the next boundary. */
        val trailingStyle = PixelTextStyle(
            color = PixelColor.fromRgb(200, 100, 0),
            textRasterizer = clusterRasterizer,
        )
        /** Two one-cell lines created only between `é` and `X`. */
        val layout = layout(
            spans = listOf(
                PixelTextSpan("e", leadingStyle),
                PixelTextSpan("\u0301X", trailingStyle),
            ),
            availableWidth = 1,
        )

        assertEquals(2, layout.lines.size)
        assertEquals("e\u0301", layout.lines[0].visualClusters.single().sourceText)
        assertEquals(0, layout.lines[0].sourceStart)
        assertEquals(2, layout.lines[0].sourceEnd)
        assertEquals(leadingStyle, layout.lines[0].visualClusters.single().style)
        assertEquals("X", layout.lines[1].visualClusters.single().sourceText)
        assertEquals(trailingStyle, layout.lines[1].visualClusters.single().style)
    }

    /** Proves one unsupported ZWJ sequence produces one fallback rather than per-code-point tofu. */
    @Test
    fun legacyRasterizerGetsOneFallbackForAnUnsupportedFamilyCluster() {
        /** Multi-code-point family cluster followed by one independently supported ASCII scalar. */
        val family = "👨‍👩‍👧‍👦"
        /** Layout using a pre-cluster-capability rasterizer. */
        val layout = layout(
            spans = listOf(
                PixelTextSpan(
                    text = family + "A",
                    style = PixelTextStyle(textRasterizer = legacyRasterizer),
                ),
            ),
            availableWidth = 10,
        )
        /** Backing clusters retained after fallback resolution. */
        val clusters = layout.lines.single().visualClusters

        assertEquals(2, clusters.size)
        assertEquals(family, clusters[0].sourceText)
        assertEquals("\uFFFD", clusters[0].renderText)
        assertEquals(0, clusters[0].sourceStart)
        assertEquals(family.length, clusters[0].sourceEnd)
        assertEquals("A", clusters[1].renderText)
    }

    /** Proves formatting controls remain in source/Bidi input but paint no standalone tofu. */
    @Test
    fun defaultIgnorablesRemainZeroWidthAndUnpainted() {
        /** Representative generated Default_Ignorable entries across BMP and supplementary ranges. */
        val samples = listOf(
            "\u034F", // COMBINING GRAPHEME JOINER
            "\u17B4", // KHMER VOWEL INHERENT AQ
            "\u200D", // ZERO WIDTH JOINER
            "\u202E", // RIGHT-TO-LEFT OVERRIDE
            "\u2065", // reserved bidi format code point
            "\uFE0F", // VARIATION SELECTOR-16
            String(Character.toChars(0xE0001)), // LANGUAGE TAG
        )
        samples.forEach { text ->
            /** Layout whose source range must retain the exact invisible scalar. */
            val layout = layout(
                spans = listOf(PixelTextSpan(text, PixelTextStyle(textRasterizer = legacyRasterizer))),
                availableWidth = 10,
            )
            /** Ignorable cluster retained for offset mapping without a paint payload. */
            val cluster = layout.lines.single().visualClusters.single()

            assertEquals(text.length, layout.lines.single().sourceEnd)
            assertEquals("", cluster.renderText)
            assertEquals(0, cluster.width)
            assertEquals(0, layout.width)
        }
    }

    /** Proves LF, CRLF, NEL, LS and PS preserve leading, consecutive and trailing empty lines. */
    @Test
    fun everyUnicodeHardBreakPreservesExactSourceLineRanges() {
        /** Source covering all required hard-break forms with empty edges. */
        val text = "\nA\r\nB\u0085\u2028\u2029"
        /** Layout with no soft wrapping so only hard breaks create lines. */
        val layout = layout(
            spans = listOf(PixelTextSpan(text, PixelTextStyle(textRasterizer = clusterRasterizer))),
            availableWidth = 20,
            softWrap = false,
        )

        assertEquals(6, layout.lines.size)
        assertEquals(
            listOf(0 to 0, 1 to 2, 4 to 5, 6 to 6, 7 to 7, 8 to 8),
            layout.lines.map { line -> line.sourceStart to line.sourceEnd },
        )
        assertEquals(
            listOf("", "A", "B", "", "", ""),
            layout.lines.map { line ->
                line.visualClusters.joinToString(separator = "") { cluster -> cluster.sourceText }
            },
        )
    }

    /** Proves mixed Latin, Hebrew and numbers use UAX #9 visual runs while retaining logical ranges. */
    @Test
    fun mixedBidiReordersClustersAndKeepsNumbersLeftToRight() {
        /** Canonical mixed-direction acceptance string from the Goal. */
        val text = "ABC אבג 123"
        /** LTR-base layout with one pixel per grapheme. */
        val layout = layout(
            spans = listOf(PixelTextSpan(text, PixelTextStyle(textRasterizer = clusterRasterizer))),
            availableWidth = 40,
            textDirection = TextDirection.LTR,
        )
        /** Final left-to-right visual cluster stream. */
        val visualText = layout.lines.single().visualClusters.joinToString(separator = "") { cluster ->
            cluster.renderText
        }

        assertEquals("ABC 123 גבא", visualText)
        assertEquals(0, layout.lines.single().sourceStart)
        assertEquals(text.length, layout.lines.single().sourceEnd)
        assertTrue(
            layout.lines.single().visualClusters
                .filter { cluster -> cluster.sourceText in listOf("א", "ב", "ג") }
                .all { cluster -> cluster.isRightToLeft },
        )
    }

    /** Proves paired punctuation selects mirrored paint glyphs without changing logical source text. */
    @Test
    fun rtlPairedPunctuationMirrorsOnlyThePaintPayload() {
        /** Parenthesized Hebrew source whose backing order must remain unchanged. */
        val text = "(אב)"
        /** RTL-base visual layout. */
        val layout = layout(
            spans = listOf(PixelTextSpan(text, PixelTextStyle(textRasterizer = clusterRasterizer))),
            availableWidth = 20,
            textDirection = TextDirection.RTL,
        )
        /** Reordered and mirrored paint payload. */
        val renderText = layout.lines.single().visualClusters.joinToString(separator = "") { cluster ->
            cluster.renderText
        }
        /** Source payload read back in logical order. */
        val logicalText = layout.lines.single().visualClusters
            .sortedBy { cluster -> cluster.sourceStart }
            .joinToString(separator = "") { cluster -> cluster.sourceText }

        assertEquals("(בא)", renderText)
        assertEquals(text, logicalText)
    }

    /** Proves distinct synthetic glyph calls cover pure/mixed RTL, clusters, breaks and ellipsis. */
    @Test
    fun distinguishableGlyphPainterCoversBidiAndClusterAcceptanceMatrix() {
        /** Cluster-aware recorder whose call payload distinguishes every painted layout unit. */
        val rasterizer = DistinguishableClusterRasterizer()
        /** Source, base direction and exact left-to-right glyph call stream. */
        val cases = listOf(
            BidiPaintCase("אבג", TextDirection.RTL, listOf("ג", "ב", "א")),
            BidiPaintCase("ABC אבג 123", TextDirection.LTR, listOf("A", "B", "C", " ", "1", "2", "3", " ", "ג", "ב", "א")),
            BidiPaintCase("אבג ABC 123", TextDirection.RTL, listOf("A", "B", "C", " ", "1", "2", "3", " ", "ג", "ב", "א")),
            BidiPaintCase("(אב)", TextDirection.RTL, listOf("(", "ב", "א", ")")),
            BidiPaintCase("e\u0301X", TextDirection.LTR, listOf("e\u0301", "X")),
            BidiPaintCase("👨‍👩‍👧‍👦A", TextDirection.LTR, listOf("👨‍👩‍👧‍👦", "A")),
        )
        cases.forEach { case ->
            rasterizer.calls.clear()
            /** One paragraph using the recorder as both style and inherited rasterizer. */
            val paragraph = layout(
                spans = listOf(PixelTextSpan(case.source, PixelTextStyle(textRasterizer = rasterizer))),
                availableWidth = 80,
                softWrap = false,
                textDirection = case.direction,
            )
            paintParagraph(paragraph, rasterizer)
            assertEquals(case.expectedGlyphs, rasterizer.calls.map { call -> call.text })
            assertEquals(case.expectedGlyphs.indices.toList(), rasterizer.calls.map { call -> call.x })
        }

        rasterizer.calls.clear()
        /** Explicit hard break must restart paint x while preserving logical line source ranges. */
        val multiline = layout(
            spans = listOf(PixelTextSpan("אב\nABC", PixelTextStyle(textRasterizer = rasterizer))),
            availableWidth = 80,
            softWrap = false,
            textDirection = TextDirection.RTL,
        )
        paintParagraph(multiline, rasterizer)
        assertEquals(listOf("ב", "א", "A", "B", "C"), rasterizer.calls.map { call -> call.text })
        assertEquals(listOf(0, 0, 1, 1, 1), rasterizer.calls.map { call -> call.y })

        rasterizer.calls.clear()
        /** Ellipsis removes only whole Hebrew clusters and paints three distinguishable synthetic dots. */
        val ellipsized = PixelParagraphLayouter.layout(
            input = PixelParagraphInput(
                spans = listOf(PixelTextSpan("אבגדה", PixelTextStyle(textRasterizer = rasterizer))),
                textAlign = PixelTextAlign.START,
                textDirection = TextDirection.RTL,
                softWrap = false,
                overflow = PixelTextOverflow.ELLIPSIS,
                maxLines = 1,
                defaultTextRasterizer = rasterizer,
            ),
            availableWidth = 4,
        )
        paintParagraph(ellipsized, rasterizer)
        assertEquals(listOf(".", ".", ".", "א"), rasterizer.calls.map { call -> call.text })
        assertTrue(ellipsized.lines.single().visualClusters.all { cluster -> cluster.sourceStart != 1 })
    }

    /** Proves caret, hit-test and partial selection all consume the same visual cluster geometry. */
    @Test
    fun renderTextUsesSharedBidiGeometryForCaretHitTestAndSelection() {
        /** Canonical mixed string with known one-cell cluster positions. */
        val text = "ABC אבג 123"
        /** Production RenderText using the same paragraph object as TextField. */
        val renderText = RenderText(
            text = text,
            style = PixelTextStyle(textRasterizer = clusterRasterizer),
            textAlign = PixelTextAlign.START,
            textDirection = TextDirection.LTR,
            softWrap = false,
            overflow = PixelTextOverflow.CLIP,
            maxLines = 1,
            defaultTextRasterizer = clusterRasterizer,
        )
        renderText.layout(RenderConstraints(maxWidth = 40, maxHeight = 4))

        /** Logical boundary before Hebrew has distinct downstream and upstream visual positions. */
        val dualCaretXs = renderText.caretRects(4).map { caret -> caret.x }
        /** Partial logical range crossing Bidi levels becomes disjoint visual rectangles. */
        val selectionRects = renderText.textRangeRects(5, 9)

        assertEquals(listOf(11, 4), dualCaretXs)
        assertEquals(11, renderText.caretRect(4, PixelTextAffinity.DOWNSTREAM).x)
        assertEquals(4, renderText.caretRect(4, PixelTextAffinity.UPSTREAM).x)
        assertEquals(4, renderText.textIndexAt(localX = 10, localY = 0))
        assertEquals(6, renderText.textIndexAt(localX = 8, localY = 0))
        assertEquals(2, selectionRects.size)
        assertEquals(listOf(4, 7), selectionRects.map { rect -> rect.x })
        assertEquals(listOf(1, 3), selectionRects.map { rect -> rect.width })
    }

    /** Proves Android UTF-16 slots reuse atomic grapheme and hard-break paragraph geometry. */
    @Test
    fun accessibilityCharacterRectsShareClusterAndHardBreakGeometry() {
        /** Combining, supplementary, CRLF, and trailing visible clusters in one backing String. */
        val text = "e\u0301🙂\r\nA"
        /** Production paragraph object queried by both semantics and TextField targets. */
        val renderText = RenderText(
            text = text,
            style = PixelTextStyle(textRasterizer = clusterRasterizer),
            textAlign = PixelTextAlign.START,
            textDirection = TextDirection.LTR,
            softWrap = true,
            overflow = PixelTextOverflow.CLIP,
            maxLines = 2,
            defaultTextRasterizer = clusterRasterizer,
        )
        renderText.layout(RenderConstraints(maxWidth = 20, maxHeight = 4))

        /** Two beyond-text slots verify Android's required nullable fixed-length response. */
        val rectangles = renderText.textCharacterRects(start = 0, length = text.length + 2)

        assertEquals(text.length + 2, rectangles.size)
        assertEquals(rectangles[0], rectangles[1])
        assertEquals(rectangles[2], rectangles[3])
        assertTrue(rectangles[0] != rectangles[2])
        assertEquals(rectangles[4], rectangles[5])
        assertTrue(requireNotNull(rectangles[6]).y > requireNotNull(rectangles[4]).y)
        assertEquals(null, rectangles[7])
        assertEquals(null, rectangles[8])
    }

    /** Proves paragraph measurement and cluster painting retain rasterizer-native pair spacing. */
    @Test
    fun paragraphPaintingMatchesDirectRasterizerPairGeometry() {
        /** Default bitmap font whose one-pixel pair spacing exposed the former geometry split. */
        val rasterizer = PixelBitmapFont.Default
        /** Same-style source that must remain visually identical when painted cluster by cluster. */
        val text = "RED"
        /** Paragraph layout carrying one measured cluster entry per ASCII grapheme. */
        val layout = PixelParagraphLayouter.layout(
            input = PixelParagraphInput(
                spans = listOf(PixelTextSpan(text, PixelTextStyle(textRasterizer = rasterizer))),
                textAlign = PixelTextAlign.START,
                textDirection = TextDirection.LTR,
                softWrap = false,
                overflow = PixelTextOverflow.CLIP,
                maxLines = 1,
                defaultTextRasterizer = rasterizer,
            ),
            availableWidth = 64,
        )
        /** Direct rasterizer output defining its native glyph-pair geometry. */
        val direct = PixelBuffer(width = 64, height = rasterizer.measureHeight(text))
        /** Paragraph output that must use the same positions for measure, paint and hit testing. */
        val paragraph = PixelBuffer(width = direct.width, height = direct.height)
        rasterizer.drawText(direct, text, x = 0, y = 0, color = PixelColor.White)
        PixelParagraphPainter.drawRun(
            buffer = paragraph,
            bufferPool = PixelBufferPool(),
            run = layout.lines.single().runs.single(),
            defaultTextRasterizer = rasterizer,
            x = 0,
            y = 0,
        )

        assertEquals(rasterizer.measureText(text), layout.width)
        assertArrayEquals(direct.pixels, paragraph.pixels)
    }

    /** 证明长段落软换行只按簇线性测量，不会随当前行长度反复整段重测。 */
    @Test
    fun softWrapMeasurementCountRemainsLinear() {
        /** 记录每次宽度测量且让每个 ASCII 簇恰好占一个单元的栅格器。 */
        val rasterizer = CountingUnitClusterRasterizer()
        /** 足以暴露旧 O(n²) 候选列表重测的固定簇数量。 */
        val clusterCount = 200
        /** 每行二十簇、共十行的确定性布局。 */
        val paragraph = layout(
            spans = listOf(
                PixelTextSpan(
                    text = "A".repeat(clusterCount),
                    style = PixelTextStyle(textRasterizer = rasterizer),
                ),
            ),
            availableWidth = 20,
            softWrap = true,
        )

        assertEquals(10, paragraph.lines.size)
        assertTrue(rasterizer.measureTextCalls <= clusterCount * 3)
    }

    /** 证明缩放 glyph 临时缓冲在同一帧池中复用，而不是逐簇新建像素数组。 */
    @Test
    fun scaledParagraphGlyphsReuseFrameBufferPool() {
        /** 两个簇都触发非 plain 样式缩放路径的一单元栅格器。 */
        val rasterizer = UnitClusterRasterizer()
        /** 带二倍字号的单行布局。 */
        val paragraph = layout(
            spans = listOf(
                PixelTextSpan(
                    text = "AB",
                    style = PixelTextStyle(textRasterizer = rasterizer, fontScale = 2),
                ),
            ),
            availableWidth = 16,
            softWrap = false,
        )
        /** 跨两次绘制共享并暴露命中统计的帧级缓冲池。 */
        val bufferPool = PixelBufferPool()
        /** 足够容纳两次相同缩放输出的目标缓冲。 */
        val buffer = PixelBuffer(width = 16, height = 4)
        /** 唯一样式 run，包含两个待缩放簇。 */
        val run = paragraph.lines.single().runs.single()

        repeat(2) {
            PixelParagraphPainter.drawRun(
                buffer = buffer,
                bufferPool = bufferPool,
                run = run,
                defaultTextRasterizer = rasterizer,
                x = 0,
                y = 0,
            )
        }

        /** 第一个 glyph 创建桶，另外三个 glyph 均复用同尺寸缓冲。 */
        val stats = bufferPool.stats()
        assertEquals(1L, stats.misses)
        assertEquals(3L, stats.hits)
        assertTrue(buffer.getPixel(0, 0) != PixelColor.Transparent)
    }

    /** Creates one paragraph request with explicit deterministic defaults. */
    private fun layout(
        spans: List<PixelTextSpan>,
        availableWidth: Int,
        softWrap: Boolean = true,
        textDirection: TextDirection = TextDirection.LTR,
    ): PixelParagraphLayout {
        return PixelParagraphLayouter.layout(
            input = PixelParagraphInput(
                spans = spans,
                textAlign = PixelTextAlign.START,
                textDirection = textDirection,
                softWrap = softWrap,
                overflow = PixelTextOverflow.CLIP,
                maxLines = Int.MAX_VALUE,
                defaultTextRasterizer = clusterRasterizer,
            ),
            availableWidth = availableWidth,
        )
    }

    /** Paints every line/run at its shared visual geometry into a disposable buffer. */
    private fun paintParagraph(
        layout: PixelParagraphLayout,
        rasterizer: PixelTextRasterizer,
    ) {
        /** Buffer large enough for every one-cell synthetic acceptance case. */
        val buffer = PixelBuffer(width = 128, height = layout.height.coerceAtLeast(1))
        /** Vertical origin of the next physical line. */
        var lineY = 0
        layout.lines.forEach { line ->
            /** Horizontal origin advanced across adjacent style runs. */
            var runX = 0
            line.runs.forEach { run ->
                PixelParagraphPainter.drawRun(
                    buffer = buffer,
                    bufferPool = PixelBufferPool(),
                    run = run,
                    defaultTextRasterizer = rasterizer,
                    x = runX,
                    y = lineY,
                )
                runX += run.width
            }
            lineY += line.height
        }
    }

    /** One distinguishable-glyph Bidi acceptance scenario. */
    private data class BidiPaintCase(
        /** Exact logical source text. */
        val source: String,
        /** Explicit paragraph base direction. */
        val direction: TextDirection,
        /** Expected left-to-right glyph payload sequence. */
        val expectedGlyphs: List<String>,
    )

    /** One recorded atomic paint call. */
    private data class PaintedGlyph(
        /** Exact cluster or synthetic payload given to the rasterizer. */
        val text: String,
        /** Visual left edge. */
        val x: Int,
        /** Physical line top. */
        val y: Int,
    )

    /** Cluster-aware one-cell rasterizer that records every distinct glyph payload and position. */
    private class DistinguishableClusterRasterizer : PixelClusterTextRasterizer {
        /** Ordered paint calls retained for exact visual-order assertions. */
        val calls: MutableList<PaintedGlyph> = mutableListOf()

        /** Every complete cluster occupies one cell. */
        override fun measureText(text: String): Int = PixelGraphemeBoundaryMap(text).graphemeCount

        /** Every sample occupies one row. */
        override fun measureHeight(text: String): Int = 1

        /** Every non-empty synthetic test cluster is intentionally supported. */
        override fun canRasterizeCluster(cluster: String): Boolean = cluster.isNotEmpty()

        /** Records one distinguishable cluster glyph and paints its one-cell marker. */
        override fun drawText(
            buffer: PixelBuffer,
            text: String,
            x: Int,
            y: Int,
            color: PixelColor,
        ) {
            calls += PaintedGlyph(text = text, x = x, y = y)
            if (text.isNotEmpty()) buffer.setPixel(x, y, color)
        }
    }

    /** Cluster-aware one-cell rasterizer used to make visual order directly observable. */
    private class UnitClusterRasterizer : PixelClusterTextRasterizer {
        /** Every complete grapheme in a scalar sequence occupies one logical pixel. */
        override fun measureText(text: String): Int = PixelGraphemeBoundaryMap(text).graphemeCount

        /** Every sample occupies one row. */
        override fun measureHeight(text: String): Int = 1

        /** Every non-empty grapheme is intentionally supported by this synthetic rasterizer. */
        override fun canRasterizeCluster(cluster: String): Boolean = cluster.isNotEmpty()

        /** Returns one-row metrics matching the one-cell synthetic bitmap. */
        override fun fontMetrics(text: String): PixelFontMetrics {
            return PixelFontMetrics(1, 0, 0, 1, 0, 0)
        }

        /** Paints one white cell for any non-empty accepted cluster. */
        override fun drawText(
            buffer: PixelBuffer,
            text: String,
            x: Int,
            y: Int,
            color: PixelColor,
        ) {
            if (text.isNotEmpty()) buffer.setPixel(x, y, color)
        }
    }

    /** 统计宽度测量次数的一单元 cluster 栅格器。 */
    private class CountingUnitClusterRasterizer : PixelClusterTextRasterizer {
        /** 本次布局累计进入宽度测量协议的次数。 */
        var measureTextCalls: Int = 0
            private set

        /** 每次调用递增计数，并以 UTF-16 长度模拟可加的单元宽度。 */
        override fun measureText(text: String): Int {
            measureTextCalls += 1
            return text.length
        }

        /** 所有测试样本固定占一行。 */
        override fun measureHeight(text: String): Int = 1

        /** 该测试只输入非空 ASCII cluster，全部声明为原子支持。 */
        override fun canRasterizeCluster(cluster: String): Boolean = cluster.isNotEmpty()

        /** 性能计数测试不检查像素，仅满足栅格器绘制协议。 */
        override fun drawText(
            buffer: PixelBuffer,
            text: String,
            x: Int,
            y: Int,
            color: PixelColor,
        ) = Unit
    }

    /** Char-era rasterizer used to verify deterministic multi-code-point fallback. */
    private class LegacyUnitRasterizer : PixelTextRasterizer {
        /** Every fallback or supported scalar occupies one cell. */
        override fun measureText(text: String): Int {
            return Character.codePointCount(text, 0, text.length)
        }

        /** Every sample occupies one row. */
        override fun measureHeight(text: String): Int = 1

        /** Paints one cell per scalar received after paragraph fallback resolution. */
        override fun drawText(
            buffer: PixelBuffer,
            text: String,
            x: Int,
            y: Int,
            color: PixelColor,
        ) {
            /** Horizontal cell receiving the next scalar. */
            var cursorX = x
            /** UTF-16 offset advanced across complete scalars. */
            var offset = 0
            while (offset < text.length) {
                buffer.setPixel(cursorX++, y, color)
                /** Scalar used only to advance past a supplementary pair. */
                val codePoint = Character.codePointAt(text, offset)
                offset += Character.charCount(codePoint)
            }
        }
    }
}
