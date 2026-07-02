package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationSummaryModelTest {

    @Test
    fun summarizeKeepsActiveNonMutedSignalsForHomeItems() {
        val summary = NotificationSummaryModel.summarize(
            signals = listOf(
                NotificationSignal(
                    sourceId = "chat",
                    sourceLabel = "CHAT",
                    title = "PING",
                    priority = NotificationSignalPriority.DEFAULT,
                    postedAtMillis = 10L,
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
                    postedAtMillis = 30L,
                ),
                NotificationSignal(
                    sourceId = "service",
                    sourceLabel = "SERVICE",
                    title = "RUNNING",
                    priority = NotificationSignalPriority.HIGH,
                    isOngoing = true,
                    postedAtMillis = 20L,
                ),
            ),
            rules = NotificationSummaryRules(mutedSourceIds = setOf("muted")),
        )

        assertEquals(3, summary.count)
        assertEquals("SILENT HIGH  SERVICE RUNNING +1", summary.text)
        assertEquals(listOf("silent", "service", "chat"), summary.items.map { it.sourceId })
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
        assertEquals(listOf("calendar", "bank"), summary.items.map { it.sourceId })
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
        assertEquals(emptyList<NotificationSignal>(), summary.items)
        assertEquals(listOf(NotificationSourceInfo(sourceId = "muted", sourceLabel = "MUTED")), summary.sources)
    }

    @Test
    fun summarizeDropsMediaControlsFromHomeItemsButKeepsSourceVisibleForSettings() {
        val summary = NotificationSummaryModel.summarize(
            signals = listOf(
                NotificationSignal(
                    sourceId = "music",
                    sourceLabel = "MUSIC",
                    title = "TRACK",
                    category = "transport",
                    isMediaStyle = true,
                    priority = NotificationSignalPriority.HIGH,
                ),
                NotificationSignal(
                    sourceId = "chat",
                    sourceLabel = "CHAT",
                    title = "PING",
                    priority = NotificationSignalPriority.DEFAULT,
                ),
            ),
        )

        assertEquals(1, summary.count)
        assertEquals("CHAT PING", summary.text)
        assertEquals(listOf("chat"), summary.items.map { it.sourceId })
        assertEquals(
            listOf(
                NotificationSourceInfo(sourceId = "chat", sourceLabel = "CHAT"),
                NotificationSourceInfo(sourceId = "music", sourceLabel = "MUSIC"),
            ),
            summary.sources,
        )
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
        assertEquals(listOf("c", "b", "a"), summary.items.map { it.sourceId })
    }
}
