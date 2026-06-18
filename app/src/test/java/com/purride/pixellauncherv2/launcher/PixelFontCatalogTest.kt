package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for [PixelFontCatalog] — style labels, the exposed option list and
 * the documented default. JVM-safe; no Android dependencies.
 */
class PixelFontCatalogTest {

    @Test
    fun styleLabel_mapsBothStyles() {
        assertEquals("MONO", PixelFontCatalog.styleLabel(PixelFontStyle.MONO))
        assertEquals("PROP", PixelFontCatalog.styleLabel(PixelFontStyle.PROP))
    }

    @Test
    fun options_exposeAllEnumEntries() {
        assertEquals(PixelFontStyle.entries.toList(), PixelFontCatalog.fontStyleOptions())
    }

    @Test
    fun default_isProportional() {
        assertEquals(PixelFontStyle.PROP, PixelFontCatalog.defaultFontStyle)
    }
}
