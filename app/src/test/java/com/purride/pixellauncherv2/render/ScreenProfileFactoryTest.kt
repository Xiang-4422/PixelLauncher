package com.purride.pixellauncherv2.render

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenProfileFactoryTest {

    @Test
    fun createsLogicalSizeFrom1080x2400Screen() {
        val profile = ScreenProfileFactory.create(widthPx = 1080, heightPx = 2400)

        assertEquals(90, profile.logicalWidth)
        assertEquals(200, profile.logicalHeight)
        assertEquals(ScreenProfileFactory.defaultDotSizePx, profile.dotSizePx)
    }

    @Test
    fun createsLogicalSizeFrom1080x1920Screen() {
        val profile = ScreenProfileFactory.create(widthPx = 1080, heightPx = 1920)

        assertEquals(90, profile.logicalWidth)
        assertEquals(160, profile.logicalHeight)
    }

    @Test
    fun createsLogicalSizeFrom1440x3200Screen() {
        val profile = ScreenProfileFactory.create(widthPx = 1440, heightPx = 3200)

        assertEquals(120, profile.logicalWidth)
        assertEquals(266, profile.logicalHeight)
    }

    @Test
    fun createsLogicalSizeFromCustomDotSizePreset() {
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
    fun resolutionOptionsAlwaysExposeAllSupportedPixelSizes() {
        val currentProfile = ScreenProfileFactory.create(
            widthPx = 1080,
            heightPx = 2400,
            dotSizePx = 12,
        )

        val options = ScreenProfileFactory.resolutionOptions(currentProfile)

        assertEquals(listOf(7, 8, 10, 12, 14, 16), options)
    }
}
