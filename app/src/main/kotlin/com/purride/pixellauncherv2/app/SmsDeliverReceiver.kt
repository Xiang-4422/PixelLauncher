package com.purride.pixellauncherv2.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.purride.pixellauncherv2.data.SmsNotificationHelper
import com.purride.pixellauncherv2.data.SmsRepository

class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repository = SmsRepository(context)
        val entry = repository.storeIncomingFromIntent(intent) ?: return
        SmsNotificationHelper(context).showIncomingMessage(entry)
        resultCode = Telephony.Sms.Intents.RESULT_SMS_HANDLED
    }
}
