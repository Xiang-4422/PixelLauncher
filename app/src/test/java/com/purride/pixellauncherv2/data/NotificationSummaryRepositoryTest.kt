package com.purride.pixellauncherv2.data

import com.purride.pixellauncherv2.launcher.NotificationSummary
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
}
