package com.purride.pixellauncherv2.data

import com.purride.pixellauncherv2.launcher.NotificationSummary
import com.purride.pixellauncherv2.launcher.NotificationSignal
import com.purride.pixellauncherv2.launcher.NotificationSignalPriority
import com.purride.pixellauncherv2.launcher.NotificationSummaryRules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NotificationSummaryRepositoryTest {

    @Before
    fun setUp() {
        NotificationSummaryStore.resetForTests()
    }

    @After
    fun tearDown() {
        NotificationSummaryStore.resetForTests()
    }

    @Test
    fun startEmitsCurrentSummaryAndReceivesUpdatesUntilStopped() {
        val repository = NotificationSummaryRepository()
        val received = mutableListOf<NotificationSummary>()

        NotificationSummaryStore.update(NotificationSummary(count = 1, text = "BANK OTP"))
        repository.start { summary -> received += summary }
        NotificationSummaryStore.update(NotificationSummary(count = 2, text = "CAL MEET  BANK OTP"))
        repository.stop()
        NotificationSummaryStore.update(NotificationSummary(count = 3, text = "SHOULD NOT ARRIVE"))

        assertEquals(
            listOf(
                NotificationSummary(count = 1, text = "BANK OTP"),
                NotificationSummary(count = 2, text = "CAL MEET  BANK OTP"),
            ),
            received,
        )
    }

    @Test
    fun updateRulesRebuildsCurrentSignalsImmediately() {
        val repository = NotificationSummaryRepository()
        val received = mutableListOf<NotificationSummary>()
        repository.start { summary -> received += summary }

        NotificationSummaryStore.updateSignals(
            nextSignals = listOf(
                NotificationSignal(
                    sourceId = "bank",
                    sourceLabel = "BANK",
                    title = "OTP",
                    priority = NotificationSignalPriority.HIGH,
                ),
            ),
            nextRules = NotificationSummaryRules(),
        )
        NotificationSummaryStore.updateRules(NotificationSummaryRules(allowedSourceIds = setOf("bank")))

        assertEquals(
            NotificationSummary(
                count = 0,
                text = "",
                sources = received[1].sources,
            ),
            received[1],
        )
        assertEquals(1, received[2].count)
        assertEquals("BANK OTP", received[2].text)
    }
}
