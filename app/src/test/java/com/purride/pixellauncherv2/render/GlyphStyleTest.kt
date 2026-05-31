package com.purride.pixellauncherv2.render

import com.purride.pixellauncherv2.launcher.PixelFontCatalog
import com.purride.pixellauncherv2.launcher.PixelFontSize
import com.purride.pixellauncherv2.launcher.PixelFontStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression guard for the font/viewport coupling.
 *
 * The launcher `*Layout` metrics (and therefore `visibleRows`, which the state
 * machine uses for selection/scroll) derive their row heights from
 * [GlyphStyle.APP_LABEL_16] / [GlyphStyle.UI_SMALL_10], which in turn reflect
 * whatever font was last passed to [GlyphStyle.configure]. If `configure()` is
 * never called the metrics silently freeze at the default font size — exactly
 * the bug fixed when MainActivity was re-wired to call `configure()` on
 * appearance load and change. This test pins that `configure()` actually drives
 * the derived styles.
 */
class GlyphStyleTest {

    @After
    fun resetToDefaultFont() {
        // GlyphStyle holds global static font state; restore it so other suites
        // observe the default font.
        GlyphStyle.configure(PixelFontCatalog.defaultFontSize, PixelFontCatalog.defaultFontStyle)
    }

    @Test
    fun configure_drivesDerivedStyleCellHeight() {
        GlyphStyle.configure(PixelFontSize.PX_8, PixelFontStyle.MONO)
        assertEquals(8, GlyphStyle.UI_SMALL_10.cellHeight)
        assertEquals(8, GlyphStyle.APP_LABEL_16.cellHeight)

        GlyphStyle.configure(PixelFontSize.PX_12, PixelFontStyle.PROP)
        assertEquals(12, GlyphStyle.UI_SMALL_10.cellHeight)
        assertEquals(12, GlyphStyle.APP_LABEL_16.cellHeight)
    }

    @Test
    fun configure_drivesProportionalAdvanceWidth() {
        GlyphStyle.configure(PixelFontSize.PX_8, PixelFontStyle.PROP)
        assertEquals(4, GlyphStyle.UI_SMALL_10.narrowAdvanceWidth)

        GlyphStyle.configure(PixelFontSize.PX_12, PixelFontStyle.PROP)
        assertEquals(8, GlyphStyle.UI_SMALL_10.narrowAdvanceWidth)
    }
}
