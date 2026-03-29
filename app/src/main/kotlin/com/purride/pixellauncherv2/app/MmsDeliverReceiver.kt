package com.purride.pixellauncherv2.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.purride.pixellauncherv2.data.SmsNotificationHelper

class MmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        SmsNotificationHelper(context).showUnsupportedMms()
    }
}
