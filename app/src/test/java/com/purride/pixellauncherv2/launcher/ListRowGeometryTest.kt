package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the fixed settings / SMS-thread row geometry shared by the engine
 * renderers (SettingsScreen / SmsThreadsScreen) and the state machine's
 * visibleRows calculations (SettingsMenuLayout / SmsLayout). Drift here makes
 * the state machine mis-count visible rows vs what is rendered.
 */
class ListRowGeometryTest {

    @Test
    fun settingsRowPitch_isExtentPlusSpacing() {
        assertEquals(LauncherSpacing.ROW_SPACING, SettingsListGeometry.ROW_SPACING_PX)
        assertEquals(27, SettingsListGeometry.ROW_PITCH_PX)
        assertEquals(
            SettingsListGeometry.ROW_EXTENT_PX + SettingsListGeometry.ROW_SPACING_PX,
            SettingsListGeometry.ROW_PITCH_PX,
        )
    }

    @Test
    fun smsThreadRowPitch_isExtentPlusSpacing() {
        assertEquals(1, SmsThreadGeometry.ROW_SPACING_PX)
        assertEquals(26, SmsThreadGeometry.ROW_PITCH_PX)
        assertEquals(
            SmsThreadGeometry.ROW_EXTENT_PX + SmsThreadGeometry.ROW_SPACING_PX,
            SmsThreadGeometry.ROW_PITCH_PX,
        )
    }
}
