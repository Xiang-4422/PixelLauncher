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
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.purride.pixellauncherv2.R
import com.purride.pixellauncherv2.app.MainActivity
import com.purride.pixellauncherv2.app.SmsNotificationActionReceiver
import com.purride.pixellauncherv2.model.SmsMessageEntry

class SmsNotificationHelper(
    private val context: Context,
) {

    /**
     * 来信通知。[recentUnread] 为该线程最近的未读消息（时间升序），
     * 用 MessagingStyle 堆叠显示，后一条不再顶掉前一条的内容。
     */
    fun showIncomingMessage(entry: SmsMessageEntry, recentUnread: List<SmsMessageEntry> = emptyList()) {
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
        val title = entry.conversationTitle.ifBlank { entry.address }.ifBlank { "SMS" }
        val sender = Person.Builder().setName(title).build()
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("ME").build())
        val stacked = recentUnread.ifEmpty { listOf(entry) }.takeLast(MAX_STACKED_MESSAGES)
        stacked.forEach { message ->
            style.addMessage(message.body.ifBlank { "(EMPTY)" }, message.dateMillis, sender)
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_sms)
            .setContentTitle(title)
            .setContentText(entry.body.ifBlank { "(EMPTY)" })
            .setStyle(style)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_SMS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (entry.threadId > 0L) {
            // 服务号会话只读，不提供回复入口；个人会话支持通知栏直接回复。
            if (!entry.isServiceConversation && entry.address.isNotBlank()) {
                builder.addAction(buildReplyAction(entry))
            }
            builder.addAction(buildMarkReadAction(entry))
        }
        if (!canPostNotifications()) {
            return
        }
        NotificationManagerCompat.from(context).notify(entry.threadId.toInt(), builder.build())
        showGroupSummary()
    }

    /** 多会话通知的分组摘要；点按打开应用（由宿主决定落点）。 */
    private fun showGroupSummary() {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            GROUP_SUMMARY_NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val summary = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_sms)
            .setContentTitle("SMS")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_SMS)
            .setGroupSummary(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(GROUP_SUMMARY_NOTIFICATION_ID, summary)
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
        cancelSummaryIfAlone()
    }

    /** 组内最后一个会话通知被撤下后，孤儿摘要也要一并清掉。 */
    private fun cancelSummaryIfAlone() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val active = runCatching { manager.activeNotifications }.getOrNull() ?: return
        val hasThreadNotification = active.any {
            it.notification.group == GROUP_KEY_SMS && it.id != GROUP_SUMMARY_NOTIFICATION_ID
        }
        if (!hasThreadNotification) {
            NotificationManagerCompat.from(context).cancel(GROUP_SUMMARY_NOTIFICATION_ID)
        }
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
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

    /** 通知栏直接回复：RemoteInput 要求 PendingIntent 可变（S+ 必须显式 FLAG_MUTABLE）。 */
    private fun buildReplyAction(entry: SmsMessageEntry): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(SmsNotificationActionReceiver.KEY_REPLY_TEXT)
            .setLabel("REPLY")
            .build()
        val intent = Intent(context, SmsNotificationActionReceiver::class.java)
            .setAction(SmsNotificationActionReceiver.ACTION_REPLY)
            .putExtra(SmsNotificationActionReceiver.EXTRA_THREAD_ID, entry.threadId)
            .putExtra(SmsNotificationActionReceiver.EXTRA_ADDRESS, entry.address)
        val mutabilityFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entry.threadId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_sms, "REPLY", pendingIntent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()
    }

    private fun buildMarkReadAction(entry: SmsMessageEntry): NotificationCompat.Action {
        val intent = Intent(context, SmsNotificationActionReceiver::class.java)
            .setAction(SmsNotificationActionReceiver.ACTION_MARK_READ)
            .putExtra(SmsNotificationActionReceiver.EXTRA_THREAD_ID, entry.threadId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entry.threadId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_sms, "READ", pendingIntent)
            .build()
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
        const val GROUP_KEY_SMS = "sms_incoming_group"
        const val GROUP_SUMMARY_NOTIFICATION_ID = 7999
        const val MAX_STACKED_MESSAGES = 5
    }
}
