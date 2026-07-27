package com.purride.pixellauncherv2.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** 系统在收到短信时创建此接收器；仓库通过 [AndroidComponentDependencies] 边界统一构造。 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repository = AndroidComponentDependencies.smsRepository(context)
        val entry = repository.storeIncomingFromIntent(intent) ?: return
        AndroidComponentDependencies.smsNotificationHelper(context).showIncomingMessage(entry)
        resultCode = Telephony.Sms.Intents.RESULT_SMS_HANDLED
    }
}
