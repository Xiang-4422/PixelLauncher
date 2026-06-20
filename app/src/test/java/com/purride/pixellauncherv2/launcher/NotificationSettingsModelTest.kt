package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationSettingsModelTest {

    @Test
    fun rowsMergeActiveConfiguredMutedAndPrioritySources() {
        val rows = NotificationSettingsModel.rows(
            sources = listOf(
                NotificationSourceInfo(sourceId = "com.bank", sourceLabel = "BANK"),
                NotificationSourceInfo(sourceId = "com.chat", sourceLabel = "CHAT"),
            ),
            mutedSourceIds = setOf("com.noisy"),
            prioritySourceIds = setOf("com.bank"),
        )

        assertEquals(
            listOf(
                NotificationSettingsRow("com.bank", "BANK", NotificationSourceMode.PRIORITY),
                NotificationSettingsRow("com.chat", "CHAT", NotificationSourceMode.NORMAL),
                NotificationSettingsRow("com.noisy", "NOISY", NotificationSourceMode.MUTED),
            ),
            rows,
        )
    }

    @Test
    fun mutedModeWinsWhenASourceIsInBothSets() {
        val rows = NotificationSettingsModel.rows(
            sources = listOf(NotificationSourceInfo(sourceId = "com.bank", sourceLabel = "BANK")),
            mutedSourceIds = setOf("com.bank"),
            prioritySourceIds = setOf("com.bank"),
        )

        assertEquals(NotificationSourceMode.MUTED, rows.single().mode)
    }

    @Test
    fun nextModeCyclesNormalPriorityMuted() {
        assertEquals(NotificationSourceMode.PRIORITY, NotificationSettingsModel.nextMode(NotificationSourceMode.NORMAL))
        assertEquals(NotificationSourceMode.MUTED, NotificationSettingsModel.nextMode(NotificationSourceMode.PRIORITY))
        assertEquals(NotificationSourceMode.NORMAL, NotificationSettingsModel.nextMode(NotificationSourceMode.MUTED))
    }

    @Test
    fun summaryShowsConfiguredCountsOnly() {
        assertEquals("DEFAULT", NotificationSettingsModel.summary(emptySet(), emptySet()))
        assertEquals("M2 P1", NotificationSettingsModel.summary(setOf("a", "b"), setOf("c")))
    }
}
