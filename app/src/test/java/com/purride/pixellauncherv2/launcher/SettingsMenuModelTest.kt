package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.render.ScreenProfileFactory
import com.purride.pixellauncherv2.render.PixelFontSize
import com.purride.pixellauncherv2.render.PixelFontStyle
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
            item = SettingsMenuItem.FONT_SIZE,
            title = "FONT SIZE",
            value = "",
        )

        val displayText = SettingsMenuModel.displayValue(row)

        assertEquals("", displayText)
    }

    @Test
    fun rowsIncludeThemeAndPixelGapItems() {
        val rowItems = SettingsMenuModel.rows(
            LauncherState(
                selectedFontSize = PixelFontSize.PX_12,
                selectedFontStyle = PixelFontStyle.MONO,
                selectedTheme = PixelTheme.AMBER_CRT,
                isPixelGapEnabled = true,
                drawerListAlignment = DrawerListAlignment.RIGHT,
                isIdlePageEnabled = false,
                openDrawerInSearchMode = true,
            ),
            null,
        ).map { it.item }

        assertTrue(SettingsMenuItem.THEME in rowItems)
        assertTrue(SettingsMenuItem.APP_LIST_ALIGNMENT in rowItems)
        assertTrue(SettingsMenuItem.DRAWER_AUTO_SEARCH in rowItems)
        assertTrue(SettingsMenuItem.FONT_SIZE in rowItems)
        assertTrue(SettingsMenuItem.FONT_STYLE in rowItems)
        assertTrue(SettingsMenuItem.PIXEL_GAP in rowItems)
        assertEquals(8, rowItems.size)
    }

    @Test
    fun rowsReflectDrawerBehaviorValues() {
        val rows = SettingsMenuModel.rows(
            LauncherState(
                isPixelGapEnabled = true,
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
        assertEquals(
            "ON",
            rows.first { it.item == SettingsMenuItem.PIXEL_GAP }.value,
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

    @Test
    fun nextFontSizeWrapsAcrossAllOptions() {
        val next = SettingsMenuModel.nextFontSize(PixelFontSize.PX_12, 1)

        assertEquals(PixelFontSize.PX_8, next)
    }

    @Test
    fun nextFontStyleWrapsAcrossAllOptions() {
        val next = SettingsMenuModel.nextFontStyle(PixelFontStyle.PROP, 1)

        assertEquals(PixelFontStyle.MONO, next)
    }
}
