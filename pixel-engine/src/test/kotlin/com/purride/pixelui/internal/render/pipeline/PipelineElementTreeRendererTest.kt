package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelcore.PixelTone
import com.purride.pixelui.Alignment
import com.purride.pixelui.Align
import com.purride.pixelui.Center
import com.purride.pixelui.Container
import com.purride.pixelui.DecoratedBox
import com.purride.pixelui.Directionality
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.InternalBuildContext
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextDirection
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.Widget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 新 pipeline 首批能力的最小回归测试。
 */
class PipelineElementTreeRendererTest {

    /**
     * 文本对象能够在单行模式下完成测量并输出像素。
     */
    @Test
    fun renderTextMeasuresAndPaintsSingleLineText() {
        val renderText = RenderText(
            text = "A",
            style = com.purride.pixelui.PixelTextStyle.Default,
            textAlign = PixelTextAlign.CENTER,
            textDirection = TextDirection.LTR,
            softWrap = false,
            overflow = TextOverflow.CLIP,
            maxLines = 1,
            defaultTextRasterizer = PixelBitmapFont.Default,
            occupyFullWidth = true,
        )

        renderText.layout(
            constraints = RenderConstraints(
                maxWidth = 12,
                maxHeight = 7,
            ),
        )
        val buffer = PixelBuffer(width = 12, height = 7).also { it.clear() }
        renderText.paint(
            context = PaintContext(buffer = buffer),
            offsetX = 0,
            offsetY = 0,
        )

        val pixels = collectActivePixels(buffer)
        assertEquals(12, renderText.size.width)
        assertTrue(pixels.isNotEmpty())
        assertTrue(pixels.minOf { it.first } >= 3)
        assertTrue(pixels.maxOf { it.first } <= 8)
    }

    /**
     * 表面对象能够绘制背景、边框、padding 并导出点击目标。
     */
    @Test
    fun renderSurfacePaintsFillBorderPaddingAndClickTarget() {
        val renderSurface = RenderSurface(
            child = RenderText(
                text = "A",
                style = com.purride.pixelui.PixelTextStyle.Default,
                textAlign = PixelTextAlign.START,
                textDirection = TextDirection.LTR,
                softWrap = false,
                overflow = TextOverflow.CLIP,
                maxLines = 1,
                defaultTextRasterizer = PixelBitmapFont.Default,
            ),
            fillTone = PixelTone.OFF,
            borderTone = PixelTone.ACCENT,
            alignment = PixelAlignment.TOP_START,
            explicitWidth = 12,
            explicitHeight = 8,
            contentPaddingLeft = 2,
            contentPaddingTop = 1,
            contentPaddingRight = 2,
            contentPaddingBottom = 1,
            onClick = { },
        )

        renderSurface.layout(
            constraints = RenderConstraints(
                maxWidth = 12,
                maxHeight = 8,
            ),
        )
        val buffer = PixelBuffer(width = 12, height = 8).also { it.clear() }
        renderSurface.paint(
            context = PaintContext(buffer = buffer),
            offsetX = 0,
            offsetY = 0,
        )
        val clickTargets = mutableListOf<PixelClickTarget>()
        renderSurface.collectClickTargets(
            offsetX = 0,
            offsetY = 0,
            targets = clickTargets,
        )

        val textPixels = collectPixelsWithTone(
            buffer = buffer,
            tone = PixelTone.ON.value,
        )
        assertEquals(PixelTone.ACCENT.value, buffer.getPixel(0, 0))
        assertEquals(PixelTone.OFF.value, buffer.getPixel(1, 1))
        assertTrue(textPixels.isNotEmpty())
        assertTrue(textPixels.minOf { it.first } >= 2)
        assertEquals(1, clickTargets.size)
        assertEquals(12, clickTargets.single().bounds.width)
        assertEquals(8, clickTargets.single().bounds.height)
    }

    /**
     * owner 能够驱动根对象完成 layout、paint 和点击目标导出。
     */
    @Test
    fun pipelineOwnerLayoutsAndPaintsRoot() {
        val owner = PipelineOwner(
            root = RenderSurface(
                child = RenderText(
                    text = "A",
                    style = com.purride.pixelui.PixelTextStyle.Default,
                    textAlign = PixelTextAlign.START,
                    textDirection = TextDirection.LTR,
                    softWrap = false,
                    overflow = TextOverflow.CLIP,
                    maxLines = 1,
                    defaultTextRasterizer = PixelBitmapFont.Default,
                ),
                fillTone = PixelTone.OFF,
                borderTone = PixelTone.ON,
                alignment = PixelAlignment.TOP_START,
                explicitWidth = 10,
                explicitHeight = 8,
                contentPaddingLeft = 1,
                contentPaddingTop = 1,
                contentPaddingRight = 1,
                contentPaddingBottom = 1,
                onClick = { },
            ),
        )

        val result = owner.render(
            logicalWidth = 10,
            logicalHeight = 8,
        )

        assertEquals(PixelTone.ON.value, result.buffer.getPixel(0, 0))
        assertTrue(collectActivePixels(result.buffer).isNotEmpty())
        assertEquals(1, result.clickTargets.size)
    }

    /**
     * 完全受支持的 Widget 树应该能直接走新 pipeline。
     */
    @Test
    fun pipelineElementTreeRendererRendersSupportedTree() {
        val result = renderWithPipeline(
            root = Center(
                child = Container(
                    width = 24,
                    height = 16,
                    padding = EdgeInsets.all(2),
                    fillTone = PixelTone.OFF,
                    borderTone = PixelTone.ACCENT,
                    alignment = Alignment.CENTER,
                    child = Column(
                        spacing = 1,
                        mainAxisSize = MainAxisSize.MIN,
                        mainAxisAlignment = MainAxisAlignment.CENTER,
                        crossAxisAlignment = CrossAxisAlignment.CENTER,
                        children = listOf(
                            Text(
                                data = "PIPE",
                                textAlign = TextAlign.CENTER,
                            ),
                            Row(
                                spacing = 1,
                                mainAxisSize = MainAxisSize.MIN,
                                mainAxisAlignment = MainAxisAlignment.CENTER,
                                crossAxisAlignment = CrossAxisAlignment.CENTER,
                                children = listOf(
                                    Text("A"),
                                    Text("B"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            logicalWidth = 24,
            logicalHeight = 16,
        )

        assertNotNull(result)
        result ?: return
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(0, 0))
        assertTrue(collectActivePixels(result.buffer).isNotEmpty())
    }

    /**
     * 直接持有 render object 的 widget 根节点应该能直接交给 pipeline。
     */
    @Test
    fun pipelineElementTreeRendererRendersDirectRenderObjectRootThroughPipeline() {
        val renderer = PipelineElementTreeRenderer()
        val result = withRenderRequest(
            root = Text("DIRECT"),
            logicalWidth = 32,
            logicalHeight = 8,
        ) { request ->
            assertTrue(renderer.canRender(request))
            renderer.renderOrNull(request)
        }

        assertNotNull(result)
        result ?: return
        assertTrue(collectActivePixels(result.buffer).isNotEmpty())
    }

    /**
     * 直接 `DecoratedBox + Text` 子树应该完整走 retained render object 主链。
     */
    @Test
    fun pipelineElementTreeRendererRendersDirectSurfaceTreeThroughPipeline() {
        val renderer = PipelineElementTreeRenderer()
        val result = withRenderRequest(
            root = DecoratedBox(
                fillTone = PixelTone.OFF,
                borderTone = PixelTone.ACCENT,
                padding = 1,
                alignment = Alignment.TOP_START,
                child = Text("SURFACE"),
            ),
            logicalWidth = 32,
            logicalHeight = 8,
        ) { request ->
            assertTrue(renderer.canRender(request))
            renderer.renderOrNull(request)
        }

        assertNotNull(result)
        result ?: return
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(0, 0))
        assertTrue(collectActivePixels(result.buffer).isNotEmpty())
    }

    /**
     * `Align + Padding + DecoratedBox + Text` 应该能完整走 direct render object 子树。
     */
    @Test
    fun pipelineElementTreeRendererRendersDirectAlignedPaddingTreeThroughPipeline() {
        val renderer = PipelineElementTreeRenderer()
        val result = withRenderRequest(
            root = Align(
                alignment = Alignment.TOP_START,
                child = com.purride.pixelui.Padding(
                    padding = EdgeInsets.all(1),
                    child = DecoratedBox(
                        fillTone = PixelTone.OFF,
                        borderTone = PixelTone.ACCENT,
                        padding = 1,
                        alignment = Alignment.TOP_START,
                        child = Text("PAD"),
                    ),
                ),
            ),
            logicalWidth = 24,
            logicalHeight = 10,
        ) { request ->
            assertTrue(renderer.canRender(request))
            renderer.renderOrNull(request)
        }

        assertNotNull(result)
        result ?: return
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(1, 1))
        assertTrue(collectActivePixels(result.buffer).isNotEmpty())
    }

    /**
     * Text 的 softWrap 配置应该继续走 direct pipeline。
     */
    @Test
    fun pipelineElementTreeRendererRendersSoftWrapTextThroughDirectPipeline() {
        val renderer = createPipelineRenderer()
        val result = withRenderRequest(
            root = SizedBox(
                width = 24,
                height = 12,
                child = Text(
                    data = "A B C D E F",
                    softWrap = true,
                    maxLines = 2,
                ),
            ),
            logicalWidth = 24,
            logicalHeight = 12,
        ) { request ->
            assertTrue(renderer.canRender(request))
            renderer.renderOrNull(request)
        }

        assertNotNull(result)
    }

    /**
     * 单行 ellipsis 应该在 render object 层统一处理，而不是依赖 demo 手写裁剪。
     */
    @Test
    fun renderTextEllipsizesSingleLineTextWhenWidthIsConstrained() {
        val rasterizer = CapturingRasterizer()
        val renderText = RenderText(
            text = "ABCDE",
            style = com.purride.pixelui.PixelTextStyle(textRasterizer = rasterizer),
            textAlign = PixelTextAlign.START,
            textDirection = TextDirection.LTR,
            softWrap = false,
            overflow = TextOverflow.ELLIPSIS,
            maxLines = 1,
            defaultTextRasterizer = rasterizer,
            explicitWidth = 4,
            explicitHeight = 1,
        )

        renderText.layout(RenderConstraints(maxWidth = 4, maxHeight = 1))
        renderText.paint(
            context = PaintContext(buffer = PixelBuffer(width = 4, height = 1)),
            offsetX = 0,
            offsetY = 0,
        )

        assertEquals("A...", rasterizer.lastDrawnText)
    }

    /**
     * CLIP 模式应该保持原始文本，只在绘制阶段裁剪可见区域。
     */
    @Test
    fun renderTextClipKeepsOriginalSingleLineText() {
        val rasterizer = CapturingRasterizer()
        val renderText = RenderText(
            text = "ABCDE",
            style = com.purride.pixelui.PixelTextStyle(textRasterizer = rasterizer),
            textAlign = PixelTextAlign.START,
            textDirection = TextDirection.LTR,
            softWrap = false,
            overflow = TextOverflow.CLIP,
            maxLines = 1,
            defaultTextRasterizer = rasterizer,
            explicitWidth = 4,
            explicitHeight = 1,
        )

        renderText.layout(RenderConstraints(maxWidth = 4, maxHeight = 1))
        renderText.paint(
            context = PaintContext(buffer = PixelBuffer(width = 4, height = 1)),
            offsetX = 0,
            offsetY = 0,
        )

        assertEquals("ABCDE", rasterizer.lastDrawnText)
    }

    /**
     * softWrap 开启后应该按字符测宽换行，并尊重 maxLines。
     */
    @Test
    fun renderTextWrapsMixedTextByCharacterWhenSoftWrapIsEnabled() {
        val rasterizer = CapturingRasterizer()
        val renderText = RenderText(
            text = "AB你好CD",
            style = com.purride.pixelui.PixelTextStyle(textRasterizer = rasterizer),
            textAlign = PixelTextAlign.START,
            textDirection = TextDirection.LTR,
            softWrap = true,
            overflow = TextOverflow.CLIP,
            maxLines = 3,
            defaultTextRasterizer = rasterizer,
            explicitWidth = 3,
            explicitHeight = 3,
        )

        renderText.layout(RenderConstraints(maxWidth = 3, maxHeight = 3))
        renderText.paint(
            context = PaintContext(buffer = PixelBuffer(width = 3, height = 3)),
            offsetX = 0,
            offsetY = 0,
        )

        assertEquals("AB你\n好CD", rasterizer.lastDrawnText)
    }

    /**
     * 多行 ellipsis 只作用于最后一个可见行。
     */
    @Test
    fun renderTextEllipsizesLastVisibleLineWhenWrappedTextExceedsMaxLines() {
        val rasterizer = CapturingRasterizer()
        val renderText = RenderText(
            text = "ABCDEFGHI",
            style = com.purride.pixelui.PixelTextStyle(textRasterizer = rasterizer),
            textAlign = PixelTextAlign.START,
            textDirection = TextDirection.LTR,
            softWrap = true,
            overflow = TextOverflow.ELLIPSIS,
            maxLines = 2,
            defaultTextRasterizer = rasterizer,
            explicitWidth = 4,
            explicitHeight = 2,
        )

        renderText.layout(RenderConstraints(maxWidth = 4, maxHeight = 2))
        renderText.paint(
            context = PaintContext(buffer = PixelBuffer(width = 4, height = 2)),
            offsetX = 0,
            offsetY = 0,
        )

        assertEquals("ABCD\nE...", rasterizer.lastDrawnText)
    }

    /**
     * 含权重 child 的 flex 树现在应该直接走 pipeline。
     */
    @Test
    fun pipelineElementTreeRendererRendersWeightedRowTree() {
        val renderer = createPipelineRenderer()
        val result = withRenderRequest(
            root = SizedBox(
                width = 24,
                height = 12,
                child = Row(
                    children = listOf(
                        Expanded(
                            child = Container(
                                fillTone = PixelTone.OFF,
                                borderTone = PixelTone.ON,
                            ),
                        ),
                        Text("B"),
                    ),
                ),
            ),
            logicalWidth = 24,
            logicalHeight = 12,
        ) { request ->
            assertTrue(renderer.canRender(request))
            renderer.renderOrNull(request)
        }

        assertNotNull(result)
    }

    /**
     * 首批支持的 row/column 基础排布应该能继续走新 pipeline。
     */
    @Test
    fun pipelineElementTreeRendererRendersBasicFlexAlignments() {
        val renderer = createPipelineRenderer()
        val result = withRenderRequest(
            root = SizedBox(
                width = 28,
                height = 16,
                child = Column(
                    mainAxisSize = MainAxisSize.MAX,
                    mainAxisAlignment = MainAxisAlignment.SPACE_EVENLY,
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    children = listOf(
                        Container(
                            height = 3,
                            fillTone = PixelTone.ON,
                            borderTone = null,
                        ),
                        Row(
                            mainAxisAlignment = MainAxisAlignment.SPACE_BETWEEN,
                            crossAxisAlignment = CrossAxisAlignment.CENTER,
                            children = listOf(
                                Container(
                                    width = 3,
                                    height = 3,
                                    fillTone = PixelTone.ACCENT,
                                    borderTone = null,
                                ),
                                Container(
                                    width = 3,
                                    height = 3,
                                    fillTone = PixelTone.ON,
                                    borderTone = null,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            logicalWidth = 28,
            logicalHeight = 16,
        ) { request ->
            assertTrue(renderer.canRender(request))
            assertEquals(null, renderer.inspect(request).reason)
            renderer.renderOrNull(request)
        }

        assertNotNull(result)
        result ?: return
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(1, 3))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(0, 10))
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(27, 10))
    }

    /**
     * `Align/Center + GestureDetector` 这条常用链路也应该能完整走 pipeline。
     */
    @Test
    fun pipelineElementTreeRendererRendersAlignedClickableSurface() {
        val renderer = createPipelineRenderer()
        val result = withRenderRequest(
            root = SizedBox(
                width = 18,
                height = 10,
                child = Align(
                    alignment = Alignment.BOTTOM_END,
                    child = GestureDetector(
                        onTap = { },
                        child = Container(
                            width = 4,
                            height = 3,
                            fillTone = PixelTone.ACCENT,
                            borderTone = PixelTone.ON,
                        ),
                    ),
                ),
            ),
            logicalWidth = 18,
            logicalHeight = 10,
        ) { request ->
            assertTrue(renderer.canRender(request))
            assertEquals(null, renderer.inspect(request).reason)
            renderer.renderOrNull(request)
        }

        assertNotNull(result)
        result ?: return
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(14, 7))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(15, 8))
        assertEquals(1, result.clickTargets.size)
        assertEquals(14, result.clickTargets.single().bounds.left)
        assertEquals(7, result.clickTargets.single().bounds.top)
        assertEquals(4, result.clickTargets.single().bounds.width)
        assertEquals(3, result.clickTargets.single().bounds.height)
    }

    /**
     * 列表滚动后只应该导出可见 item 的点击目标。
     */
    @Test
    fun listViewportExportsOnlyVisibleClickTargetsAfterScrollOffset() {
        val controller = ScrollController()
        val state = controller.create(initialScrollOffsetPx = 10f)

        val result = renderWithPipeline(
            root = SizedBox(
                width = 24,
                height = 10,
                child = ListViewBuilder(
                    itemCount = 4,
                    state = state,
                    controller = controller,
                    itemBuilder = { index ->
                        SizedBox(
                            height = 8,
                            child = OutlinedButton(
                                text = "ITEM $index",
                                onPressed = { },
                            ),
                        )
                    },
                ),
            ),
            logicalWidth = 24,
            logicalHeight = 10,
        )

        assertNotNull(result)
        result ?: return
        assertEquals(2, result.clickTargets.size)
        assertTrue(result.clickTargets.all { target ->
            target.bounds.top in 0 until 10 &&
                target.bounds.top + target.bounds.height <= 10
        })
    }

    /**
     * renderer 应该复用长期 `PipelineOwner`，同一个 render root 重复渲染时不重复 attach。
     */
    @Test
    fun pipelineElementTreeRendererKeepsOwnerForStableRenderRoot() {
        val renderBox = CountingPipelineRenderBox()
        val renderer = createPipelineRenderer()

        withRenderRequest(
            root = FixedRenderObjectWidget(renderBox),
            logicalWidth = 8,
            logicalHeight = 6,
        ) { request ->
            renderer.render(request)
            renderer.render(request)
            assertEquals(1, renderBox.attachCount)
            assertEquals(1, renderBox.layoutCount)
            assertEquals(2, renderBox.paintCount)

            renderBox.requestLayout()
            renderer.render(request)
            assertEquals(2, renderBox.layoutCount)
            assertEquals(3, renderBox.paintCount)
        }
    }

    /**
     * 用 build runtime + pipeline renderer 跑一棵测试树。
     */
    private fun renderWithPipeline(
        root: Widget,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult? {
        return withRenderRequest(
            root = root,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
        ) { request ->
            createPipelineRenderer().renderOrNull(request)
        }
    }

    /**
     * 在 build runtime 生命周期内构造并消费统一的 render request。
     */
    private fun <T> withRenderRequest(
        root: Widget,
        logicalWidth: Int,
        logicalHeight: Int,
        block: (ElementTreeRenderRequest) -> T,
    ): T {
        val buildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = { },
            widgetAdapter = UnsupportedWidgetAdapter,
        )
        return try {
            block(
                ElementTreeRenderRequest(
                    root = buildRuntime.resolveElementTree(root),
                    logicalWidth = logicalWidth,
                    logicalHeight = logicalHeight,
                ),
            )
        } finally {
            buildRuntime.dispose()
        }
    }

    /**
     * 创建测试统一使用的 pipeline renderer。
     */
    private fun createPipelineRenderer(): PipelineElementTreeRenderer {
        return PipelineElementTreeRenderer()
    }

    /**
     * 测试用的固定 render object widget。
     */
    private class FixedRenderObjectWidget(
        private val renderObject: RenderObject,
    ) : RenderObjectWidget() {
        /**
         * 返回测试预置的 render object。
         */
        override fun createRenderObject(context: InternalBuildContext): RenderObject {
            return renderObject
        }
    }

    /**
     * 测试 renderer owner 复用的计数 render box。
     */
    private class CountingPipelineRenderBox : RenderBox() {
        var attachCount = 0
            private set
        var layoutCount = 0
            private set
        var paintCount = 0
            private set

        /**
         * 触发下一次 layout。
         */
        fun requestLayout() {
            markNeedsLayout()
        }

        /**
         * 记录 attach 次数。
         */
        override fun onAttach() {
            attachCount += 1
        }

        /**
         * 记录 layout 次数。
         */
        override fun layout(constraints: RenderConstraints) {
            layoutCount += 1
            size = RenderSize(
                width = constraints.constrainWidth(1),
                height = constraints.constrainHeight(1),
            )
        }

        /**
         * 记录 paint 次数。
         */
        override fun paint(
            context: PaintContext,
            offsetX: Int,
            offsetY: Int,
        ) {
            paintCount += 1
        }
    }

    private class CapturingRasterizer : PixelTextRasterizer {
        var lastDrawnText: String = ""
            private set

        override fun measureText(text: String): Int {
            return text.length
        }

        override fun measureHeight(text: String): Int {
            return 1
        }

        override fun drawText(
            buffer: PixelBuffer,
            text: String,
            x: Int,
            y: Int,
            value: Byte,
        ) {
            lastDrawnText = text
            text.forEachIndexed { index, _ ->
                if (x + index in 0 until buffer.width && y in 0 until buffer.height) {
                    buffer.setPixel(x = x + index, y = y, value = value)
                }
            }
        }
    }

    /**
     * 收集 buffer 里所有非背景像素，方便断言布局和绘制结果。
     */
    private fun collectActivePixels(buffer: PixelBuffer): List<Pair<Int, Int>> {
        return collectPixelsWithTone(
            buffer = buffer,
            tone = null,
        )
    }

    /**
     * 收集 buffer 里指定 tone 的像素；tone 为空时收集所有非背景像素。
     */
    private fun collectPixelsWithTone(
        buffer: PixelBuffer,
        tone: Byte?,
    ): List<Pair<Int, Int>> {
        val pixels = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until buffer.height) {
            for (x in 0 until buffer.width) {
                val value = buffer.getPixel(x, y)
                val matched = if (tone != null) {
                    value == tone
                } else {
                    value != PixelTone.OFF.value
                }
                if (matched) {
                    pixels += x to y
                }
            }
        }
        return pixels
    }
}
