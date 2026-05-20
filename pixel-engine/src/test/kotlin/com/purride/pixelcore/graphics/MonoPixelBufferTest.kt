package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Test

class MonoPixelBufferTest {

    @Test
    fun newBufferIsAllOff() {
        val buf = MonoPixelBuffer(width = 4, height = 4)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(PixelTone.OFF, buf.getPixel(x, y))
            }
        }
    }

    @Test
    fun setAndGetPixelRoundTrips() {
        val buf = MonoPixelBuffer(width = 4, height = 4)
        buf.setPixel(1, 2, PixelTone.ON)
        buf.setPixel(3, 0, PixelTone.ACCENT)
        assertEquals(PixelTone.ON, buf.getPixel(1, 2))
        assertEquals(PixelTone.ACCENT, buf.getPixel(3, 0))
        assertEquals(PixelTone.OFF, buf.getPixel(0, 0))
    }

    @Test
    fun outOfBoundsAccessIsIgnoredOrReturnsOff() {
        val buf = MonoPixelBuffer(width = 4, height = 4)
        buf.setPixel(-1, 0, PixelTone.ON)
        buf.setPixel(0, -1, PixelTone.ON)
        buf.setPixel(4, 0, PixelTone.ON)
        buf.setPixel(0, 4, PixelTone.ON)
        assertEquals(PixelTone.OFF, buf.getPixel(-1, 0))
        assertEquals(PixelTone.OFF, buf.getPixel(0, -1))
        assertEquals(PixelTone.OFF, buf.getPixel(4, 0))
        assertEquals(PixelTone.OFF, buf.getPixel(0, 4))
    }

    @Test
    fun clearResetsAllPixelsToOff() {
        val buf = MonoPixelBuffer(width = 4, height = 4)
        buf.setPixel(0, 0, PixelTone.ON)
        buf.setPixel(3, 3, PixelTone.ACCENT)
        buf.clear()
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(PixelTone.OFF, buf.getPixel(x, y))
            }
        }
    }

    @Test
    fun fillRectFillsCorrectRegion() {
        val buf = MonoPixelBuffer(width = 8, height = 8)
        buf.fillRect(left = 1, top = 1, rectWidth = 3, rectHeight = 3, tone = PixelTone.ON)
        for (y in 1..3) {
            for (x in 1..3) {
                assertEquals(PixelTone.ON, buf.getPixel(x, y))
            }
        }
        assertEquals(PixelTone.OFF, buf.getPixel(0, 0))
        assertEquals(PixelTone.OFF, buf.getPixel(4, 4))
    }

    @Test
    fun drawRectDrawsBorderOnly() {
        val buf = MonoPixelBuffer(width = 6, height = 6)
        buf.drawRect(left = 0, top = 0, rectWidth = 6, rectHeight = 6, tone = PixelTone.ON)
        assertEquals(PixelTone.ON, buf.getPixel(0, 0))
        assertEquals(PixelTone.ON, buf.getPixel(5, 5))
        assertEquals(PixelTone.OFF, buf.getPixel(2, 2))
    }

    @Test
    fun blitCopiesSourcePixels() {
        val src = MonoPixelBuffer(width = 2, height = 2)
        src.setPixel(0, 0, PixelTone.ON)
        src.setPixel(1, 1, PixelTone.ACCENT)
        val dst = MonoPixelBuffer(width = 4, height = 4)
        dst.blit(source = src, destX = 1, destY = 1)
        assertEquals(PixelTone.ON, dst.getPixel(1, 1))
        assertEquals(PixelTone.ACCENT, dst.getPixel(2, 2))
        assertEquals(PixelTone.OFF, dst.getPixel(0, 0))
    }

    @Test
    fun blitColorIntoMonoThrows() {
        val mono = MonoPixelBuffer(width = 4, height = 4)
        val color = ColorPixelBuffer(width = 2, height = 2)
        assertThrows(UnsupportedOperationException::class.java) {
            mono.blit(source = color, destX = 0, destY = 0)
        }
    }

    @Test
    fun copyProducesIndependentClone() {
        val src = MonoPixelBuffer(width = 3, height = 3)
        src.setPixel(1, 1, PixelTone.ON)
        val copy = src.copy()
        assertNotSame(src, copy)
        assertEquals(PixelTone.ON, copy.getPixel(1, 1))
        copy.setPixel(1, 1, PixelTone.ACCENT)
        assertEquals(PixelTone.ON, src.getPixel(1, 1))
    }

    @Test
    fun widthAndHeightReflectConstructorArgs() {
        val buf = MonoPixelBuffer(width = 10, height = 5)
        assertEquals(10, buf.width)
        assertEquals(5, buf.height)
    }
}
