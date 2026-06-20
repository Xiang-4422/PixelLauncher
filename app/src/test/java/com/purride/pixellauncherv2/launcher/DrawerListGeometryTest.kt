package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the single source of drawer row geometry shared by the engine renderer
 * (DrawerScreen) and the state machine's visibleRows calculation (AppListLayout).
 * If these drift apart the state machine mis-counts visible rows and selection /
 * scroll windows desync from what is rendered.
 */
class DrawerListGeometryTest {

    @Test
    fun rowExtent_usesNaturalFontHeight() {
        assertEquals(10, DrawerListGeometry.rowExtent(10))
        assertEquals(12, DrawerListGeometry.rowExtent(12))
    }

    @Test
    fun rowPitch_isExtentPlusSpacing() {
        assertEquals(12, DrawerListGeometry.rowPitch(10))
        assertEquals(14, DrawerListGeometry.rowPitch(12))
        // pitch must always exceed the rendered item extent by exactly the spacing
        assertEquals(
            DrawerListGeometry.rowExtent(10) + DrawerListGeometry.ROW_SPACING_PX,
            DrawerListGeometry.rowPitch(10),
        )
    }
}
