package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for [PixelTheme.resolve] — AUTO follows the system dark-mode flag while
 * explicit DAY / NIGHT ignore it. This is the resolution applied at the render
 * boundary before a theme file is loaded.
 */
class PixelThemeTest {

    @Test
    fun resolve_autoFollowsSystemDarkMode() {
        assertEquals(PixelTheme.NIGHT, PixelTheme.AUTO.resolve(systemInDarkMode = true))
        assertEquals(PixelTheme.DAY, PixelTheme.AUTO.resolve(systemInDarkMode = false))
    }

    @Test
    fun resolve_explicitThemesIgnoreSystem() {
        assertEquals(PixelTheme.DAY, PixelTheme.DAY.resolve(systemInDarkMode = true))
        assertEquals(PixelTheme.NIGHT, PixelTheme.NIGHT.resolve(systemInDarkMode = false))
    }
}
