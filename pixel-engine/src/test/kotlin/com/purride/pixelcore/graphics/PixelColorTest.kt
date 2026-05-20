package com.purride.pixelcore.graphics

import com.purride.pixelcore.PixelColor
import org.junit.Assert.assertEquals
import org.junit.Test

class PixelColorTest {

    // ── fromArgb 通道分解 ──────────────────────────────────────────────────

    @Test
    fun `fromArgb stores all channels correctly`() {
        val color = PixelColor.fromArgb(255, 128, 64, 32)
        assertEquals(255, color.alpha)
        assertEquals(128, color.red)
        assertEquals(64, color.green)
        assertEquals(32, color.blue)
    }

    @Test
    fun `fromArgb with zero channels`() {
        val color = PixelColor.fromArgb(0, 0, 0, 0)
        assertEquals(0, color.alpha)
        assertEquals(0, color.red)
        assertEquals(0, color.green)
        assertEquals(0, color.blue)
        assertEquals(0, color.argb)
    }

    @Test
    fun `fromArgb with max channels`() {
        val color = PixelColor.fromArgb(255, 255, 255, 255)
        assertEquals(255, color.alpha)
        assertEquals(255, color.red)
        assertEquals(255, color.green)
        assertEquals(255, color.blue)
        assertEquals(-1, color.argb) // 0xFFFFFFFF.toInt() == -1
    }

    @Test
    fun `fromArgb red produces correct argb int`() {
        val color = PixelColor.fromArgb(255, 255, 0, 0)
        assertEquals(0xFFFF0000.toInt(), color.argb)
    }

    @Test
    fun `fromArgb green produces correct argb int`() {
        val color = PixelColor.fromArgb(255, 0, 255, 0)
        assertEquals(0xFF00FF00.toInt(), color.argb)
    }

    @Test
    fun `fromArgb blue produces correct argb int`() {
        val color = PixelColor.fromArgb(255, 0, 0, 255)
        assertEquals(0xFF0000FF.toInt(), color.argb)
    }

    // ── fromRgb ───────────────────────────────────────────────────────────

    @Test
    fun `fromRgb sets alpha to 0xFF`() {
        val color = PixelColor.fromRgb(100, 150, 200)
        assertEquals(0xFF, color.alpha)
        assertEquals(100, color.red)
        assertEquals(150, color.green)
        assertEquals(200, color.blue)
    }

    // ── Transparent ───────────────────────────────────────────────────────

    @Test
    fun `Transparent has zero alpha`() {
        assertEquals(0, PixelColor.Transparent.alpha)
    }

    @Test
    fun `Transparent has argb zero`() {
        assertEquals(0, PixelColor.Transparent.argb)
    }

    // ── 边界值 ────────────────────────────────────────────────────────────

    @Test
    fun `channel values are masked to 0xFF`() {
        // 超出 0xFF 的通道值只取低 8 位
        val color = PixelColor.fromArgb(0x1FF, 0x1FF, 0x1FF, 0x1FF)
        assertEquals(0xFF, color.alpha)
        assertEquals(0xFF, color.red)
        assertEquals(0xFF, color.green)
        assertEquals(0xFF, color.blue)
    }

    // ── value class 相等性 ────────────────────────────────────────────────

    @Test
    fun `same argb produces equal PixelColor`() {
        val a = PixelColor.fromArgb(255, 10, 20, 30)
        val b = PixelColor.fromArgb(255, 10, 20, 30)
        assertEquals(a, b)
    }
}
