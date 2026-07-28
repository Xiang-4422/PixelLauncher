package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Test

class AxisBufferComposerTest {

    private val colorA = PixelColor.White
    private val colorB = PixelColor.fromRgb(200, 100, 0)

    @Test
    fun composeHorizontalSlidesSecondaryIntoViewFromRight() {
        val primary = PixelBuffer(width = 4, height = 2).apply {
            fillRect(0, 0, 4, 2, colorA)
        }
        val secondary = PixelBuffer(width = 4, height = 2).apply {
            fillRect(0, 0, 4, 2, colorB)
        }

        val composed = AxisBufferComposer.compose(
            primary = primary,
            secondary = secondary,
            axis = PixelAxis.HORIZONTAL,
            offsetPx = -2f,
            out = PixelBuffer(width = 4, height = 2),
        )

        assertEquals(colorA, composed.getPixel(0, 0))
        assertEquals(colorA, composed.getPixel(1, 0))
        assertEquals(colorB, composed.getPixel(2, 0))
        assertEquals(colorB, composed.getPixel(3, 0))
    }

    @Test
    fun composeVerticalSlidesSecondaryIntoViewFromBottom() {
        val primary = PixelBuffer(width = 2, height = 4).apply {
            fillRect(0, 0, 2, 4, colorA)
        }
        val secondary = PixelBuffer(width = 2, height = 4).apply {
            fillRect(0, 0, 2, 4, colorB)
        }

        val composed = AxisBufferComposer.compose(
            primary = primary,
            secondary = secondary,
            axis = PixelAxis.VERTICAL,
            offsetPx = -2f,
            out = PixelBuffer(width = 2, height = 4),
        )

        assertEquals(colorA, composed.getPixel(0, 0))
        assertEquals(colorA, composed.getPixel(0, 1))
        assertEquals(colorB, composed.getPixel(0, 2))
        assertEquals(colorB, composed.getPixel(0, 3))
    }
}
