package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.layout.LauncherLayoutProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for [SettingsMenuLayout] — visibleRows (fixed SettingsListGeometry
 * pitch) and largeVisibleRows (double-height SMS-inbox clamp) composed off the
 * header offset and panel padding. Expected values are recomputed through an
 * independent arithmetic path (not the same TextListSupport call) rather than
 * brittle pixel constants. JVM-safe; no Android dependencies.
 */
class SettingsMenuLayoutTest {

    private fun expectedVisibleRows(profile: LauncherLayoutProfile, rowHeight: Int): Int {
        val top = LauncherHeaderLayout.firstContentItemTop(profile)
        val panelBottom = (profile.logicalHeight - LauncherSpacing.CONTENT_VERTICAL).coerceAtLeast(top + 24)
        val height = (panelBottom - top).coerceAtLeast(rowHeight)
        return (height / rowHeight).coerceAtLeast(1)
    }

    @Test
    fun visibleRows_matchesFixedPitchComposition() {
        for (h in listOf(200, 320, 480, 640)) {
            val profile = LauncherLayoutProfile(120, h, 4)
            assertEquals(
                "visibleRows mismatch at height=$h",
                expectedVisibleRows(profile, SettingsListGeometry.ROW_PITCH_PX),
                SettingsMenuLayout.visibleRows(profile),
            )
        }
    }

    @Test
    fun largeVisibleRows_matchesDoubleHeightComposition() {
        val largeRowHeight = PixelFontCatalog.metrics(PixelFontCatalog.defaultUiFontSelection).cellHeight * 2 + 2
        for (h in listOf(200, 320, 480, 640)) {
            val profile = LauncherLayoutProfile(120, h, 4)
            assertEquals(
                "largeVisibleRows mismatch at height=$h",
                expectedVisibleRows(profile, largeRowHeight),
                SettingsMenuLayout.largeVisibleRows(profile),
            )
        }
    }

    @Test
    fun visibleRows_isAtLeastOneForTinyScreens() {
        assertEquals(1, SettingsMenuLayout.visibleRows(LauncherLayoutProfile(64, 1, 4)))
    }

    @Test
    fun visibleRows_usesStatusBarHeightFromProfile() {
        val defaultHeader = SettingsMenuLayout.visibleRows(LauncherLayoutProfile(120, 240, 4))
        val tallerHeader = SettingsMenuLayout.visibleRows(
            LauncherLayoutProfile(
                logicalWidth = 120,
                logicalHeight = 240,
                dotSizePx = 4,
                statusBarHeight = 48,
            ),
        )

        assertEquals(
            expectedVisibleRows(
                LauncherLayoutProfile(120, 240, 4, statusBarHeight = 48),
                SettingsListGeometry.ROW_PITCH_PX,
            ),
            tallerHeader,
        )
        assertTrue("a taller status bar must reduce settings rows", tallerHeader < defaultHeader)
    }
}
