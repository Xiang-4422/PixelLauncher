package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Test

class PixelBufferTest {

    private val red = PixelColor.fromRgb(255, 0, 0)
    private val blue = PixelColor.fromRgb(0, 0, 255)
    private val green = PixelColor.fromArgb(0xFF, 0, 200, 0)

    @Test
    fun newBufferIsAllTransparent() {
        val buf = PixelBuffer(width = 4, height = 4)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(PixelColor.Transparent, buf.getPixel(x, y))
            }
        }
    }

    @Test
    fun setAndGetPixelRoundTrips() {
        val buf = PixelBuffer(width = 4, height = 4)
        buf.setPixel(1, 2, red)
        buf.setPixel(3, 0, blue)
        assertEquals(red, buf.getPixel(1, 2))
        assertEquals(blue, buf.getPixel(3, 0))
        assertEquals(PixelColor.Transparent, buf.getPixel(0, 0))
    }

    @Test
    fun outOfBoundsAccessIsIgnoredOrReturnsTransparent() {
        val buf = PixelBuffer(width = 4, height = 4)
        buf.setPixel(-1, 0, red)
        buf.setPixel(0, -1, red)
        buf.setPixel(4, 0, red)
        buf.setPixel(0, 4, red)
        assertEquals(PixelColor.Transparent, buf.getPixel(-1, 0))
        assertEquals(PixelColor.Transparent, buf.getPixel(0, -1))
        assertEquals(PixelColor.Transparent, buf.getPixel(4, 0))
        assertEquals(PixelColor.Transparent, buf.getPixel(0, 4))
    }

    @Test
    fun clearResetsAllPixelsToTransparent() {
        val buf = PixelBuffer(width = 4, height = 4)
        buf.setPixel(0, 0, red)
        buf.setPixel(3, 3, blue)
        buf.clear()
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(PixelColor.Transparent, buf.getPixel(x, y))
            }
        }
    }

    @Test
    fun copyReturnsIndependentBuffer() {
        val buf = PixelBuffer(width = 2, height = 2)
        buf.setPixel(0, 0, red)
        buf.setPixel(1, 1, blue)

        val copy = buf.copy()
        buf.setPixel(0, 0, green)
        copy.setPixel(1, 1, red)

        assertEquals(red, copy.getPixel(0, 0))
        assertEquals(red, copy.getPixel(1, 1))
        assertEquals(green, buf.getPixel(0, 0))
        assertEquals(blue, buf.getPixel(1, 1))
    }

    @Test
    fun blitCopiesSourcePixels() {
        val src = PixelBuffer(width = 2, height = 2)
        src.setPixel(0, 0, red)
        src.setPixel(1, 1, blue)
        val dst = PixelBuffer(width = 4, height = 4)
        dst.blit(source = src, destX = 1, destY = 1)
        assertEquals(red, dst.getPixel(1, 1))
        assertEquals(blue, dst.getPixel(2, 2))
        assertEquals(PixelColor.Transparent, dst.getPixel(0, 0))
    }

    @Test
    fun blitHandlesNegativeDestOffset() {
        val src = PixelBuffer(width = 4, height = 4)
        src.setPixel(2, 0, green)
        val dst = PixelBuffer(width = 4, height = 4)
        dst.blit(source = src, destX = -1, destY = 0)
        assertEquals(green, dst.getPixel(1, 0))
    }

    @Test
    fun blitRegionCopiesPartialSource() {
        val src = PixelBuffer(width = 4, height = 4)
        src.setPixel(0, 2, red)
        src.setPixel(1, 2, blue)
        val dst = PixelBuffer(width = 4, height = 4)
        dst.blitRegion(source = src, sourceX = 0, sourceY = 2, copyWidth = 2, copyHeight = 1, destX = 0, destY = 0)
        assertEquals(red, dst.getPixel(0, 0))
        assertEquals(blue, dst.getPixel(1, 0))
        assertEquals(PixelColor.Transparent, dst.getPixel(0, 1))
    }

    @Test
    fun widthAndHeightReflectConstructorArgs() {
        val buf = PixelBuffer(width = 10, height = 5)
        assertEquals(10, buf.width)
        assertEquals(5, buf.height)
    }

    @Test
    fun pixelArraySizeMatchesDimensions() {
        val buf = PixelBuffer(width = 6, height = 3)
        assertEquals(18, buf.pixels.size)
    }
}
