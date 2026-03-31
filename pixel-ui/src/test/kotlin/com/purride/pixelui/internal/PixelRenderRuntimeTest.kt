package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelAlignment
import com.purride.pixelui.PixelButton
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelBox
import com.purride.pixelui.PixelColumn
import com.purride.pixelui.PixelCrossAxisAlignment
import com.purride.pixelui.PixelList
import com.purride.pixelui.PixelMainAxisAlignment
import com.purride.pixelui.PixelModifier
import com.purride.pixelui.PixelPager
import com.purride.pixelui.PixelRow
import com.purride.pixelui.PixelSurface
import com.purride.pixelui.PixelText
import com.purride.pixelui.PixelTextField
import com.purride.pixelui.PixelTextFieldStyle
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.clickable
import com.purride.pixelui.fillMaxSize
import com.purride.pixelui.fillMaxWidth
import com.purride.pixelui.height
import com.purride.pixelui.padding
import com.purride.pixelui.size
import com.purride.pixelui.weight
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelcore.PixelTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelRenderRuntimeTest {

    private val runtime = PixelRenderRuntime()

    @Test
    fun surfaceCentersChildWithinPadding() {
        val result = runtime.render(
            root = PixelSurface(
                modifier = PixelModifier.Empty.size(20, 20),
                padding = 2,
                alignment = PixelAlignment.CENTER,
                borderTone = null,
                fillTone = PixelTone.OFF,
                child = PixelText("A"),
            ),
            logicalWidth = 20,
            logicalHeight = 20,
        )

        val pixels = collectOnPixels(result)
        val minX = pixels.minOf { it.first }
        val maxX = pixels.maxOf { it.first }
        val minY = pixels.minOf { it.second }
        assertTrue(minX >= 7)
        assertTrue(maxX <= 12)
        assertTrue(minY >= 6)
    }

    @Test
    fun columnStacksChildrenVerticallyWithSpacing() {
        val result = runtime.render(
            root = PixelColumn(
                spacing = 3,
                children = listOf(
                    PixelSurface(modifier = PixelModifier.Empty.size(10, 6)),
                    PixelSurface(modifier = PixelModifier.Empty.size(10, 6)),
                ),
            ),
            logicalWidth = 30,
            logicalHeight = 30,
        )

        val clickFreeBounds = collectFilledRows(result)
        assertTrue(clickFreeBounds.contains(0))
        assertTrue(clickFreeBounds.contains(5))
        assertTrue(clickFreeBounds.contains(9))
        assertTrue(clickFreeBounds.contains(14))
    }

    @Test
    fun rowWeightDistributesRemainingWidth() {
        val result = runtime.render(
            root = PixelRow(
                modifier = PixelModifier.Empty.size(20, 4),
                spacing = 2,
                children = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(4, 4),
                        borderTone = null,
                        fillTone = PixelTone.ON,
                    ),
                    PixelSurface(
                        modifier = PixelModifier.Empty.weight(1f).height(4),
                        borderTone = null,
                        fillTone = PixelTone.ACCENT,
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 4,
        )

        assertEquals(PixelTone.ON.value, result.buffer.getPixel(1, 1))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(18, 1))
    }

    @Test
    fun columnWeightDistributesRemainingHeight() {
        val result = runtime.render(
            root = PixelColumn(
                modifier = PixelModifier.Empty.size(6, 20),
                spacing = 2,
                children = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(6, 4),
                        borderTone = null,
                        fillTone = PixelTone.ON,
                    ),
                    PixelSurface(
                        modifier = PixelModifier.Empty.fillMaxWidth().weight(1f),
                        borderTone = null,
                        fillTone = PixelTone.ACCENT,
                    ),
                ),
            ),
            logicalWidth = 6,
            logicalHeight = 20,
        )

        assertEquals(PixelTone.ON.value, result.buffer.getPixel(1, 1))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(1, 18))
    }

    @Test
    fun rowCrossAxisCenterAlignsChildrenVertically() {
        val result = runtime.render(
            root = PixelRow(
                modifier = PixelModifier.Empty.size(12, 10),
                crossAxisAlignment = PixelCrossAxisAlignment.CENTER,
                children = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(4, 4),
                        borderTone = null,
                        fillTone = PixelTone.ON,
                    ),
                ),
            ),
            logicalWidth = 12,
            logicalHeight = 10,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(1, 2))
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(1, 3))
    }

    @Test
    fun columnCrossAxisEndAlignsChildrenHorizontally() {
        val result = runtime.render(
            root = PixelColumn(
                modifier = PixelModifier.Empty.size(10, 12),
                crossAxisAlignment = PixelCrossAxisAlignment.END,
                children = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(4, 4),
                        borderTone = null,
                        fillTone = PixelTone.ACCENT,
                    ),
                ),
            ),
            logicalWidth = 10,
            logicalHeight = 12,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(5, 1))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(6, 1))
    }

    @Test
    fun rowMainAxisCenterAlignsChildrenHorizontally() {
        val result = runtime.render(
            root = PixelRow(
                modifier = PixelModifier.Empty.size(12, 4),
                mainAxisAlignment = PixelMainAxisAlignment.CENTER,
                children = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(4, 4),
                        borderTone = null,
                        fillTone = PixelTone.ON,
                    ),
                ),
            ),
            logicalWidth = 12,
            logicalHeight = 4,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(3, 1))
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(4, 1))
    }

    @Test
    fun columnMainAxisEndAlignsChildrenVertically() {
        val result = runtime.render(
            root = PixelColumn(
                modifier = PixelModifier.Empty.size(4, 12),
                mainAxisAlignment = PixelMainAxisAlignment.END,
                children = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(4, 4),
                        borderTone = null,
                        fillTone = PixelTone.ACCENT,
                    ),
                ),
            ),
            logicalWidth = 4,
            logicalHeight = 12,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(1, 7))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(1, 8))
    }

    @Test
    fun pagerExportsClickTargetsFromCurrentPage() {
        var clicked = false
        val controller = PixelPagerController()
        val state = controller.create(pageCount = 2, currentPage = 0, axis = PixelAxis.HORIZONTAL)

        val result = runtime.render(
            root = PixelPager(
                axis = PixelAxis.HORIZONTAL,
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 20),
                pages = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty
                            .size(10, 10)
                            .clickable { clicked = true },
                    ),
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(10, 10),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 20,
        )

        assertEquals(1, result.clickTargets.size)
        assertTrue(result.clickTargets.first().bounds.contains(5, 5))
        result.clickTargets.first().onClick.invoke()
        assertTrue(clicked)
    }

    @Test
    fun pagerTranslatesAdjacentPageTargetsWhileDragging() {
        val controller = PixelPagerController()
        val state = controller.create(pageCount = 2, currentPage = 0, axis = PixelAxis.HORIZONTAL)
        controller.startDrag(state)
        controller.dragBy(state, deltaPx = -10f, viewportSizePx = 20)

        val result = runtime.render(
            root = PixelPager(
                axis = PixelAxis.HORIZONTAL,
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 20),
                pages = listOf(
                    PixelBox(
                        modifier = PixelModifier.Empty.fillMaxSize(),
                        alignment = PixelAlignment.TOP_START,
                        children = listOf(
                            PixelSurface(
                                modifier = PixelModifier.Empty.size(6, 6),
                            ),
                        ),
                    ),
                    PixelBox(
                        modifier = PixelModifier.Empty.fillMaxSize().padding(left = 2, top = 0),
                        alignment = PixelAlignment.TOP_START,
                        children = listOf(
                            PixelSurface(
                                modifier = PixelModifier.Empty
                                    .size(6, 6)
                                    .clickable {},
                            ),
                        ),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 20,
        )

        val adjacentTarget = result.clickTargets.single()
        assertTrue(adjacentTarget.bounds.contains(12, 2))
    }

    @Test
    fun textNodeCanOverrideRuntimeTextRasterizer() {
        val customRasterizer = object : PixelTextRasterizer {
            override fun measureText(text: String): Int = 3

            override fun measureHeight(text: String): Int = 4

            override fun drawText(
                buffer: PixelBuffer,
                text: String,
                x: Int,
                y: Int,
                value: Byte,
            ) {
                buffer.fillRect(
                    left = x,
                    top = y,
                    rectWidth = 3,
                    rectHeight = 4,
                    value = value,
                )
            }
        }

        val result = runtime.render(
            root = PixelSurface(
                modifier = PixelModifier.Empty.size(10, 10),
                padding = 0,
                alignment = PixelAlignment.TOP_START,
                borderTone = null,
                fillTone = PixelTone.OFF,
                child = PixelText(
                    text = "WIDE",
                    style = PixelTextStyle(
                        tone = PixelTone.ACCENT,
                        textRasterizer = customRasterizer,
                    ),
                ),
            ),
            logicalWidth = 10,
            logicalHeight = 10,
        )

        val pixels = collectOnPixels(result)
        val minX = pixels.minOf { it.first }
        val maxX = pixels.maxOf { it.first }
        val minY = pixels.minOf { it.second }
        val maxY = pixels.maxOf { it.second }
        assertEquals(0, minX)
        assertEquals(2, maxX)
        assertEquals(0, minY)
        assertEquals(3, maxY)
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(0, 0))
    }

    @Test
    fun pixelButtonBuildsClickableSurfaceWithStyledText() {
        var clicked = false

        val result = runtime.render(
            root = PixelButton(
                text = "OK",
                onClick = { clicked = true },
                modifier = PixelModifier.Empty.size(18, 10),
                style = PixelButtonStyle.Accent,
            ),
            logicalWidth = 20,
            logicalHeight = 12,
        )

        assertEquals(1, result.clickTargets.size)
        assertTrue(result.clickTargets.first().bounds.contains(9, 5))
        result.clickTargets.first().onClick.invoke()
        assertTrue(clicked)
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(0, 0))
    }

    @Test
    fun listExportsViewportTargetAndClipsChildClickArea() {
        val controller = PixelListController()
        val state = controller.create(initialScrollOffsetPx = 4f)

        val result = runtime.render(
            root = PixelList(
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 10),
                items = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty
                            .size(20, 8)
                            .clickable {},
                    ),
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(20, 8),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 10,
        )

        assertEquals(1, result.listTargets.size)
        assertTrue(result.listTargets.single().bounds.contains(10, 5))
        assertEquals(1, result.clickTargets.size)
        assertEquals(0, result.clickTargets.single().bounds.top)
        assertEquals(4, result.clickTargets.single().bounds.height)
    }

    @Test
    fun listRendersLowerItemsAfterScrollOffsetApplied() {
        val controller = PixelListController()
        val state = controller.create(initialScrollOffsetPx = 8f)

        val result = runtime.render(
            root = PixelList(
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 6),
                spacing = 2,
                items = listOf(
                    PixelSurface(modifier = PixelModifier.Empty.size(20, 6)),
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(20, 6),
                        fillTone = PixelTone.ACCENT,
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 6,
        )

        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(5, 1))
    }

    @Test
    fun textFieldExportsInputTargetAndDrawsPlaceholder() {
        val controller = PixelTextFieldController()
        val state = controller.create()

        val result = runtime.render(
            root = PixelTextField(
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 10),
                placeholder = "TYPE",
                style = PixelTextFieldStyle.Default,
            ),
            logicalWidth = 20,
            logicalHeight = 10,
        )

        assertEquals(1, result.textInputTargets.size)
        assertTrue(result.textInputTargets.single().bounds.contains(5, 5))
        assertTrue(
            hasTone(
                result = result,
                tone = PixelTone.ACCENT,
                minX = 2,
                maxX = 18,
                minY = 1,
                maxY = 9,
            ),
        )
    }

    @Test
    fun focusedTextFieldDrawsAccentCursor() {
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "HI")
        controller.focus(state)

        val result = runtime.render(
            root = PixelTextField(
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 10),
                style = PixelTextFieldStyle.Default,
            ),
            logicalWidth = 20,
            logicalHeight = 10,
        )

        assertTrue(
            hasTone(
                result = result,
                tone = PixelTone.ACCENT,
                minX = 1,
                maxX = 19,
                minY = 1,
                maxY = 9,
            ),
        )
    }

    private fun collectOnPixels(result: PixelRenderResult): List<Pair<Int, Int>> {
        val pixels = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until result.buffer.height) {
            for (x in 0 until result.buffer.width) {
                if (result.buffer.getPixel(x, y).toInt() != 0) {
                    pixels += x to y
                }
            }
        }
        return pixels
    }

    private fun collectFilledRows(result: PixelRenderResult): Set<Int> {
        val rows = mutableSetOf<Int>()
        for (y in 0 until result.buffer.height) {
            var hasValue = false
            for (x in 0 until result.buffer.width) {
                if (result.buffer.getPixel(x, y).toInt() != 0) {
                    hasValue = true
                    break
                }
            }
            if (hasValue) {
                rows += y
            }
        }
        return rows
    }

    private fun hasTone(
        result: PixelRenderResult,
        tone: PixelTone,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
    ): Boolean {
        for (y in minY until maxY) {
            for (x in minX until maxX) {
                if (result.buffer.getPixel(x, y) == tone.value) {
                    return true
                }
            }
        }
        return false
    }
}
