package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `ScreenProfileFactory` 的核心模块测试。
 *
 * 这组测试先在 `:pixel-core` 内重复建立一份，
 * 目的是让屏幕配置相关能力的回归验证逐步脱离 `:app`。
 */
class ScreenProfileFactoryTest {

    @Test
    fun 从1080乘2400屏幕计算逻辑分辨率() {
        val profile = ScreenProfileFactory.create(widthPx = 1080, heightPx = 2400)

        assertEquals(90, profile.logicalWidth)
        assertEquals(200, profile.logicalHeight)
        assertEquals(ScreenProfileFactory.defaultDotSizePx, profile.dotSizePx)
    }

    @Test
    fun 从1080乘1920屏幕计算逻辑分辨率() {
        val profile = ScreenProfileFactory.create(widthPx = 1080, heightPx = 1920)

        assertEquals(90, profile.logicalWidth)
        assertEquals(160, profile.logicalHeight)
    }

    @Test
    fun 从1440乘3200屏幕计算逻辑分辨率() {
        val profile = ScreenProfileFactory.create(widthPx = 1440, heightPx = 3200)

        assertEquals(120, profile.logicalWidth)
        assertEquals(266, profile.logicalHeight)
    }

    @Test
    fun 自定义点阵尺寸时返回对应逻辑分辨率() {
        val profile = ScreenProfileFactory.create(
            widthPx = 1080,
            heightPx = 2400,
            dotSizePx = 12,
        )

        assertEquals(90, profile.logicalWidth)
        assertEquals(200, profile.logicalHeight)
        assertEquals(12, profile.dotSizePx)
    }

    @Test
    fun 分辨率选项返回全部支持的点阵尺寸() {
        val currentProfile = ScreenProfileFactory.create(
            widthPx = 1080,
            heightPx = 2400,
            dotSizePx = 12,
        )

        val options = ScreenProfileFactory.resolutionOptions(currentProfile)

        assertEquals(listOf(7, 8, 10, 12, 14, 16), options)
    }
}
