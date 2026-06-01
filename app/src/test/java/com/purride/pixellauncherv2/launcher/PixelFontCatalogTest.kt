package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for [PixelFontCatalog] — size/style labels, the combined diagnostics
 * label, the exposed option lists and the documented defaults. JVM-safe; no
 * Android dependencies.
 */
class PixelFontCatalogTest {

    @Test
    fun sizeLabel_appendsPxSuffix() {
        assertEquals("8PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_8))
        assertEquals("10PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_10))
        assertEquals("12PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_12))
    }

    @Test
    fun styleLabel_mapsBothStyles() {
        assertEquals("MONO", PixelFontCatalog.styleLabel(PixelFontStyle.MONO))
        assertEquals("PROP", PixelFontCatalog.styleLabel(PixelFontStyle.PROP))
    }

    @Test
    fun combinedLabel_joinsFusionSizeAndStyle() {
        assertEquals("FUSION 10 PROP", PixelFontCatalog.combinedLabel(PixelFontSize.PX_10, PixelFontStyle.PROP))
        assertEquals("FUSION 8 MONO", PixelFontCatalog.combinedLabel(PixelFontSize.PX_8, PixelFontStyle.MONO))
    }

    @Test
    fun options_exposeAllEnumEntries() {
        assertEquals(PixelFontSize.entries.toList(), PixelFontCatalog.fontSizeOptions())
        assertEquals(PixelFontStyle.entries.toList(), PixelFontCatalog.fontStyleOptions())
    }

    @Test
    fun defaults_areTenPxProportional() {
        assertEquals(PixelFontSize.PX_10, PixelFontCatalog.defaultFontSize)
        assertEquals(PixelFontStyle.PROP, PixelFontCatalog.defaultFontStyle)
    }
}
