package com.purride.pixellauncherv2.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.purride.pixellauncherv2.R
import com.purride.pixellauncherv2.app.MainActivity
import com.purride.pixellauncherv2.model.SmsMessageEntry

class SmsNotificationHelper(
    private val context: Context,
) {

    fun showIncomingMessage(entry: SmsMessageEntry) {
        ensureChannel()
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_OPEN_SMS_THREAD_ID, entry.threadId)
            putExtra(MainActivity.EXTRA_OPEN_SMS_ADDRESS, entry.address)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            entry.threadId.toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_sms)
            .setContentTitle(entry.conversationTitle.ifBlank { entry.address }.ifBlank { "SMS" })
            .setContentText(entry.body.ifBlank { "(EMPTY)" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(entry.body.ifBlank { "(EMPTY)" }))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(entry.threadId.toInt(), notification)
    }

    fun showUnsupportedMms() {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_sms)
            .setContentTitle("MMS RECEIVED")
            .setContentText("THIS LAUNCHER DOES NOT YET SUPPORT MMS")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(unsupportedMmsNotificationId, notification)
    }

    /** 会话被打开或标记已读后，撤下它挂着的通知（通知 id 与 threadId 一一对应）。 */
    fun cancelForThread(threadId: Long) {
        NotificationManagerCompat.from(context).cancel(threadId.toInt())
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = notificationManager.getNotificationChannel(channelId)
        if (existing != null) {
            return
        }
        notificationManager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "SMS",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Incoming SMS notifications"
            },
        )
    }

    private companion object {
        const val channelId = "sms_incoming"
        const val unsupportedMmsNotificationId = 8000
    }
}
