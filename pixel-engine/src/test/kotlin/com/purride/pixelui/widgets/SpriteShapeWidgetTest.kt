package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelBitmapRegion
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelSpriteSheet
import com.purride.pixelui.Path
import com.purride.pixelui.PixelPath
import com.purride.pixelui.PixelPathCommand
import com.purride.pixelui.PixelPoint
import com.purride.pixelui.Polygon
import com.purride.pixelui.Sprite
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.widgets.animated.AnimatedSprite
import org.junit.Assert.assertEquals
import org.junit.Test

class SpriteShapeWidgetTest {
    @Test
    fun spriteBlitsSelectedFrame() {
        val sheet = twoFrameSheet()
        val tester = PixelTester()
        tester.pumpWidget(Sprite(sheet, frameIndex = 1), 2, 2)

        assertEquals(PixelColor.fromRgb(200, 100, 0), tester.renderResult!!.buffer.getPixel(0, 0))
        tester.dispose()
    }

    @Test
    fun animatedSpriteAdvancesByFps() {
        val sheet = twoFrameSheet()
        val tester = PixelTester()
        tester.pumpWidget(AnimatedSprite(sheet, fps = 10, vsync = tester.vsync), 2, 2)

        tester.pumpFrame(0)
        tester.pumpFrame(100)

        assertEquals(PixelColor.fromRgb(200, 100, 0), tester.renderResult!!.buffer.getPixel(0, 0))
        tester.dispose()
    }

    @Test(expected = IllegalArgumentException::class)
    fun animatedSpriteRejectsInvalidFps() {
        AnimatedSprite(twoFrameSheet(), fps = 0, vsync = PixelTester().vsync)
    }

    @Test
    fun polygonFilledPaintsInterior() {
        val tester = PixelTester()
        tester.pumpWidget(
            Polygon(
                points = listOf(PixelPoint(0, 0), PixelPoint(4, 0), PixelPoint(4, 4), PixelPoint(0, 4)),
                color = PixelColor.White,
                filled = true,
            ),
            5,
            5,
        )

        assertEquals(PixelColor.White, tester.renderResult!!.buffer.getPixel(2, 2))
        tester.dispose()
    }

    @Test
    fun pathDrawsLineAndCloseSegments() {
        val tester = PixelTester()
        tester.pumpWidget(
            Path(
                path = PixelPath(
                    listOf(
                        PixelPathCommand.MoveTo(PixelPoint(0, 0)),
                        PixelPathCommand.LineTo(PixelPoint(3, 0)),
                        PixelPathCommand.LineTo(PixelPoint(3, 3)),
                        PixelPathCommand.Close,
                    ),
                ),
                color = PixelColor.White,
            ),
            4,
            4,
        )

        assertEquals(PixelColor.White, tester.renderResult!!.buffer.getPixel(0, 0))
        assertEquals(PixelColor.White, tester.renderResult!!.buffer.getPixel(3, 3))
        tester.dispose()
    }

    private fun twoFrameSheet(): PixelSpriteSheet {
        val pixels = intArrayOf(
            PixelColor.White.argb,
            PixelColor.White.argb,
            PixelColor.fromRgb(200, 100, 0).argb,
            PixelColor.fromRgb(200, 100, 0).argb,
        )
        return PixelSpriteSheet(
            bitmap = PixelBitmap(width = 4, height = 1, pixels = pixels),
            frames = listOf(
                PixelBitmapRegion(0, 0, 2, 1),
                PixelBitmapRegion(2, 0, 2, 1),
            ),
        )
    }
}
