package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.render.ScreenProfileFactory
import com.purride.pixellauncherv2.render.PixelTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMenuModelTest {

    @Test
    fun rowsShowCurrentLogicalResolutionForResolutionItem() {
        val state = LauncherState(
            selectedDotSizePx = 15,
        )
        val screenProfile = ScreenProfile(
            logicalWidth = 72,
            logicalHeight = 160,
            dotSizePx = 15,
        )

        val resolutionRow = SettingsMenuModel.rows(state, screenProfile)
            .first { it.item == SettingsMenuItem.RESOLUTION }

        assertEquals("72X160", resolutionRow.value)
    }

    @Test
    fun nextResolutionWrapsAcrossSupportedPresets() {
        val current = ScreenProfileFactory.supportedDotSizePxOptions.last()

        val next = SettingsMenuModel.nextResolution(current, 1)

        assertEquals(ScreenProfileFactory.supportedDotSizePxOptions.first(), next)
    }

    @Test
    fun displayValueWrapsOptionInAngleBrackets() {
        val row = SettingsMenuRow(
            item = SettingsMenuItem.STYLE,
            title = "STYLE",
            value = "SQUARE",
        )

        val displayText = SettingsMenuModel.displayValue(row)

        assertEquals("<SQUARE>", displayText)
    }

    @Test
    fun displayValueReturnsEmptyForBlankRows() {
        val row = SettingsMenuRow(
            item = SettingsMenuItem.FONT,
            title = "FONT",
            value = "",
        )

        val displayText = SettingsMenuModel.displayValue(row)

        assertEquals("", displayText)
    }

    @Test
    fun rowsIncludeThemeAndAdvancedItems() {
        val rowItems = SettingsMenuModel.rows(
            LauncherState(selectedTheme = PixelTheme.AMBER_CRT),
            null,
        ).map { it.item }

        assertTrue(SettingsMenuItem.THEME in rowItems)
        assertTrue(SettingsMenuItem.ADVANCED in rowItems)
    }
}
