package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.GlyphStyle
import com.purride.pixellauncherv2.layout.LauncherLayoutProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for [AppListLayout.visibleRows] — verifies it composes the shared
 * drawer row geometry (DrawerListGeometry.rowPitch of APP_LABEL_16) and the
 * header content offset into a visible-row count. Expected values are recomputed
 * from the same public parts rather than asserting brittle pixel constants.
 * JVM-safe; no Android dependencies.
 */
class AppListLayoutTest {

    private fun expectedVisibleRows(profile: LauncherLayoutProfile): Int {
        val rowHeight = DrawerListGeometry.rowPitch(GlyphStyle.APP_LABEL_16.cellHeight)
        val top = LauncherHeaderLayout.firstContentItemTop(profile)
        val rail = (profile.logicalHeight - top).coerceAtLeast(rowHeight)
        return (rail / rowHeight).coerceAtLeast(1)
    }

    @Test
    fun visibleRows_matchesSharedGeometryComposition() {
        for (height in listOf(120, 240, 360, 480, 640)) {
            val profile = LauncherLayoutProfile(logicalWidth = 120, logicalHeight = height, dotSizePx = 4)
            assertEquals(
                "visibleRows mismatch at height=$height",
                expectedVisibleRows(profile),
                AppListLayout.visibleRows(profile),
            )
        }
    }

    @Test
    fun visibleRows_isAtLeastOneForTinyScreens() {
        assertEquals(1, AppListLayout.visibleRows(LauncherLayoutProfile(64, 1, 4)))
    }

    @Test
    fun visibleRows_growsWithTallerScreen() {
        val short = AppListLayout.visibleRows(LauncherLayoutProfile(120, 200, 4))
        val tall = AppListLayout.visibleRows(LauncherLayoutProfile(120, 800, 4))
        assertTrue("a 600px taller viewport must show strictly more rows", tall > short)
    }

    @Test
    fun visibleRows_usesStatusBarHeightFromProfile() {
        val defaultHeader = AppListLayout.visibleRows(LauncherLayoutProfile(120, 240, 4))
        val tallerHeader = AppListLayout.visibleRows(
            LauncherLayoutProfile(
                logicalWidth = 120,
                logicalHeight = 240,
                dotSizePx = 4,
                statusBarHeight = 48,
            ),
        )

        assertTrue("a taller status bar must reduce visible drawer rows", tallerHeader < defaultHeader)
    }
}
