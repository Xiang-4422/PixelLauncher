package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.PixelShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for [SettingsMenuModel] — the deterministic settings value logic
 * (cyclic option stepping, slider ratio mapping, gap snapping, labels and the
 * conditional row list). All JVM-safe; no Android dependencies.
 */
class SettingsMenuModelTest {

    // ── Cyclic option stepping (wrapIndex) ────────────────────────────────────

    @Test
    fun nextFontSize_stepsForwardAndWrapsAround() {
        assertEquals(PixelFontSize.PX_10, SettingsMenuModel.nextFontSize(PixelFontSize.PX_8, 1))
        assertEquals(PixelFontSize.PX_8, SettingsMenuModel.nextFontSize(PixelFontSize.PX_12, 1))
    }

    @Test
    fun nextFontSize_stepsBackwardWrapsToLast() {
        assertEquals(PixelFontSize.PX_12, SettingsMenuModel.nextFontSize(PixelFontSize.PX_8, -1))
    }

    @Test
    fun nextFontStyle_togglesBetweenTheTwoOptions() {
        assertEquals(PixelFontStyle.PROP, SettingsMenuModel.nextFontStyle(PixelFontStyle.MONO, 1))
        assertEquals(PixelFontStyle.MONO, SettingsMenuModel.nextFontStyle(PixelFontStyle.PROP, 1))
    }

    @Test
    fun nextStyle_cyclesThroughShapes() {
        assertEquals(PixelShape.CIRCLE, SettingsMenuModel.nextStyle(PixelShape.SQUARE, 1))
        assertEquals(PixelShape.SQUARE, SettingsMenuModel.nextStyle(PixelShape.DIAMOND, 1))
        assertEquals(PixelShape.DIAMOND, SettingsMenuModel.nextStyle(PixelShape.SQUARE, -1))
    }

    @Test
    fun nextDrawerListAlignment_cyclesLeftCenterRight() {
        assertEquals(DrawerListAlignment.CENTER, SettingsMenuModel.nextDrawerListAlignment(DrawerListAlignment.LEFT, 1))
        assertEquals(DrawerListAlignment.LEFT, SettingsMenuModel.nextDrawerListAlignment(DrawerListAlignment.RIGHT, 1))
    }

    @Test
    fun nextTheme_wrapsAtBothEndsAndRoundTrips() {
        val first = PixelTheme.entries.first()
        val last = PixelTheme.entries.last()
        assertEquals(last, SettingsMenuModel.nextTheme(first, -1))
        assertEquals(first, SettingsMenuModel.nextTheme(last, 1))
        // forward then backward returns to the original
        val stepped = SettingsMenuModel.nextTheme(first, 1)
        assertEquals(first, SettingsMenuModel.nextTheme(stepped, -1))
    }

    // ── Slider ratio mapping (font size) ──────────────────────────────────────

    @Test
    fun fontSizeRatio_mapsEndpointsToZeroAndOne() {
        assertEquals(0f, SettingsMenuModel.fontSizeRatio(PixelFontSize.PX_8), 0f)
        assertEquals(1f, SettingsMenuModel.fontSizeRatio(PixelFontSize.PX_12), 0f)
    }

    @Test
    fun fontSizeAtRatio_isInverseOfFontSizeRatio() {
        for (size in SettingsMenuModel.fontSizeOptions) {
            assertEquals(size, SettingsMenuModel.fontSizeAtRatio(SettingsMenuModel.fontSizeRatio(size)))
        }
    }

    // ── Pixel gap ratio stepping + snapping ───────────────────────────────────

    @Test
    fun nextPixelGapRatio_stepsToNextDiscreteStop() {
        assertEquals(0.75f, SettingsMenuModel.nextPixelGapRatio(0.5f, 1), 0f)
    }

    @Test
    fun nextPixelGapRatio_wrapsAroundEnds() {
        assertEquals(0f, SettingsMenuModel.nextPixelGapRatio(1f, 1), 0f)
        assertEquals(1f, SettingsMenuModel.nextPixelGapRatio(0f, -1), 0f)
    }

    @Test
    fun pixelGapRatioSnap_picksNearestStopAndClamps() {
        assertEquals(0.5f, SettingsMenuModel.pixelGapRatioSnap(0.6f), 0f)
        assertEquals(1f, SettingsMenuModel.pixelGapRatioSnap(1.5f), 0f)
        assertEquals(0f, SettingsMenuModel.pixelGapRatioSnap(-1f), 0f)
    }

    // ── Labels + toggles ──────────────────────────────────────────────────────

    @Test
    fun labelsAndToggleAreStable() {
        assertEquals("ON", SettingsMenuModel.onOffLabel(true))
        assertEquals("OFF", SettingsMenuModel.onOffLabel(false))
        assertFalse(SettingsMenuModel.toggle(true))
        assertEquals("SQUARE", SettingsMenuModel.styleLabel(PixelShape.SQUARE))
        assertEquals("CENTER", SettingsMenuModel.drawerListAlignmentLabel(DrawerListAlignment.CENTER))
    }

    @Test
    fun pixelGapSizeLabel_rendersClampedPercent() {
        assertEquals("0%", SettingsMenuModel.pixelGapSizeLabel(0f))
        assertEquals("50%", SettingsMenuModel.pixelGapSizeLabel(0.5f))
        assertEquals("100%", SettingsMenuModel.pixelGapSizeLabel(1.5f))
    }

    @Test
    fun displayValue_wrapsNonBlankInAngleBrackets() {
        assertEquals("<MONO>", SettingsMenuModel.displayValue(SettingsMenuRow(SettingsMenuItem.FONT_STYLE, "FONT STYLE", "MONO")))
        assertEquals("", SettingsMenuModel.displayValue(SettingsMenuRow(SettingsMenuItem.FONT_STYLE, "FONT STYLE", "")))
    }

    // ── rows() conditional content + selectedItem clamping ────────────────────

    @Test
    fun rows_includeStyleRowOnlyWhenPixelGapEnabled() {
        val withGap = SettingsMenuModel.rows(LauncherState(isPixelGapEnabled = true)).map { it.item }
        val withoutGap = SettingsMenuModel.rows(LauncherState(isPixelGapEnabled = false)).map { it.item }
        assertTrue(withGap.contains(SettingsMenuItem.STYLE))
        assertFalse(withoutGap.contains(SettingsMenuItem.STYLE))
    }

    @Test
    fun selectedItem_clampsOutOfRangeIndexToLastRow() {
        val state = LauncherState(settingsSelectedIndex = 999)
        val expected = SettingsMenuModel.rows(state).last().item
        assertEquals(expected, SettingsMenuModel.selectedItem(state))
    }

    @Test
    fun selectedItem_firstRowIsFontSize() {
        assertEquals(SettingsMenuItem.FONT_SIZE, SettingsMenuModel.selectedItem(LauncherState(settingsSelectedIndex = 0)))
    }
}
