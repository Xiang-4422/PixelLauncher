package com.purride.pixellauncherv2.render

import org.junit.Assert.assertEquals
import org.junit.Test

class PixelFontCatalogTest {

    @Test
    fun defaultSelectionUsesFusionTenProportional() {
        assertEquals(PixelFontSize.PX_10, PixelFontCatalog.defaultFontSize)
        assertEquals(PixelFontStyle.PROP, PixelFontCatalog.defaultFontStyle)
    }

    @Test
    fun sizeAndStyleOptionsExposeFusionCombinations() {
        assertEquals(listOf(PixelFontSize.PX_8, PixelFontSize.PX_10, PixelFontSize.PX_12), PixelFontCatalog.fontSizeOptions())
        assertEquals(listOf(PixelFontStyle.MONO, PixelFontStyle.PROP), PixelFontCatalog.fontStyleOptions())
    }

    @Test
    fun definitionReturnsFusionPackIds() {
        val definition = PixelFontCatalog.definition(PixelFontSize.PX_12, PixelFontStyle.MONO)

        assertEquals(PixelGlyphPackId.FUSION_PIXEL_12PX_MONOSPACED_LATIN, definition.latinPackId)
        assertEquals(PixelGlyphPackId.FUSION_PIXEL_12PX_MONOSPACED_ZH_HANS, definition.zhHansPackId)
    }

    @Test
    fun combinedLabelReflectsSelection() {
        assertEquals("FUSION 8 MONO", PixelFontCatalog.combinedLabel(PixelFontSize.PX_8, PixelFontStyle.MONO))
        assertEquals("FUSION 12 PROP", PixelFontCatalog.combinedLabel(PixelFontSize.PX_12, PixelFontStyle.PROP))
    }
}
