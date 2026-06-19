package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for [PixelFontCatalog] — fixed pixel-size choices and the documented
 * UI default. JVM-safe; no Android dependencies.
 */
class PixelFontCatalogTest {

    @Test
    fun sizeLabel_mapsPixelSizes() {
        assertEquals("8PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_8))
        assertEquals("10PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_10))
        assertEquals("12PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_12))
    }

    @Test
    fun options_exposeAllEnumEntries() {
        assertEquals(PixelFontSize.entries.toList(), PixelFontCatalog.fontSizeOptions())
    }

    @Test
    fun default_is10px() {
        assertEquals(PixelFontSize.PX_10, PixelFontCatalog.defaultUiFontSize)
    }
}
