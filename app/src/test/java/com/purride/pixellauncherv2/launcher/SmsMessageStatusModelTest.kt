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

    private companion object {
        /** android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX = 1 */
        const val TYPE_INBOX = 1

        /** android.provider.Telephony.Sms.MESSAGE_TYPE_SENT = 2 */
        const val TYPE_SENT = 2

        const val TYPE_UNKNOWN = -1
    }
}
