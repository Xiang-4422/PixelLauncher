package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsMessageStatusModelTest {

    @Test
    fun sentTypeShowsSentStatus() {
        assertTrue(SmsMessageStatusModel.isSent(TYPE_SENT))
    }

    @Test
    fun inboxAndUnknownTypesShowIncomingStatus() {
        assertFalse(SmsMessageStatusModel.isSent(TYPE_INBOX))
        assertFalse(SmsMessageStatusModel.isSent(TYPE_UNKNOWN))
    }

    @Test
    fun outgoingCoversSentPendingAndFailed() {
        assertTrue(SmsMessageStatusModel.isOutgoing(TYPE_SENT))
        assertTrue(SmsMessageStatusModel.isOutgoing(TYPE_OUTBOX))
        assertTrue(SmsMessageStatusModel.isOutgoing(TYPE_QUEUED))
        assertTrue(SmsMessageStatusModel.isOutgoing(TYPE_FAILED))
        assertFalse(SmsMessageStatusModel.isOutgoing(TYPE_INBOX))
        assertFalse(SmsMessageStatusModel.isOutgoing(TYPE_UNKNOWN))
    }

    @Test
    fun pendingCoversOutboxAndQueuedOnly() {
        assertTrue(SmsMessageStatusModel.isPending(TYPE_OUTBOX))
        assertTrue(SmsMessageStatusModel.isPending(TYPE_QUEUED))
        assertFalse(SmsMessageStatusModel.isPending(TYPE_SENT))
        assertFalse(SmsMessageStatusModel.isPending(TYPE_FAILED))
    }

    @Test
    fun conversationTypesExcludeDraftAndUnknown() {
        assertTrue(SmsMessageStatusModel.conversationTypes.contains(TYPE_INBOX))
        assertTrue(SmsMessageStatusModel.conversationTypes.contains(TYPE_SENT))
        assertTrue(SmsMessageStatusModel.conversationTypes.contains(TYPE_OUTBOX))
        assertTrue(SmsMessageStatusModel.conversationTypes.contains(TYPE_FAILED))
        assertTrue(SmsMessageStatusModel.conversationTypes.contains(TYPE_QUEUED))
        assertFalse(SmsMessageStatusModel.conversationTypes.contains(TYPE_DRAFT))
        assertFalse(SmsMessageStatusModel.conversationTypes.contains(TYPE_UNKNOWN))
    }

    @Test
    fun failedCoversFailedTypeOnly() {
        assertTrue(SmsMessageStatusModel.isFailed(TYPE_FAILED))
        assertFalse(SmsMessageStatusModel.isFailed(TYPE_SENT))
        assertFalse(SmsMessageStatusModel.isFailed(TYPE_OUTBOX))
        assertFalse(SmsMessageStatusModel.isFailed(TYPE_INBOX))
    }

    private companion object {
        /** android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX = 1 */
        const val TYPE_INBOX = 1

        /** android.provider.Telephony.Sms.MESSAGE_TYPE_SENT = 2 */
        const val TYPE_SENT = 2

        /** android.provider.Telephony.Sms.MESSAGE_TYPE_DRAFT = 3 */
        const val TYPE_DRAFT = 3

        /** android.provider.Telephony.Sms.MESSAGE_TYPE_OUTBOX = 4 */
        const val TYPE_OUTBOX = 4

        /** android.provider.Telephony.Sms.MESSAGE_TYPE_FAILED = 5 */
        const val TYPE_FAILED = 5

        /** android.provider.Telephony.Sms.MESSAGE_TYPE_QUEUED = 6 */
        const val TYPE_QUEUED = 6

        const val TYPE_UNKNOWN = -1
    }
}
