package com.purride.pixellauncherv2.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenUsageSummaryModelTest {

    @Test
    fun summarizeCountsInteractiveSessionsAndDuration() {
        val snapshot = ScreenUsageSummaryModel.summarize(
            events = listOf(
                ScreenUsageEvent(ScreenUsageEventType.INTERACTIVE, timestampMillis = 0L),
                ScreenUsageEvent(ScreenUsageEventType.NON_INTERACTIVE, timestampMillis = 10 * 60_000L),
                ScreenUsageEvent(ScreenUsageEventType.INTERACTIVE, timestampMillis = 20 * 60_000L),
                ScreenUsageEvent(ScreenUsageEventType.NON_INTERACTIVE, timestampMillis = 25 * 60_000L),
            ),
            nowMillis = 30 * 60_000L,
        )

        assertEquals("00:15", snapshot.usageTimeText)
        assertEquals("2", snapshot.openCountText)
    }

    @Test
    fun summarizeIncludesStillInteractiveSessionUntilNow() {
        val snapshot = ScreenUsageSummaryModel.summarize(
            events = listOf(
                ScreenUsageEvent(ScreenUsageEventType.INTERACTIVE, timestampMillis = 5 * 60_000L),
            ),
            nowMillis = 35 * 60_000L,
        )

        assertEquals("00:30", snapshot.usageTimeText)
        assertEquals("1", snapshot.openCountText)
    }

    @Test
    fun summarizeSortsEventsBeforeCalculating() {
        val snapshot = ScreenUsageSummaryModel.summarize(
            events = listOf(
                ScreenUsageEvent(ScreenUsageEventType.NON_INTERACTIVE, timestampMillis = 12 * 60_000L),
                ScreenUsageEvent(ScreenUsageEventType.INTERACTIVE, timestampMillis = 2 * 60_000L),
            ),
            nowMillis = 20 * 60_000L,
        )

        assertEquals("00:10", snapshot.usageTimeText)
        assertEquals("1", snapshot.openCountText)
    }
}
