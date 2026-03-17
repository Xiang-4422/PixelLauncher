package com.purride.pixellauncherv2.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class PixelGlCodecTest {

    @Test
    fun encodePixelValuesMapsOffOnAccentToExpectedLuminance() {
        val source = byteArrayOf(
            PixelBuffer.OFF,
            PixelBuffer.ON,
            PixelBuffer.ACCENT,
            99,
        )
        val target = ByteBuffer.allocateDirect(source.size)
        PixelGlCodec.encodePixelValues(source, target)
        assertEquals(0, target.get(0).toInt() and 0xFF)
        assertEquals(0x7F, target.get(1).toInt() and 0xFF)
        assertEquals(0xFF, target.get(2).toInt() and 0xFF)
        assertEquals(0, target.get(3).toInt() and 0xFF)
    }

    @Test
    fun shapeMaskRulesMatchSquareCircleAndDiamondExpectations() {
        val dotSize = 10f
        assertTrue(PixelGlCodec.isDotMaskHit(PixelShape.SQUARE, 0f, 0f, dotSize))

        assertTrue(PixelGlCodec.isDotMaskHit(PixelShape.CIRCLE, 5f, 5f, dotSize))
        assertFalse(PixelGlCodec.isDotMaskHit(PixelShape.CIRCLE, 0f, 0f, dotSize))

        assertTrue(PixelGlCodec.isDotMaskHit(PixelShape.DIAMOND, 5f, 0f, dotSize))
        assertFalse(PixelGlCodec.isDotMaskHit(PixelShape.DIAMOND, 0f, 0f, dotSize))
    }

    @Test
    fun logicalTextureCoordinateUsesTopLeftToBottomRightOrderWithoutYFlip() {
        val topLeft = PixelGlCodec.logicalTextureCoordinate(
            cellX = 0,
            cellY = 0,
            logicalWidth = 4,
            logicalHeight = 4,
        )
        val bottomLeft = PixelGlCodec.logicalTextureCoordinate(
            cellX = 0,
            cellY = 3,
            logicalWidth = 4,
            logicalHeight = 4,
        )

        assertEquals(0.125f, topLeft.first, 1e-4f)
        assertEquals(0.125f, topLeft.second, 1e-4f)
        assertEquals(0.125f, bottomLeft.first, 1e-4f)
        assertEquals(0.875f, bottomLeft.second, 1e-4f)
    }
}
