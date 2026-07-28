package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSendRetryPolicyTest {

    @Test
    fun radioOffAndNoServiceAreTransient() {
        assertTrue(SmsSendRetryPolicy.isTransientError(RESULT_ERROR_RADIO_OFF))
        assertTrue(SmsSendRetryPolicy.isTransientError(RESULT_ERROR_NO_SERVICE))
    }

    @Test
    fun otherErrorsAreTerminal() {
        assertFalse(SmsSendRetryPolicy.isTransientError(RESULT_ERROR_GENERIC_FAILURE))
        assertFalse(SmsSendRetryPolicy.isTransientError(RESULT_ERROR_NULL_PDU))
        assertFalse(SmsSendRetryPolicy.isTransientError(0))
        assertFalse(SmsSendRetryPolicy.isTransientError(-1))
    }

    private companion object {
        /** android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE = 1 */
        const val RESULT_ERROR_GENERIC_FAILURE = 1

        /** android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF = 2 */
        const val RESULT_ERROR_RADIO_OFF = 2

        /** android.telephony.SmsManager.RESULT_ERROR_NULL_PDU = 3 */
        const val RESULT_ERROR_NULL_PDU = 3

        /** android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE = 4 */
        const val RESULT_ERROR_NO_SERVICE = 4
    }
}
