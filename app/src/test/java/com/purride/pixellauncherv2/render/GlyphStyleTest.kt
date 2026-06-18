package com.purride.pixellauncherv2.render

import com.purride.pixellauncherv2.launcher.PixelFontCatalog
import com.purride.pixellauncherv2.launcher.PixelFontStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression guard for the font/viewport coupling.
 *
 * Layout metrics (visibleRows / row heights) derive from [GlyphStyle.APP_LABEL_16]
 * / [GlyphStyle.UI_SMALL_10], which have a fixed 10px cell height. Only the
 * advance width changes with the font style (MONO vs PROP). This test pins that
 * [GlyphStyle.configure] drives those derived widths correctly.
 */
class GlyphStyleTest {

    @After
    fun resetToDefaultFont() {
        GlyphStyle.configure(PixelFontCatalog.defaultFontStyle)
    }

    @Test
    fun cellHeight_isAlways10px() {
        GlyphStyle.configure(PixelFontStyle.MONO)
        assertEquals(10, GlyphStyle.UI_SMALL_10.cellHeight)
        assertEquals(10, GlyphStyle.APP_LABEL_16.cellHeight)

        GlyphStyle.configure(PixelFontStyle.PROP)
        assertEquals(10, GlyphStyle.UI_SMALL_10.cellHeight)
        assertEquals(10, GlyphStyle.APP_LABEL_16.cellHeight)
    }

    @Test
    fun configure_drivesNarrowAdvanceWidth() {
        GlyphStyle.configure(PixelFontStyle.MONO)
        assertEquals(10, GlyphStyle.UI_SMALL_10.narrowAdvanceWidth)

        GlyphStyle.configure(PixelFontStyle.PROP)
        assertEquals(6, GlyphStyle.UI_SMALL_10.narrowAdvanceWidth)
    }
}
