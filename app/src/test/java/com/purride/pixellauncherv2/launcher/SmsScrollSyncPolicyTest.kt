package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.model.SmsMessageEntry
import com.purride.pixellauncherv2.viewmodel.LauncherUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsScrollSyncPolicyTest {

    @Test
    fun threadListPositionOnlySyncsOnEntryOrSelectionChange() {
        val threads = LauncherUiState(
            mode = LauncherMode.SMS_THREADS,
            smsPageIndex = SmsPageIndex.ALL,
            smsThreadSelectedIndex = 1,
        )

        assertTrue(SmsScrollSyncPolicy.shouldRevealSelectedThread(LauncherUiState(), threads))
        assertFalse(SmsScrollSyncPolicy.shouldRevealSelectedThread(threads, threads.copy()))
        assertTrue(
            SmsScrollSyncPolicy.shouldRevealSelectedThread(
                threads,
                threads.copy(smsThreadSelectedIndex = 2),
            ),
        )
    }

    @Test
    fun threadListPositionDoesNotSyncOnUnreadPage() {
        val unreadPage = LauncherUiState(
            mode = LauncherMode.SMS_THREADS,
            smsPageIndex = SmsPageIndex.UNREAD,
            smsThreadSelectedIndex = 1,
        )

        assertFalse(SmsScrollSyncPolicy.shouldRevealSelectedThread(LauncherUiState(), unreadPage))
    }

    @Test
    fun unreadListPositionOnlySyncsOnEntryOrSelectionChange() {
        val unread = LauncherUiState(
            mode = LauncherMode.SMS_THREADS,
            smsPageIndex = SmsPageIndex.UNREAD,
            smsSelectedIndex = 1,
        )

        assertTrue(SmsScrollSyncPolicy.shouldRevealSelectedUnread(LauncherUiState(), unread))
        assertFalse(SmsScrollSyncPolicy.shouldRevealSelectedUnread(unread, unread.copy()))
        assertTrue(
            SmsScrollSyncPolicy.shouldRevealSelectedUnread(
                unread,
                unread.copy(smsSelectedIndex = 2),
            ),
        )
    }

    @Test
    fun ordinaryDetailRedrawNeverChangesMessagePosition() {
        val detail = detailState(messages = listOf(message(1L)))

        assertFalse(
            SmsScrollSyncPolicy.shouldFollowMessagesToEnd(
                previous = detail,
                current = detail.copy(),
                wasAtEnd = false,
            ),
        )
    }

    @Test
    fun newMessagesOnlyFollowWhenUserWasAlreadyAtEnd() {
        val previous = detailState(messages = listOf(message(1L)))
        val current = detailState(messages = listOf(message(1L), message(2L)))

        assertTrue(SmsScrollSyncPolicy.shouldFollowMessagesToEnd(previous, current, wasAtEnd = true))
        assertFalse(SmsScrollSyncPolicy.shouldFollowMessagesToEnd(previous, current, wasAtEnd = false))
    }

    @Test
    fun enteringOrSwitchingThreadAlwaysRequestsInitialEndPosition() {
        val detail = detailState(messages = emptyList())
        assertTrue(
            SmsScrollSyncPolicy.shouldFollowMessagesToEnd(
                previous = LauncherUiState(mode = LauncherMode.SMS_THREADS),
                current = detail,
                wasAtEnd = false,
            ),
        )
        assertTrue(
            SmsScrollSyncPolicy.shouldFollowMessagesToEnd(
                previous = detail.copy(smsCurrentThreadId = 1L),
                current = detail.copy(smsCurrentThreadId = 2L),
                wasAtEnd = false,
            ),
        )
    }

    private fun detailState(messages: List<SmsMessageEntry>): LauncherUiState = LauncherUiState(
        mode = LauncherMode.SMS_THREAD_DETAIL,
        smsCurrentThreadId = 1L,
        smsCurrentAddress = "10086",
        smsMessages = messages,
    )

    private fun message(id: Long): SmsMessageEntry = SmsMessageEntry(
        messageId = id,
        threadId = 1L,
        address = "10086",
        body = "MSG $id",
        dateMillis = id,
        type = 1,
        isRead = true,
    )
}
