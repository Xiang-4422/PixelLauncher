package com.purride.pixellauncherv2.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelFontCatalogTest {

    @Test
    fun catalogContainsBundledBitmapFamiliesAndThemePresets() {
        val definitions = PixelFontCatalog.all()

        assertTrue(definitions.any { it.id == PixelFontId.WEN_QUAN_YI_BITMAP_SONG_16PX })
        assertTrue(definitions.any { it.id == PixelFontId.GNU_UNIFONT_17_0_03 })
        assertEquals(3, definitions.count { it.familyName == "Dotted Theme" })
        assertEquals(8, definitions.count { it.familyName == "Ark Pixel Font" })
    }

    @Test
    fun catalogIdsAreUniqueAndContainDefaultFont() {
        val definitions = PixelFontCatalog.all()

        assertEquals(definitions.size, definitions.map { it.id }.toSet().size)
        assertTrue(definitions.any { it.id == PixelFontCatalog.defaultFontId })
        assertEquals(PixelFontId.WEN_QUAN_YI_BITMAP_SONG_16PX, PixelFontCatalog.defaultFontId)
    }

    @Test
    fun dottedThemesReuseWenQuanYiGlyphPackAndOnlyChangePixelShape() {
        val square = PixelFontCatalog.definition(PixelFontId.DOTTED_SONGTI_SQUARE)
        val circle = PixelFontCatalog.definition(PixelFontId.DOTTED_SONGTI_CIRCLE)
        val diamond = PixelFontCatalog.definition(PixelFontId.DOTTED_SONGTI_DIAMOND)

        assertEquals(PixelGlyphPackId.WEN_QUAN_YI_BITMAP_SONG_16PX, square.primaryGlyphPackId)
        assertEquals(square.primaryGlyphPackId, circle.primaryGlyphPackId)
        assertEquals(square.primaryGlyphPackId, diamond.primaryGlyphPackId)
        assertEquals(PixelShape.SQUARE, square.pixelShape)
        assertEquals(PixelShape.CIRCLE, circle.pixelShape)
        assertEquals(PixelShape.DIAMOND, diamond.pixelShape)
    }

    @Test
    fun settingsFontOptionsExcludeDottedThemePresets() {
        val settingsOptions = PixelFontCatalog.settingsFontOptions()

        assertTrue(PixelFontId.DOTTED_SONGTI_SQUARE !in settingsOptions)
        assertTrue(PixelFontId.DOTTED_SONGTI_CIRCLE !in settingsOptions)
        assertTrue(PixelFontId.DOTTED_SONGTI_DIAMOND !in settingsOptions)
        assertTrue(PixelFontId.WEN_QUAN_YI_BITMAP_SONG_16PX in settingsOptions)
        assertTrue(PixelFontId.ARK_PIXEL_16PX_MONOSPACED_ZH_CN !in settingsOptions)
        assertTrue(PixelFontId.ARK_PIXEL_16PX_PROPORTIONAL_ZH_CN in settingsOptions)
    }
}
