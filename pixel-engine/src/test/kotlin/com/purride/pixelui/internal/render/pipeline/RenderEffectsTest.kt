package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderEffectsTest {
    @Test
    fun opacityBlendsChildIntoDestination() {
        val child = SolidBox(width = 2, height = 2, color = PixelColor.fromRgb(255, 0, 0))
        val opacity = RenderOpacity(child = child, opacity = 0.5f)
        opacity.layout(RenderConstraints(maxWidth = 2, maxHeight = 2))
        val buffer = PixelBuffer(width = 2, height = 2)
        buffer.fillRect(0, 0, 2, 2, PixelColor.fromRgb(0, 0, 255))

        opacity.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        val pixel = buffer.getPixel(0, 0)
        assertTrue(pixel.red in 127..129)
        assertTrue(pixel.blue in 126..128)
    }

    @Test
    fun clipRectClipsChildPaintToLayoutBox() {
        val child = SolidBox(width = 4, height = 4, color = PixelColor.White)
        val clip = RenderClipRect(child = child)
        clip.layout(RenderConstraints(maxWidth = 2, maxHeight = 2))
        val buffer = PixelBuffer(width = 4, height = 4)

        clip.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        assertEquals(PixelColor.White.argb, buffer.getPixel(1, 1).argb)
        assertEquals(PixelColor.Transparent.argb, buffer.getPixel(2, 0).argb)
        assertEquals(PixelColor.Transparent.argb, buffer.getPixel(0, 2).argb)
    }

    @Test
    fun translateMovesChildPaintAndTargets() {
        var clicked = false
        val child = SolidBox(width = 1, height = 1, color = PixelColor.White, onClick = { clicked = true })
        val translate = RenderTranslate(child = child, dx = 2, dy = 1)
        translate.layout(RenderConstraints(maxWidth = 4, maxHeight = 4))
        val buffer = PixelBuffer(width = 4, height = 4)

        translate.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        assertEquals(PixelColor.White.argb, buffer.getPixel(2, 1).argb)
        assertNotEquals(PixelColor.White.argb, buffer.getPixel(0, 0).argb)
        val targets = mutableListOf<PixelClickTarget>()
        translate.collectClickTargets(0, 0, targets)
        assertEquals(1, targets.size)
        assertEquals(PixelRect(2, 1, 1, 1), targets.single().bounds)
        targets.single().onClick()
        assertTrue(clicked)
    }

    private class SolidBox(
        private val width: Int,
        private val height: Int,
        private val color: PixelColor,
        private val onClick: (() -> Unit)? = null,
    ) : RenderBox() {
        override fun layout(constraints: RenderConstraints) {
            size = RenderSize(
                width = constraints.constrainWidth(width),
                height = constraints.constrainHeight(height),
            )
        }

        override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
            context.fillRect(offsetX, offsetY, size.width, size.height, color)
        }

        override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
            val callback = onClick ?: return
            targets += PixelClickTarget(PixelRect(offsetX, offsetY, size.width, size.height), callback)
        }
    }
}
