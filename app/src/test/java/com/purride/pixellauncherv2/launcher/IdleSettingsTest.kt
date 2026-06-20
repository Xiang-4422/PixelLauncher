package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class IdleSettingsTest {

    @Test
    fun timeoutOptionsExposeDefaultThirtySeconds() {
        assertEquals(30, IdleSettings.DEFAULT_TIMEOUT_SECONDS)
        assertEquals(listOf(15, 30, 60, 120), IdleSettings.timeoutOptionsSeconds)
    }

    @Test
    fun normalizeTimeoutSecondsSnapsToNearestAllowedOption() {
        assertEquals(15, IdleSettings.normalizeTimeoutSeconds(1))
        assertEquals(30, IdleSettings.normalizeTimeoutSeconds(29))
        assertEquals(30, IdleSettings.normalizeTimeoutSeconds(45))
        assertEquals(120, IdleSettings.normalizeTimeoutSeconds(200))
    }

    @Test
    fun nextTimeoutSecondsCyclesThroughOptions() {
        assertEquals(60, IdleSettings.nextTimeoutSeconds(current = 30, direction = 1))
        assertEquals(15, IdleSettings.nextTimeoutSeconds(current = 120, direction = 1))
        assertEquals(120, IdleSettings.nextTimeoutSeconds(current = 15, direction = -1))
    }

    @Test
    fun timeoutLabelUsesNormalizedValue() {
        assertEquals("30S", IdleSettings.timeoutLabel(29))
        assertEquals("120S", IdleSettings.timeoutLabel(200))
    }
}
