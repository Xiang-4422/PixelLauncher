package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Line` / `Circle` widget render object 的最小回归测试。
 */
class RenderShapesTest {

    private val red = PixelColor.fromRgb(0xFF, 0, 0)

    // ── Line ───────────────────────────────────────────────────────────────

    @Test
    fun lineHorizontalFillsExpectedPixels() {
        val render = RenderLine(startX = 0, startY = 0, endX = 4, endY = 0, color = red)
        render.layout(RenderConstraints(maxWidth = 10, maxHeight = 10))
        val buffer = PixelBuffer(width = 6, height = 3).also { it.clear() }

        render.paint(PaintContext(buffer), offsetX = 0, offsetY = 1)

        for (x in 0..4) {
            assertEquals("(${x},1) should be red", red.argb, buffer.pixels[1 * 6 + x])
        }
        // 上下行应保持透明
        for (x in 0 until 6) {
            assertEquals(PixelColor.Transparent.argb, buffer.pixels[0 * 6 + x])
            assertEquals(PixelColor.Transparent.argb, buffer.pixels[2 * 6 + x])
        }
    }

    @Test
    fun lineDiagonalUsesBresenhamPath() {
        val render = RenderLine(startX = 0, startY = 0, endX = 3, endY = 3, color = red)
        render.layout(RenderConstraints(maxWidth = 10, maxHeight = 10))
        val buffer = PixelBuffer(width = 4, height = 4).also { it.clear() }

        render.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        // 主对角线应当全亮
        for (i in 0..3) {
            assertEquals("diag ($i,$i)", red.argb, buffer.pixels[i * 4 + i])
        }
    }

    @Test
    fun lineLayoutIntrinsicSizeMatchesBoundingBox() {
        val render = RenderLine(startX = 0, startY = 0, endX = 5, endY = 2, color = red)
        render.layout(RenderConstraints(maxWidth = 100, maxHeight = 100))
        assertEquals(6, render.size.width)
        assertEquals(3, render.size.height)
    }

    @Test
    fun lineLayoutClampsToTightConstraints() {
        val render = RenderLine(startX = 0, startY = 0, endX = 9, endY = 9, color = red)
        render.layout(RenderConstraints(maxWidth = 4, maxHeight = 4))
        assertEquals(4, render.size.width)
        assertEquals(4, render.size.height)
    }

    // ── Circle ─────────────────────────────────────────────────────────────

    @Test
    fun circleFilledHitsCenterAndCardinals() {
        val render = RenderCircle(radius = 2, color = red, filled = true)
        render.layout(RenderConstraints(maxWidth = 20, maxHeight = 20))
        val buffer = PixelBuffer(width = 5, height = 5).also { it.clear() }

        render.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        // 中心点
        assertEquals(red.argb, buffer.pixels[2 * 5 + 2])
        // 上下左右
        assertEquals(red.argb, buffer.pixels[0 * 5 + 2]) // top
        assertEquals(red.argb, buffer.pixels[4 * 5 + 2]) // bottom
        assertEquals(red.argb, buffer.pixels[2 * 5 + 0]) // left
        assertEquals(red.argb, buffer.pixels[2 * 5 + 4]) // right
    }

    @Test
    fun circleFilledDoesNotPaintFourCorners() {
        val render = RenderCircle(radius = 2, color = red, filled = true)
        render.layout(RenderConstraints(maxWidth = 20, maxHeight = 20))
        val buffer = PixelBuffer(width = 5, height = 5).also { it.clear() }

        render.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        // 四角对 r=2 圆形不应被点亮（距离 ≈ √8 > 2）
        assertEquals(PixelColor.Transparent.argb, buffer.pixels[0 * 5 + 0])
        assertEquals(PixelColor.Transparent.argb, buffer.pixels[0 * 5 + 4])
        assertEquals(PixelColor.Transparent.argb, buffer.pixels[4 * 5 + 0])
        assertEquals(PixelColor.Transparent.argb, buffer.pixels[4 * 5 + 4])
    }

    @Test
    fun circleStrokeOnlyDrawsRing() {
        val render = RenderCircle(radius = 3, color = red, filled = false)
        render.layout(RenderConstraints(maxWidth = 20, maxHeight = 20))
        val buffer = PixelBuffer(width = 7, height = 7).also { it.clear() }

        render.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        // 中心点对 stroke 应为透明
        assertEquals(PixelColor.Transparent.argb, buffer.pixels[3 * 7 + 3])
        // 但 cardinal 应当被点亮
        assertEquals(red.argb, buffer.pixels[0 * 7 + 3]) // top
        assertEquals(red.argb, buffer.pixels[3 * 7 + 0]) // left
        // 应当至少有一些像素被点亮
        val lit = buffer.pixels.count { it == red.argb }
        assertTrue("stroke ring should have multiple pixels, got $lit", lit >= 8)
    }

    @Test
    fun circleLayoutIntrinsicSizeIsDiameterPlusOne() {
        val render = RenderCircle(radius = 3, color = red, filled = true)
        render.layout(RenderConstraints(maxWidth = 100, maxHeight = 100))
        assertEquals(7, render.size.width)
        assertEquals(7, render.size.height)
    }
}
