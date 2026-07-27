package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.AnimatedPixelLoadingBar
import com.purride.pixelui.PixelColorScheme
import com.purride.pixelui.PixelLoadingBar
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PixelLoadingBarTest {
    private val active = PixelColor.fromRgb(220, 36, 84)
    private val track = PixelColor.fromArgb(96, 220, 36, 84)

    @Test
    fun loadingBarPaintsTrackDotsAndSolidBlock() {
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelLoadingBar(
                progress = 0f,
                width = 24,
                height = 7,
                color = active,
                trackColor = track,
                blockWidth = 5,
                trailWidth = 3,
            ),
            logicalWidth = 24,
            logicalHeight = 7,
        )

        val buffer = tester.renderResult!!.buffer
        assertEquals(active, buffer.getPixel(0, 0))
        assertEquals(active, buffer.getPixel(4, 6))
        assertEquals(track, buffer.getPixel(8, 0))
        assertEquals(track, buffer.getPixel(8, 6))
        assertFalse(track == buffer.getPixel(8, 1))

        tester.pumpWidget(
            widget = PixelLoadingBar(
                progress = 0f,
                width = 24,
                height = 7,
                color = active,
                trackColor = track,
                blockWidth = 5,
                trailWidth = 3,
                reversed = true,
            ),
            logicalWidth = 24,
            logicalHeight = 7,
        )

        assertEquals(active, tester.renderResult!!.buffer.getPixel(23, 0))
        tester.dispose()
    }

    @Test
    fun loadingBarResolvesOmittedTrackDotsFromProgressTokens() {
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelLoadingBar(
                progress = 0f,
                width = 24,
                height = 7,
                color = active,
                blockWidth = 5,
                trailWidth = 0,
            ),
            logicalWidth = 24,
            logicalHeight = 7,
        )

        // 省略 trackColor 时点阵背景由 progress 组件的 container 角色解析。
        assertEquals(PixelColorScheme.Dark.warning, tester.renderResult!!.buffer.getPixel(8, 0))

        tester.pumpWidget(
            widget = PixelLoadingBar(
                progress = 0f,
                width = 24,
                height = 7,
                color = active,
                trackColor = active,
                blockWidth = 5,
                trailWidth = 0,
            ),
            logicalWidth = 24,
            logicalHeight = 7,
        )

        // 显式 trackColor 仍然优先于解析出的角色颜色。
        assertEquals(active, tester.renderResult!!.buffer.getPixel(8, 0))
        tester.dispose()
    }

    @Test
    fun animatedLoadingBarAdvancesWithTicker() {
        val tester = PixelTester()

        tester.pumpWidget(
            widget = AnimatedPixelLoadingBar(
                vsync = tester.vsync,
                width = 24,
                height = 7,
                color = active,
                trackColor = track,
                blockWidth = 5,
                trailWidth = 3,
                fps = 10,
                cycleFrames = 12,
            ),
            logicalWidth = 24,
            logicalHeight = 7,
        )

        val firstFrame = tester.renderResult!!.buffer.pixels.copyOf()
        tester.pumpFrame(0)
        tester.pumpFrame(100)
        tester.pumpFrame(100)

        assertFalse(firstFrame.contentEquals(tester.renderResult!!.buffer.pixels))
        tester.dispose()
    }

    @Test
    fun loadingBarExpandsDynamicWakeNearMiddleOfTravel() {
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelLoadingBar(
                progress = 0f,
                width = 40,
                height = 9,
                color = active,
                trackColor = track,
                blockWidth = 7,
                trailWidth = 4,
            ),
            logicalWidth = 40,
            logicalHeight = 9,
        )
        val endpointActivePixels = tester.renderResult!!.buffer.pixels.count { it == active.argb }

        tester.pumpWidget(
            widget = PixelLoadingBar(
                progress = 0.5f,
                width = 40,
                height = 9,
                color = active,
                trackColor = track,
                blockWidth = 7,
                trailWidth = 4,
            ),
            logicalWidth = 40,
            logicalHeight = 9,
        )
        val middleActivePixels = tester.renderResult!!.buffer.pixels.count { it == active.argb }

        assertFalse(middleActivePixels <= endpointActivePixels)
        tester.dispose()
    }

    @Test
    fun loadingBarWakeUsesFixedSymmetricNearAndFarSegments() {
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelLoadingBar(
                progress = 0.5f,
                width = 40,
                height = 9,
                color = active,
                trackColor = track,
                blockWidth = 7,
                trailWidth = 4,
            ),
            logicalWidth = 40,
            logicalHeight = 9,
        )

        val forwardBuffer = tester.renderResult!!.buffer
        assertFalse(active == forwardBuffer.getPixel(15, 0))
        assertEquals(active, forwardBuffer.getPixel(15, 1))
        assertFalse(active == forwardBuffer.getPixel(15, 8))
        assertEquals(active, forwardBuffer.getPixel(14, 0))
        assertEquals(PixelColor.Transparent, forwardBuffer.getPixel(14, 2))
        assertEquals(active, forwardBuffer.getPixel(14, 8))
        assertEquals(PixelColor.Transparent, forwardBuffer.getPixel(10, 2))
        assertFalse(active == forwardBuffer.getPixel(11, 0))
        assertEquals(active, forwardBuffer.getPixel(11, 2))
        assertFalse(active == forwardBuffer.getPixel(11, 8))
        assertFalse(active == forwardBuffer.getPixel(11, 1))

        tester.pumpWidget(
            widget = PixelLoadingBar(
                progress = 0.5f,
                width = 40,
                height = 9,
                color = active,
                trackColor = track,
                blockWidth = 7,
                trailWidth = 4,
                reversed = true,
            ),
            logicalWidth = 40,
            logicalHeight = 9,
        )

        val reversedBuffer = tester.renderResult!!.buffer
        assertFalse(active == reversedBuffer.getPixel(24, 0))
        assertEquals(PixelColor.Transparent, reversedBuffer.getPixel(24, 0))
        assertEquals(active, reversedBuffer.getPixel(24, 1))
        assertFalse(active == reversedBuffer.getPixel(24, 8))
        assertEquals(active, reversedBuffer.getPixel(25, 0))
        assertEquals(active, reversedBuffer.getPixel(25, 8))
        assertFalse(active == reversedBuffer.getPixel(28, 0))
        assertEquals(active, reversedBuffer.getPixel(28, 2))
        assertFalse(active == reversedBuffer.getPixel(28, 8))
        assertFalse(active == reversedBuffer.getPixel(28, 1))
        tester.dispose()
    }

    @Test
    fun loadingBarClampsEachWakeSegmentToTwoThirdsOfBlockWidth() {
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelLoadingBar(
                progress = 0.5f,
                width = 60,
                height = 9,
                color = active,
                trackColor = track,
                blockWidth = 9,
                trailWidth = 99,
            ),
            logicalWidth = 60,
            logicalHeight = 9,
        )

        val forwardBuffer = tester.renderResult!!.buffer
        assertEquals(active, forwardBuffer.getPixel(13, 0))
        assertFalse((0 until 9).any { y -> active == forwardBuffer.getPixel(12, y) })

        tester.pumpWidget(
            widget = PixelLoadingBar(
                progress = 0.5f,
                width = 60,
                height = 9,
                color = active,
                trackColor = track,
                blockWidth = 9,
                trailWidth = 99,
                reversed = true,
            ),
            logicalWidth = 60,
            logicalHeight = 9,
        )

        val reversedBuffer = tester.renderResult!!.buffer
        assertEquals(active, reversedBuffer.getPixel(46, 0))
        assertFalse((0 until 9).any { y -> active == reversedBuffer.getPixel(47, y) })
        tester.dispose()
    }

    @Test
    fun loadingBarContinuesWakeGridAcrossNearAndFarSegments() {
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelLoadingBar(
                progress = 0.5f,
                width = 70,
                height = 9,
                color = active,
                trackColor = track,
                blockWidth = 11,
                trailWidth = 7,
            ),
            logicalWidth = 70,
            logicalHeight = 9,
        )

        val buffer = tester.renderResult!!.buffer
        assertFalse(active == buffer.getPixel(22, 0))
        assertEquals(active, buffer.getPixel(22, 1))
        assertEquals(active, buffer.getPixel(21, 0))
        assertFalse(active == buffer.getPixel(21, 1))
        tester.dispose()
    }

    @Test
    fun loadingBarHidesWakeAtTravelEndpoints() {
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelLoadingBar(
                progress = 1f,
                width = 40,
                height = 9,
                color = active,
                trackColor = track,
                blockWidth = 7,
                trailWidth = 4,
            ),
            logicalWidth = 40,
            logicalHeight = 9,
        )

        val forwardBuffer = tester.renderResult!!.buffer
        assertEquals(active, forwardBuffer.getPixel(33, 0))
        assertEquals(active, forwardBuffer.getPixel(39, 8))
        assertFalse(active == forwardBuffer.getPixel(32, 0))
        assertFalse(active == forwardBuffer.getPixel(30, 4))

        tester.pumpWidget(
            widget = PixelLoadingBar(
                progress = 1f,
                width = 40,
                height = 9,
                color = active,
                trackColor = track,
                blockWidth = 7,
                trailWidth = 4,
                reversed = true,
            ),
            logicalWidth = 40,
            logicalHeight = 9,
        )

        val reversedBuffer = tester.renderResult!!.buffer
        assertEquals(active, reversedBuffer.getPixel(0, 0))
        assertEquals(active, reversedBuffer.getPixel(6, 8))
        assertFalse(active == reversedBuffer.getPixel(7, 0))
        assertFalse(active == reversedBuffer.getPixel(9, 4))
        tester.dispose()
    }
}
