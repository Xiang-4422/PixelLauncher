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

    /** 稀疏 blit 跳过完全透明源像素时必须保留既有目标颜色。 */
    @Test
    fun blitTransparentSourcePreservesOpaqueDestination() {
        /** 只在中间位置包含一个不透明像素的稀疏源缓冲。 */
        val source = PixelBuffer(width = 3, height = 1)
        /** 用于证明非透明源像素仍会正常合成的红色。 */
        val red = PixelColor.fromRgb(255, 0, 0)
        source.setPixel(1, 0, red)
        /** 预先填充为蓝色、用于验证透明位置不被回写的目标缓冲。 */
        val destination = PixelBuffer(width = 3, height = 1)
        /** 目标缓冲的原始不透明蓝色。 */
        val blue = PixelColor.fromRgb(0, 0, 255)
        destination.fillRect(0, 0, 3, 1, blue)

        destination.blit(source, destX = 0, destY = 0)

        assertEquals(blue.argb, destination.getPixel(0, 0).argb)
        assertEquals(red.argb, destination.getPixel(1, 0).argb)
        assertEquals(blue.argb, destination.getPixel(2, 0).argb)
    }
}
