package com.purride.pixellauncherv2.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FrameSwapBufferTest {
    @Test
    fun consumeLatestReturnsOnlyNewerFrames() {
        val swapBuffer = FrameSwapBuffer()
        val profile = ScreenProfileFactory.create(widthPx = 100, heightPx = 100)
        val first = swapBuffer.offer(
            pixelBuffer = PixelBuffer(width = 8, height = 8),
            screenProfile = profile,
            palette = PixelPalette(
                backgroundColor = 0x000000,
                pixelOnColor = 0x00FF00,
                pixelOffColor = 0x001100,
                accentColor = 0x99FF99,
            ),
        )
        val consumedFirst = swapBuffer.consumeLatest(afterSequence = 0L)
        assertNotNull(consumedFirst)
        assertEquals(first.sequence, consumedFirst?.sequence)
        val consumedAgain = swapBuffer.consumeLatest(afterSequence = first.sequence)
        assertNull(consumedAgain)
    }
}
