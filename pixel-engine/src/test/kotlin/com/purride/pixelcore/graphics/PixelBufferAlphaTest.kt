package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelBufferAlphaTest {
    @Test
    fun setPixelUsesSrcOverForHalfAlpha() {
        val buffer = PixelBuffer(width = 1, height = 1)
        buffer.setPixel(0, 0, PixelColor.fromRgb(0, 0, 255))

        buffer.setPixel(0, 0, PixelColor.fromArgb(128, 255, 0, 0))

        val pixel = buffer.getPixel(0, 0)
        assertEquals(255, pixel.alpha)
        assertTrue(pixel.red in 127..129)
        assertEquals(0, pixel.green)
        assertTrue(pixel.blue in 126..128)
    }

    @Test
    fun transparentSourceDoesNotOverwriteDestination() {
        val buffer = PixelBuffer(width = 1, height = 1)
        val blue = PixelColor.fromRgb(0, 0, 255)
        buffer.setPixel(0, 0, blue)

        buffer.setPixel(0, 0, PixelColor.Transparent)

        assertEquals(blue.argb, buffer.getPixel(0, 0).argb)
    }

    @Test
    fun srcBlendModeOverwritesDestinationWithTransparentSource() {
        val buffer = PixelBuffer(width = 1, height = 1)
        buffer.setPixel(0, 0, PixelColor.White)

        buffer.setPixel(0, 0, PixelColor.Transparent, blendMode = PixelBlendMode.Src)

        assertEquals(PixelColor.Transparent.argb, buffer.getPixel(0, 0).argb)
    }

    @Test
    fun clearBlendModeClearsRect() {
        val buffer = PixelBuffer(width = 2, height = 1)
        buffer.fillRect(0, 0, 2, 1, PixelColor.White)

        buffer.fillRect(1, 0, 1, 1, PixelColor.White, blendMode = PixelBlendMode.Clear)

        assertEquals(PixelColor.White.argb, buffer.getPixel(0, 0).argb)
        assertEquals(PixelColor.Transparent.argb, buffer.getPixel(1, 0).argb)
    }

    @Test
    fun blitKeepsOpaqueFastPathSemantics() {
        val source = PixelBuffer(width = 1, height = 1)
        val dest = PixelBuffer(width = 1, height = 1)
        val red = PixelColor.fromRgb(255, 0, 0)
        source.setPixel(0, 0, red)

        dest.blit(source, destX = 0, destY = 0)

        assertEquals(red.argb, dest.getPixel(0, 0).argb)
    }
}
