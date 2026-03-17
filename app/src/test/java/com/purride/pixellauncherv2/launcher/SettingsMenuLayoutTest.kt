package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.GlyphStyle
import com.purride.pixellauncherv2.render.ScreenProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsMenuLayoutTest {

    @Test
    fun hitTestMapsLogicalPointToSettingsRow() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )
        val metrics = SettingsMenuLayout.metrics(screenProfile)

        val tappedRow = SettingsMenuLayout.hitTestRow(
            screenProfile = screenProfile,
            logicalX = metrics.rowTextX + 2,
            logicalY = metrics.firstRowY + metrics.rowHeight + 1,
            rowCount = 3,
        )

        assertEquals(1, tappedRow)
    }

    @Test
    fun hitTestResolvesScrolledRowsFromVisibleWindow() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )
        val metrics = SettingsMenuLayout.metrics(screenProfile)

        val tappedRow = SettingsMenuLayout.hitTestRow(
            screenProfile = screenProfile,
            logicalX = metrics.rowTextX + 2,
            logicalY = metrics.firstRowY + 1,
            rowCount = 8,
            listStartIndex = 3,
            scrollOffsetPx = 0,
        )

        assertEquals(3, tappedRow)
    }

    @Test
    fun hitTestReturnsNullOutsideSettingsPanel() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )
        val metrics = SettingsMenuLayout.metrics(screenProfile)

        val tappedRow = SettingsMenuLayout.hitTestRow(
            screenProfile = screenProfile,
            logicalX = metrics.panelX + metrics.panelWidth + 2,
            logicalY = metrics.firstRowY,
            rowCount = 3,
        )

        assertNull(tappedRow)
    }

    @Test
    fun headerLivesAbovePanelAndRowsStartInsidePanel() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )

        val metrics = SettingsMenuLayout.metrics(screenProfile)

        assertEquals(true, LauncherHeaderLayout.rowY < metrics.panelTop)
        assertEquals(true, LauncherHeaderLayout.dividerY < metrics.panelTop)
        assertEquals(true, metrics.firstRowY >= metrics.panelTop)
    }

    @Test
    fun eachRowHasRoomForSingleLineMenuText() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )

        val metrics = SettingsMenuLayout.metrics(screenProfile)

        assertEquals(true, metrics.rowHeight >= GlyphStyle.UI_SMALL_10.cellHeight + 2)
    }

    @Test
    fun settingsRowsSpanFullLogicalWidth() {
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )

        val metrics = SettingsMenuLayout.metrics(screenProfile)

        assertEquals(72, metrics.panelWidth)
        assertEquals(true, metrics.rowMaxTextWidth >= 68)
        assertEquals(true, metrics.visibleRows >= 1)
    }
}
