package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelTone
import com.purride.pixelui.Alignment
import com.purride.pixelui.Center
import com.purride.pixelui.Container
import com.purride.pixelui.Directionality
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextDirection
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelAlignment
import com.purride.pixelui.internal.legacy.PixelTextAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
                    height = 12,
                    padding = EdgeInsets.all(2),
                    fillTone = PixelTone.OFF,
                    borderTone = PixelTone.ACCENT,
                    alignment = Alignment.CENTER,
                    child = Text(
                        data = "PIPE",
                        textAlign = TextAlign.CENTER,
                    ),
                ),
            ),
            logicalWidth = 24,
            logicalHeight = 12,
        )

        assertNotNull(result)
        result ?: return
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(0, 0))
        assertTrue(collectActivePixels(result.buffer).isNotEmpty())
    }

    /**
     * 只要树里出现首批不支持节点，就应该整树回退。
     */
    @Test
    fun pipelineElementTreeRendererReturnsNullForUnsupportedTree() {
        val result = renderWithPipeline(
            root = Directionality(
                textDirection = TextDirection.LTR,
                child = SizedBox(
                    width = 24,
                    height = 12,
                    child = Row(
                        children = listOf(
                            Text("A"),
                            Text("B"),
                        ),
                    ),
                ),
            ),
            logicalWidth = 24,
            logicalHeight = 12,
        )

        assertNull(result)
    }

    /**
     * 用 build runtime + pipeline renderer 跑一棵测试树。
     */
    private fun renderWithPipeline(
        root: Widget,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult? {
        val buildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = { },
            widgetAdapter = BridgeWidgetAdapter,
        )
        return try {
            val elementRoot = buildRuntime.resolveElementTree(root)
            PipelineElementTreeRenderer(
                bridgeTreeResolver = DefaultBridgeTreeResolver,
                defaultTextRasterizer = PixelBitmapFont.Default,
            ).renderOrNull(
                request = ElementTreeRenderRequest(
                    root = elementRoot,
                    logicalWidth = logicalWidth,
                    logicalHeight = logicalHeight,
                ),
            )
        } finally {
            buildRuntime.dispose()
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
