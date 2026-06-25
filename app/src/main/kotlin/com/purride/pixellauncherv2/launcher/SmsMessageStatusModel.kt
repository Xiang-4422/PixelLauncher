package com.purride.pixellauncherv2.launcher

object SmsMessageStatusModel {

    fun isSent(type: Int): Boolean = type == TYPE_SENT

    /** android.provider.Telephony.Sms.MESSAGE_TYPE_SENT = 2 */
    private const val TYPE_SENT = 2
}
