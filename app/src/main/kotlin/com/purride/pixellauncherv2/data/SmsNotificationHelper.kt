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

    /**
     * 彩信到达提示。[senderTitle] 为发件人（联系人名或号码），可为空。
     * 通知 id 按发件人区分，多个发件人的彩信提示不互相覆盖。
     */
    fun showUnsupportedMms(senderTitle: String = "") {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_sms)
            .setContentTitle(if (senderTitle.isBlank()) "MMS RECEIVED" else "MMS FROM $senderTitle")
            .setContentText("THIS LAUNCHER DOES NOT SUPPORT MMS")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context)
            .notify(UNSUPPORTED_MMS_TAG, senderTitle.hashCode(), notification)
    }

    /** 会话被打开或标记已读后，撤下它挂着的通知（通知 id 与 threadId 一一对应）。 */
    fun cancelForThread(threadId: Long) {
        NotificationManagerCompat.from(context).cancel(threadId.toInt())
    }

    /** 快捷回复（通话中“以短信回复”）发送失败时提示；点按进入对应会话补发。 */
    fun showSendFailure(address: String) {
        ensureChannel()
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_OPEN_SMS_ADDRESS, address)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            address.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_sms)
            .setContentTitle("SEND FAILED")
            .setContentText(address.ifBlank { "SMS" })
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // 用独立 tag 划分命名空间，避免与按 threadId 编号的来信通知互相覆盖/误撤。
        NotificationManagerCompat.from(context)
            .notify(SEND_FAILURE_TAG, address.hashCode(), notification)
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
        const val SEND_FAILURE_TAG = "sms_send_failure"
        const val UNSUPPORTED_MMS_TAG = "mms_unsupported"
    }
}
