package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.AnimatedPixelLoadingBar
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
        assertEquals(track, buffer.getPixel(9, 1))

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
}
