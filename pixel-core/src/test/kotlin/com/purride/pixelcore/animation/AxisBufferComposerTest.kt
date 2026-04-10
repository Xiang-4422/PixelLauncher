package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Test

class AxisBufferComposerTest {

    @Test
    fun composeHorizontalSlidesSecondaryIntoViewFromRight() {
        val primary = PixelBuffer(width = 4, height = 2).apply {
            fillRect(0, 0, 4, 2, PixelTone.ON.value)
        }
        val secondary = PixelBuffer(width = 4, height = 2).apply {
            fillRect(0, 0, 4, 2, PixelTone.ACCENT.value)
        }

        val composed = AxisBufferComposer.compose(
            primary = primary,
            secondary = secondary,
            axis = PixelAxis.HORIZONTAL,
            offsetPx = -2f,
        )

        assertEquals(PixelTone.ON.value, composed.getPixel(0, 0))
        assertEquals(PixelTone.ON.value, composed.getPixel(1, 0))
        assertEquals(PixelTone.ACCENT.value, composed.getPixel(2, 0))
        assertEquals(PixelTone.ACCENT.value, composed.getPixel(3, 0))
    }

    @Test
    fun composeVerticalSlidesSecondaryIntoViewFromBottom() {
        val primary = PixelBuffer(width = 2, height = 4).apply {
            fillRect(0, 0, 2, 4, PixelTone.ON.value)
        }
        val secondary = PixelBuffer(width = 2, height = 4).apply {
            fillRect(0, 0, 2, 4, PixelTone.ACCENT.value)
        }

        val composed = AxisBufferComposer.compose(
            primary = primary,
            secondary = secondary,
            axis = PixelAxis.VERTICAL,
            offsetPx = -2f,
        )

        assertEquals(PixelTone.ON.value, composed.getPixel(0, 0))
        assertEquals(PixelTone.ON.value, composed.getPixel(0, 1))
        assertEquals(PixelTone.ACCENT.value, composed.getPixel(0, 2))
        assertEquals(PixelTone.ACCENT.value, composed.getPixel(0, 3))
    }
}
