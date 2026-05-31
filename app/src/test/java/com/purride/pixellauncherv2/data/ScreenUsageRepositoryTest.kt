package com.purride.pixellauncherv2.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for the pure duration formatting in [ScreenUsageRepository]. The
 * UsageStats query path needs Android framework services and is not unit-tested
 * here; `formatDurationText` is a pure companion helper.
 */
class ScreenUsageRepositoryTest {

    @Test
    fun formatDurationText_rendersClockStyleHoursAndMinutes() {
        assertEquals("00:00", ScreenUsageRepository.formatDurationText(0L))
        assertEquals("00:01", ScreenUsageRepository.formatDurationText(60_000L))
        assertEquals("01:30", ScreenUsageRepository.formatDurationText(90L * 60_000L))
        assertEquals("02:05", ScreenUsageRepository.formatDurationText(125L * 60_000L))
    }

    @Test
    fun formatDurationText_floorsSubMinuteAndClampsNegative() {
        assertEquals("00:00", ScreenUsageRepository.formatDurationText(59_999L))
        assertEquals("00:00", ScreenUsageRepository.formatDurationText(-5_000L))
    }

    @Test
    fun formatDurationText_doesNotCapHoursAtTwentyFour() {
        assertEquals("25:00", ScreenUsageRepository.formatDurationText(25L * 60L * 60_000L))
    }

    @Test
    fun noAccessSnapshot_usesPlaceholders() {
        assertEquals("--:--", ScreenUsageRepository.noAccessSnapshot.usageTimeText)
        assertEquals("--", ScreenUsageRepository.noAccessSnapshot.openCountText)
    }
}
