package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Image` widget render object 的最小回归测试。
 */
class RenderImageTest {

    /** Layout：intrinsic 尺寸下，size 等于 bitmap 自身宽高。 */
    @Test
    fun layoutMatchesBitmapWhenUnconstrained() {
        val bitmap = solidBitmap(width = 4, height = 3, color = PixelColor.fromRgb(0xFF, 0, 0))
        val render = RenderImage(bitmap = bitmap)

        render.layout(RenderConstraints(maxWidth = 100, maxHeight = 100))

        assertEquals(4, render.size.width)
        assertEquals(3, render.size.height)
    }

    /** Layout：父约束更紧时 size 被 clamp，paint 时按 size 裁剪源 bitmap。 */
    @Test
    fun layoutClampsToTightConstraints() {
        val bitmap = solidBitmap(width = 8, height = 8, color = PixelColor.fromRgb(0, 0xFF, 0))
        val render = RenderImage(bitmap = bitmap)

        render.layout(RenderConstraints(maxWidth = 4, maxHeight = 2))

        assertEquals(4, render.size.width)
        assertEquals(2, render.size.height)
    }

    /** Paint：bitmap 像素被按 (offsetX, offsetY) blit 到目标 buffer。 */
    @Test
    fun paintCopiesBitmapPixelsToOffset() {
        val red = PixelColor.fromRgb(0xFF, 0, 0)
        val bitmap = PixelBitmap(
            width = 2,
            height = 2,
            pixels = intArrayOf(red.argb, red.argb, red.argb, red.argb),
        )
        val render = RenderImage(bitmap = bitmap)
        render.layout(RenderConstraints(maxWidth = 10, maxHeight = 10))

        val buffer = PixelBuffer(width = 6, height = 6).also { it.clear() }
        render.paint(context = PaintContext(buffer = buffer), offsetX = 2, offsetY = 1)

        // 期望 (2,1)..(3,2) 4 个像素是红色，其它为透明。
        for (y in 0 until 6) for (x in 0 until 6) {
            val expected = if (x in 2..3 && y in 1..2) red.argb else PixelColor.Transparent.argb
            assertEquals("pixel ($x,$y)", expected, buffer.pixels[y * 6 + x])
        }
    }

    /** Paint：超出目标 buffer 的部分被裁剪而不抛出异常。 */
    @Test
    fun paintClipsAgainstTargetBufferBounds() {
        val red = PixelColor.fromRgb(0xFF, 0, 0)
        val bitmap = solidBitmap(width = 4, height = 4, color = red)
        val render = RenderImage(bitmap = bitmap)
        render.layout(RenderConstraints(maxWidth = 10, maxHeight = 10))

        val buffer = PixelBuffer(width = 4, height = 4).also { it.clear() }
        // 把 bitmap 偏移到右下角越界，应只画出左上角 2x2。
        render.paint(context = PaintContext(buffer = buffer), offsetX = 2, offsetY = 2)

        for (y in 0 until 4) for (x in 0 until 4) {
            val expected = if (x in 2..3 && y in 2..3) red.argb else PixelColor.Transparent.argb
            assertEquals("pixel ($x,$y)", expected, buffer.pixels[y * 4 + x])
        }
    }

    /** Paint：layout 被父节点压窄时只绘制 size 范围内的源像素，不画超出部分。 */
    @Test
    fun paintRespectsTightenedSize() {
        val red = PixelColor.fromRgb(0xFF, 0, 0)
        val bitmap = solidBitmap(width = 4, height = 4, color = red)
        val render = RenderImage(bitmap = bitmap)
        // 父只给 2x2，剩下 2 行 / 2 列应当不被画
        render.layout(RenderConstraints(maxWidth = 2, maxHeight = 2))

        val buffer = PixelBuffer(width = 6, height = 6).also { it.clear() }
        render.paint(context = PaintContext(buffer = buffer), offsetX = 0, offsetY = 0)

        for (y in 0 until 6) for (x in 0 until 6) {
            val expected = if (x < 2 && y < 2) red.argb else PixelColor.Transparent.argb
            assertEquals("pixel ($x,$y)", expected, buffer.pixels[y * 6 + x])
        }
    }

    @Test
    fun paintKeepsDestinationForTransparentSourcePixels() {
        val blue = PixelColor.fromRgb(0, 0, 255)
        val bitmap = PixelBitmap(
            width = 2,
            height = 1,
            pixels = intArrayOf(PixelColor.Transparent.argb, PixelColor.fromArgb(128, 255, 0, 0).argb),
        )
        val render = RenderImage(bitmap = bitmap)
        render.layout(RenderConstraints(maxWidth = 2, maxHeight = 1))

        val buffer = PixelBuffer(width = 2, height = 1)
        buffer.fillRect(0, 0, 2, 1, blue)
        render.paint(context = PaintContext(buffer = buffer), offsetX = 0, offsetY = 0)

        assertEquals(blue.argb, buffer.getPixel(0, 0).argb)
        val blended = buffer.getPixel(1, 0)
        assertEquals(255, blended.alpha)
        assertTrue(blended.red in 127..129)
        assertEquals(0, blended.green)
        assertTrue(blended.blue in 126..128)
    }

    /** 更新到不同尺寸的 bitmap 后 layout 应当重新计算 size。 */
    @Test
    fun updateBitmapWithDifferentSizeReLayouts() {
        val small = solidBitmap(width = 2, height = 2, color = PixelColor.fromRgb(0xFF, 0, 0))
        val large = solidBitmap(width = 5, height = 5, color = PixelColor.fromRgb(0, 0, 0xFF))
        val render = RenderImage(bitmap = small)

        render.layout(RenderConstraints(maxWidth = 10, maxHeight = 10))
        assertEquals(2, render.size.width)

        render.updateBitmap(large)
        render.layout(RenderConstraints(maxWidth = 10, maxHeight = 10))
        assertEquals(5, render.size.width)
        assertEquals(5, render.size.height)

        assertNotEquals("identity should change after update", small, large)
    }

    private fun solidBitmap(width: Int, height: Int, color: PixelColor): PixelBitmap {
        val pixels = IntArray(width * height) { color.argb }
        return PixelBitmap(width = width, height = height, pixels = pixels)
    }
}
