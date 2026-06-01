package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.GlyphStyle
import com.purride.pixellauncherv2.render.ScreenProfile
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for [SettingsMenuLayout] — visibleRows (fixed SettingsListGeometry
 * pitch) and largeVisibleRows (double-height SMS-inbox clamp) composed off the
 * header offset and panel padding. Expected values are recomputed through an
 * independent arithmetic path (not the same TextListSupport call) rather than
 * brittle pixel constants. JVM-safe; no Android dependencies.
 */
class SettingsMenuLayoutTest {

    private fun expectedVisibleRows(profile: ScreenProfile, rowHeight: Int): Int {
        val top = LauncherHeaderLayout.firstContentItemTop
        val panelBottom = (profile.logicalHeight - 4).coerceAtLeast(top + 24)
        val height = (panelBottom - top).coerceAtLeast(rowHeight)
        return (height / rowHeight).coerceAtLeast(1)
    }

    @Test
    fun visibleRows_matchesFixedPitchComposition() {
        for (h in listOf(200, 320, 480, 640)) {
            val profile = ScreenProfile(120, h, 4)
            assertEquals(
                "visibleRows mismatch at height=$h",
                expectedVisibleRows(profile, SettingsListGeometry.ROW_PITCH_PX),
                SettingsMenuLayout.visibleRows(profile),
            )
        }
    }

    @Test
    fun largeVisibleRows_matchesDoubleHeightComposition() {
        val largeRowHeight = GlyphStyle.APP_LABEL_16.cellHeight * 2 + 2
        for (h in listOf(200, 320, 480, 640)) {
            val profile = ScreenProfile(120, h, 4)
            assertEquals(
                "largeVisibleRows mismatch at height=$h",
                expectedVisibleRows(profile, largeRowHeight),
                SettingsMenuLayout.largeVisibleRows(profile),
            )
        }
    }

    @Test
    fun visibleRows_isAtLeastOneForTinyScreens() {
        assertEquals(1, SettingsMenuLayout.visibleRows(ScreenProfile(64, 1, 4)))
    }
}
