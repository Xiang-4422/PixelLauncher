package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `PixelGridGeometryResolver` 的核心模块测试。
 *
 * 这组测试保证像素网格的居中计算、触摸映射和像素间隙策略
 * 已经可以直接由 `:pixel-core` 独立回归验证。
 */
class PixelGridGeometryTest {

    @Test
    fun 解析后得到预期的居中网格几何() {
        val profile = ScreenProfile(
            logicalWidth = 10,
            logicalHeight = 5,
            dotSizePx = 8,
        )

        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = 120,
            viewHeight = 80,
            profile = profile,
        )

        assertNotNull(geometry)
        assertEquals(12f, geometry?.cellSize ?: 0f, 1e-4f)
        assertEquals(0f, geometry?.originX ?: 0f, 1e-4f)
        assertEquals(10f, geometry?.originY ?: 0f, 1e-4f)
    }

    @Test
    fun 触摸映射沿用同一套居中网格规则() {
        val profile = ScreenProfile(
            logicalWidth = 10,
            logicalHeight = 5,
            dotSizePx = 8,
        )

        val logicalPoint = PixelGridGeometryResolver.mapSurfaceToLogical(
            touchX = 25f,
            touchY = 22f,
            viewWidth = 120,
            viewHeight = 80,
            profile = profile,
        )

        assertEquals(2 to 1, logicalPoint)
    }

    @Test
    fun 触摸落在居中内容区外时返回空值() {
        val profile = ScreenProfile(
            logicalWidth = 10,
            logicalHeight = 5,
            dotSizePx = 8,
        )

        val logicalPoint = PixelGridGeometryResolver.mapSurfaceToLogical(
            touchX = 5f,
            touchY = 5f,
            viewWidth = 120,
            viewHeight = 80,
            profile = profile,
        )

        assertNull(logicalPoint)
    }

    @Test
    fun 小尺寸网格使用紧凑点阵内缩() {
        val profile = ScreenProfile(
            logicalWidth = 10,
            logicalHeight = 20,
            dotSizePx = 8,
        )

        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = 80,
            viewHeight = 160,
            profile = profile,
        )

        assertNotNull(geometry)
        assertEquals(8f, geometry?.cellSize ?: 0f, 1e-4f)
        assertEquals(0.5f, geometry?.dotInset ?: 0f, 1e-4f)
        assertEquals(7f, geometry?.dotSize ?: 0f, 1e-4f)
    }

    @Test
    fun 关闭像素间隙时不再保留内缩() {
        val profile = ScreenProfile(
            logicalWidth = 10,
            logicalHeight = 20,
            dotSizePx = 8,
        )

        val geometry = PixelGridGeometryResolver.resolve(
            viewWidth = 80,
            viewHeight = 160,
            profile = profile,
            pixelGapEnabled = false,
        )

        assertNotNull(geometry)
        assertEquals(8f, geometry?.cellSize ?: 0f, 1e-4f)
        assertEquals(0f, geometry?.dotInset ?: -1f, 1e-4f)
        assertEquals(8f, geometry?.dotSize ?: 0f, 1e-4f)
    }
}
