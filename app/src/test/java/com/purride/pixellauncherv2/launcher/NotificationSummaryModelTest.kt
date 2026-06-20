package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationSummaryModelTest {

    @Test
    fun summarizeDropsSilentMutedAndNormalPrioritySignals() {
        val summary = NotificationSummaryModel.summarize(
            signals = listOf(
                NotificationSignal(
                    sourceId = "chat",
                    sourceLabel = "CHAT",
                    title = "PING",
                    priority = NotificationSignalPriority.DEFAULT,
                ),
                NotificationSignal(
                    sourceId = "muted",
                    sourceLabel = "MUTED",
                    title = "HIGH",
                    priority = NotificationSignalPriority.HIGH,
                ),
                NotificationSignal(
                    sourceId = "silent",
                    sourceLabel = "SILENT",
                    title = "HIGH",
                    priority = NotificationSignalPriority.HIGH,
                    isSilent = true,
                ),
                NotificationSignal(
                    sourceId = "service",
                    sourceLabel = "SERVICE",
                    title = "RUNNING",
                    priority = NotificationSignalPriority.HIGH,
                    isOngoing = true,
                ),
            ),
            rules = NotificationSummaryRules(mutedSourceIds = setOf("muted")),
        )

        assertEquals(0, summary.count)
        assertEquals("", summary.text)
        assertEquals(4, summary.sources.size)
    }

    @Test
    fun summarizeKeepsHighPriorityAndConfiguredPrioritySources() {
        val summary = NotificationSummaryModel.summarize(
            signals = listOf(
                NotificationSignal(
                    sourceId = "bank",
                    sourceLabel = "BANK",
                    title = "OTP",
                    priority = NotificationSignalPriority.HIGH,
                    postedAtMillis = 10L,
                ),
                NotificationSignal(
                    sourceId = "calendar",
                    sourceLabel = "CAL",
                    title = "MEET",
                    priority = NotificationSignalPriority.DEFAULT,
                    postedAtMillis = 20L,
                ),
            ),
            rules = NotificationSummaryRules(prioritySourceIds = setOf("calendar")),
        )

        assertEquals(2, summary.count)
        assertEquals("CAL MEET  BANK OTP", summary.text)
        assertEquals(
            listOf(
                NotificationSourceInfo(sourceId = "bank", sourceLabel = "BANK"),
                NotificationSourceInfo(sourceId = "calendar", sourceLabel = "CAL"),
            ),
            summary.sources,
        )
    }

    @Test
    fun summarizeKeepsMutedSourcesVisibleForSettings() {
        val summary = NotificationSummaryModel.summarize(
            signals = listOf(
                NotificationSignal(
                    sourceId = "muted",
                    sourceLabel = "MUTED",
                    priority = NotificationSignalPriority.HIGH,
                ),
            ),
            rules = NotificationSummaryRules(mutedSourceIds = setOf("muted")),
        )

        assertEquals(0, summary.count)
        assertEquals("", summary.text)
        assertEquals(listOf(NotificationSourceInfo(sourceId = "muted", sourceLabel = "MUTED")), summary.sources)
    }

    @Test
    fun summarizeCapsVisibleItemsAndShowsHiddenCount() {
        val summary = NotificationSummaryModel.summarize(
            signals = listOf(
                NotificationSignal("a", "A", priority = NotificationSignalPriority.HIGH, postedAtMillis = 1L),
                NotificationSignal("b", "B", priority = NotificationSignalPriority.HIGH, postedAtMillis = 2L),
                NotificationSignal("c", "C", priority = NotificationSignalPriority.HIGH, postedAtMillis = 3L),
            ),
            rules = NotificationSummaryRules(maxItems = 2),
        )

        assertEquals(3, summary.count)
        assertEquals("C  B +1", summary.text)
    }
}
