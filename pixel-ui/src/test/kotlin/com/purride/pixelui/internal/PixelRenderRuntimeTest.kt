package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelAlignment
import com.purride.pixelui.PixelBox
import com.purride.pixelui.PixelColumn
import com.purride.pixelui.PixelModifier
import com.purride.pixelui.PixelPager
import com.purride.pixelui.PixelSurface
import com.purride.pixelui.PixelText
import com.purride.pixelui.clickable
import com.purride.pixelui.fillMaxSize
import com.purride.pixelui.height
import com.purride.pixelui.padding
import com.purride.pixelui.size
import com.purride.pixelui.state.PixelPagerController
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
                    textRasterizer = customRasterizer,
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
}
