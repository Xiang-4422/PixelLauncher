package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Test

class AxisBufferComposerTest {

    @Test
    fun composeHorizontalSlidesSecondaryIntoViewFromRight() {
        val primary = MonoPixelBuffer(width = 4, height = 2).apply {
            fillRect(0, 0, 4, 2, PixelTone.ON)
        }
        val secondary = MonoPixelBuffer(width = 4, height = 2).apply {
            fillRect(0, 0, 4, 2, PixelTone.ACCENT)
        }

        val composed = AxisBufferComposer.compose(
            primary = primary,
            secondary = secondary,
            axis = PixelAxis.HORIZONTAL,
            offsetPx = -2f,
        ) as MonoPixelBuffer

        assertEquals(PixelTone.ON, composed.getPixel(0, 0))
        assertEquals(PixelTone.ON, composed.getPixel(1, 0))
        assertEquals(PixelTone.ACCENT, composed.getPixel(2, 0))
        assertEquals(PixelTone.ACCENT, composed.getPixel(3, 0))
    }

    @Test
    fun composeVerticalSlidesSecondaryIntoViewFromBottom() {
        val primary = MonoPixelBuffer(width = 2, height = 4).apply {
            fillRect(0, 0, 2, 4, PixelTone.ON)
        }
        val secondary = MonoPixelBuffer(width = 2, height = 4).apply {
            fillRect(0, 0, 2, 4, PixelTone.ACCENT)
        }

        val composed = AxisBufferComposer.compose(
            primary = primary,
            secondary = secondary,
            axis = PixelAxis.VERTICAL,
            offsetPx = -2f,
        ) as MonoPixelBuffer

        assertEquals(PixelTone.ON, composed.getPixel(0, 0))
        assertEquals(PixelTone.ON, composed.getPixel(0, 1))
        assertEquals(PixelTone.ACCENT, composed.getPixel(0, 2))
        assertEquals(PixelTone.ACCENT, composed.getPixel(0, 3))
    }
}
