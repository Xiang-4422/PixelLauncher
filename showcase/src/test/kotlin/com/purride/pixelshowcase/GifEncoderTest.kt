package com.purride.pixelshowcase

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GIF 编码结果用 JDK 自带的 ImageIO 解码回来验证——编码器
 * 说自己写对了不算数，独立实现的解码器读得回来才算。
 */
class GifEncoderTest {

    @Test
    fun singleFrameRoundTripsExactPixels() {
        val width = 4
        val height = 3
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val frame = IntArray(width * height) { if (it % 2 == 0) red else blue }

        val bytes = GifEncoder.encode(width, height, listOf(frame), delayCentis = 10)
        val decoded = ImageIO.read(ByteArrayInputStream(bytes))

        assertEquals(width, decoded.width)
        assertEquals(height, decoded.height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals(frame[y * width + x], decoded.getRGB(x, y))
            }
        }
    }

    @Test
    fun multiFrameGifExposesEveryFrame() {
        val width = 8
        val height = 8
        val frames = (0 until 5).map { index ->
            IntArray(width * height) { pixel ->
                if (pixel % (index + 2) == 0) 0xFF10203A.toInt() else 0xFFECF4FF.toInt()
            }
        }

        val bytes = GifEncoder.encode(width, height, frames, delayCentis = 8)

        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        assertEquals(frames.size, reader.getNumImages(true))
        val first = reader.read(0)
        assertEquals(0xFF10203A.toInt(), first.getRGB(0, 0))
        reader.dispose()
    }

    @Test
    fun manyColorsQuantizeInsteadOfFailing() {
        // 32x32 渐变 = 1024 种颜色，必须量化到 256 色以内而不是抛异常。
        val width = 32
        val height = 32
        val frame = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            0xFF000000.toInt() or (x * 8 shl 16) or (y * 8 shl 8) or ((x + y) * 4)
        }

        val bytes = GifEncoder.encode(width, height, listOf(frame), delayCentis = 10)
        val decoded = ImageIO.read(ByteArrayInputStream(bytes))

        assertEquals(width, decoded.width)
        // 量化后颜色仍应大致保序：左上比右下暗。
        val topLeft = decoded.getRGB(0, 0) and 0xFFFFFF
        val bottomRight = decoded.getRGB(width - 1, height - 1) and 0xFFFFFF
        assertTrue(topLeft < bottomRight)
    }

    @Test
    fun longRunsCompressBelowRawSize() {
        // 单色大图：LZW 对长重复串必须显著小于裸索引流。
        val width = 64
        val height = 64
        val frame = IntArray(width * height) { 0xFF224466.toInt() }

        val bytes = GifEncoder.encode(width, height, listOf(frame), delayCentis = 10)

        assertTrue("期望 LZW 压缩生效，实际 ${bytes.size} 字节", bytes.size < width * height / 4)
        val decoded = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(0xFF224466.toInt(), decoded.getRGB(63, 63))
    }
}
