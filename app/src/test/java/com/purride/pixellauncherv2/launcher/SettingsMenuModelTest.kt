package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.render.ScreenProfileFactory
import com.purride.pixellauncherv2.render.PixelTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMenuModelTest {

    @Test
    fun rowsShowPixelSizeForResolutionItem() {
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

        assertEquals("PIXEL SIZE", resolutionRow.title)
        assertEquals("15PX", resolutionRow.value)
    }

    @Test
    fun nextResolutionWrapsAcrossSupportedPresets() {
        val screenProfile = ScreenProfileFactory.create(
            widthPx = 1080,
            heightPx = 2400,
            dotSizePx = 12,
        )
        val current = ScreenProfileFactory.resolutionOptions(screenProfile).last()

        val next = SettingsMenuModel.nextResolution(current, 1, screenProfile)

        assertEquals(ScreenProfileFactory.resolutionOptions(screenProfile).first(), next)
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
            LauncherState(
                selectedTheme = PixelTheme.AMBER_CRT,
                drawerListAlignment = DrawerListAlignment.RIGHT,
                isIdlePageEnabled = false,
                openDrawerInSearchMode = true,
            ),
            null,
        ).map { it.item }

        assertTrue(SettingsMenuItem.THEME in rowItems)
        assertTrue(SettingsMenuItem.APP_LIST_ALIGNMENT in rowItems)
        assertTrue(SettingsMenuItem.DRAWER_AUTO_SEARCH in rowItems)
        assertEquals(6, rowItems.size)
    }

    @Test
    fun rowsReflectDrawerBehaviorValues() {
        val rows = SettingsMenuModel.rows(
            LauncherState(
                drawerListAlignment = DrawerListAlignment.CENTER,
                isIdlePageEnabled = false,
                openDrawerInSearchMode = true,
            ),
            null,
        )

        assertEquals(
            "CENTER",
            rows.first { it.item == SettingsMenuItem.APP_LIST_ALIGNMENT }.value,
        )
        assertEquals(
            "ON",
            rows.first { it.item == SettingsMenuItem.DRAWER_AUTO_SEARCH }.value,
        )
    }

    @Test
    fun nextDrawerListAlignmentWrapsAcrossAllOptions() {
        val next = SettingsMenuModel.nextDrawerListAlignment(DrawerListAlignment.RIGHT, 1)

        assertEquals(DrawerListAlignment.LEFT, next)
    }

    @Test
    fun themeLabelIncludesMonoTheme() {
        val label = SettingsMenuModel.themeLabel(PixelTheme.MONO_LCD)

        assertEquals("MONO", label)
    }

    @Test
    fun themeLabelIncludesNightTheme() {
        val label = SettingsMenuModel.themeLabel(PixelTheme.NIGHT_MONO)

        assertEquals("NIGHT", label)
    }
}
