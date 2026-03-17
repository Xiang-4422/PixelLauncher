package com.purride.pixellauncherv2.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenUsageRepositoryTest {

    @Test
    fun formatDurationTextRoundsDownToWholeMinutes() {
        assertEquals("00:00", ScreenUsageRepository.formatDurationText(59_999L))
        assertEquals("00:01", ScreenUsageRepository.formatDurationText(60_000L))
        assertEquals("02:15", ScreenUsageRepository.formatDurationText((2 * 60L + 15L) * 60_000L))
    }
}
