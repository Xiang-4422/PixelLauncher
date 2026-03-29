package com.purride.pixellauncherv2.app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.purride.pixellauncherv2.data.SmsRepository
import com.purride.pixellauncherv2.data.SmsSendRequest

class RespondViaMessageService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.data?.schemeSpecificPart.orEmpty().substringBefore('?').trim()
        val body = intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent?.getStringExtra("sms_body")
            ?: ""
        if (address.isNotBlank() && body.isNotBlank()) {
            SmsRepository(applicationContext).sendMessage(
                SmsSendRequest(
                    address = address,
                    body = body,
                ),
            )
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
