package com.purride.pixellauncherv2.render

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression guard for the font/viewport coupling.
 *
 * Layout metrics (visibleRows / row heights) derive from [GlyphStyle.APP_LABEL_16]
 * / [GlyphStyle.UI_SMALL_10], which have fixed metrics. Font style is not a
 * user setting, so these values must stay deterministic.
 */
class GlyphStyleTest {

    @Test
    fun cellHeight_isAlways10px() {
        assertEquals(10, GlyphStyle.UI_SMALL_10.cellHeight)
        assertEquals(10, GlyphStyle.APP_LABEL_16.cellHeight)
    }

    @Test
    fun narrowAdvanceWidth_isStable() {
        assertEquals(6, GlyphStyle.UI_SMALL_10.narrowAdvanceWidth)
        assertEquals(6, GlyphStyle.APP_LABEL_16.narrowAdvanceWidth)
    }
}
