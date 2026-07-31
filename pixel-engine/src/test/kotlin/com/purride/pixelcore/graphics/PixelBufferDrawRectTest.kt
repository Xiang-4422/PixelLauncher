package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `drawRect` 的边框像素必须恰好绘制一次。
 *
 * 历史实现的横向循环画整条顶边/底边、纵向循环画整条左边/右边，四角像素被两个循环
 * 各画一次；`rectHeight == 1` 时 top == bottom，横向循环对每个像素画两次；`1×1` 时
 * 单像素累计四次。不透明色与 Src/Clear 模式下重复写入结果相同看不出来，半透明
 * SrcOver 会逐次叠加混合，让四角与退化尺寸明显变深。
 */
class PixelBufferDrawRectTest {

    private val opaqueRed = PixelColor.fromRgb(255, 0, 0)

    /** alpha=128 的白色叠在黑底上：混合一次 ≈128 灰，混合两次 ≈192 灰，可精确区分次数。 */
    private val translucentWhite = PixelColor.fromArgb(128, 255, 255, 255)

    private fun blackBuffer(width: Int, height: Int): PixelBuffer {
        val buf = PixelBuffer(width = width, height = height)
        buf.fillRect(0, 0, width, height, PixelColor.fromRgb(0, 0, 0), PixelBlendMode.Src)
        return buf
    }

    /** SrcOver 单次混合的期望值（+127/255 整数舍入，un-premultiplied，黑底不透明）。 */
    private fun blendOnceOnBlack(srcAlpha: Int, srcChannel: Int): Int =
        (srcChannel * srcAlpha + 127) / 255

    @Test
    fun opaqueRectOutlinesExactBorder() {
        val buf = blackBuffer(5, 4)
        buf.drawRect(0, 0, 5, 4, opaqueRed)
        for (y in 0 until 4) {
            for (x in 0 until 5) {
                val onBorder = x == 0 || x == 4 || y == 0 || y == 3
                val expected = if (onBorder) opaqueRed else PixelColor.fromRgb(0, 0, 0)
                assertEquals("pixel ($x,$y)", expected, buf.getPixel(x, y))
            }
        }
    }

    @Test
    fun translucentCornersBlendExactlyOnce() {
        val buf = blackBuffer(4, 4)
        buf.drawRect(0, 0, 4, 4, translucentWhite)
        val expectedChannel = blendOnceOnBlack(srcAlpha = 128, srcChannel = 255)
        val corners = listOf(0 to 0, 3 to 0, 0 to 3, 3 to 3)
        val edges = listOf(1 to 0, 0 to 1, 3 to 2, 2 to 3)
        for ((x, y) in corners + edges) {
            val pixel = buf.getPixel(x, y).argb
            val r = (pixel ushr 16) and 0xFF
            assertEquals("corner/edge ($x,$y) 必须只混合一次", expectedChannel, r)
        }
    }

    @Test
    fun singleRowRectBlendsEachPixelOnce() {
        val buf = blackBuffer(5, 3)
        buf.drawRect(1, 1, 3, 1, translucentWhite)
        val expectedChannel = blendOnceOnBlack(srcAlpha = 128, srcChannel = 255)
        for (x in 1..3) {
            val r = (buf.getPixel(x, 1).argb ushr 16) and 0xFF
            assertEquals("单行矩形像素 ($x,1) 只混合一次", expectedChannel, r)
        }
        assertEquals("行外像素不受影响", PixelColor.fromRgb(0, 0, 0), buf.getPixel(0, 1))
    }

    @Test
    fun singleColumnRectBlendsEachPixelOnce() {
        val buf = blackBuffer(3, 5)
        buf.drawRect(1, 1, 1, 3, translucentWhite)
        val expectedChannel = blendOnceOnBlack(srcAlpha = 128, srcChannel = 255)
        for (y in 1..3) {
            val r = (buf.getPixel(1, y).argb ushr 16) and 0xFF
            assertEquals("单列矩形像素 (1,$y) 只混合一次", expectedChannel, r)
        }
    }

    @Test
    fun onePixelRectBlendsExactlyOnce() {
        val buf = blackBuffer(3, 3)
        buf.drawRect(1, 1, 1, 1, translucentWhite)
        val expectedChannel = blendOnceOnBlack(srcAlpha = 128, srcChannel = 255)
        val r = (buf.getPixel(1, 1).argb ushr 16) and 0xFF
        assertEquals("1×1 矩形只混合一次", expectedChannel, r)
    }

    @Test
    fun rectPartiallyOutsideBufferClipsWithoutError() {
        val buf = blackBuffer(4, 4)
        buf.drawRect(-2, -2, 5, 5, opaqueRed)
        // 缓冲内可见的边只有右边 x=2 与底边 y=2。
        for (i in 0..2) {
            assertEquals("右边 (2,$i)", opaqueRed, buf.getPixel(2, i))
            assertEquals("底边 ($i,2)", opaqueRed, buf.getPixel(i, 2))
        }
        assertEquals("内部不填充", PixelColor.fromRgb(0, 0, 0), buf.getPixel(1, 1))
        assertEquals("边框外不受影响", PixelColor.fromRgb(0, 0, 0), buf.getPixel(3, 3))
    }

    @Test
    fun zeroOrNegativeSizeDrawsNothing() {
        val buf = blackBuffer(3, 3)
        buf.drawRect(1, 1, 0, 2, translucentWhite)
        buf.drawRect(1, 1, 2, 0, translucentWhite)
        buf.drawRect(1, 1, -1, -1, translucentWhite)
        for (y in 0 until 3) {
            for (x in 0 until 3) {
                assertEquals(PixelColor.fromRgb(0, 0, 0), buf.getPixel(x, y))
            }
        }
    }

    @Test
    fun translucentOutlineNeverExceedsSingleBlendValue() {
        // 全量扫描 2..6 尺寸组合：任何像素通道值超过单次混合期望即说明存在重复混合。
        val expectedChannel = blendOnceOnBlack(srcAlpha = 128, srcChannel = 255)
        for (w in 1..6) {
            for (h in 1..6) {
                val buf = blackBuffer(8, 8)
                buf.drawRect(1, 1, w, h, translucentWhite)
                for (y in 0 until 8) {
                    for (x in 0 until 8) {
                        val r = (buf.getPixel(x, y).argb ushr 16) and 0xFF
                        assertTrue(
                            "尺寸 ${w}x$h 像素 ($x,$y) 通道值 $r 超过单次混合期望 $expectedChannel",
                            r == 0 || r == expectedChannel,
                        )
                    }
                }
            }
        }
    }
}
