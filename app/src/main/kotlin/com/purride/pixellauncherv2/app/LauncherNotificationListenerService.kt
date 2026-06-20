package com.purride.pixellauncherv2.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.purride.pixellauncherv2.data.NotificationSummaryStore
import com.purride.pixellauncherv2.launcher.NotificationSignal
import com.purride.pixellauncherv2.launcher.NotificationSignalPriority
import com.purride.pixellauncherv2.launcher.NotificationSummary
import com.purride.pixellauncherv2.launcher.NotificationSummaryModel
import com.purride.pixellauncherv2.launcher.NotificationSummaryRules
import java.util.Locale

@Suppress("DEPRECATION")
class LauncherNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        publishActiveNotificationSummary()
    }

    override fun onListenerDisconnected() {
        NotificationSummaryStore.update(NotificationSummary(count = 0, text = ""))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        publishActiveNotificationSummary()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        publishActiveNotificationSummary()
    }

    private fun publishActiveNotificationSummary() {
        val notifications = runCatching {
            activeNotifications.orEmpty()
        }.getOrElse {
            emptyArray<StatusBarNotification>()
        }
        val summary = NotificationSummaryModel.summarize(
            signals = notifications
                .mapNotNull(::toSignal),
            rules = NotificationSummaryRules(mutedSourceIds = setOf(packageName)),
        )
        NotificationSummaryStore.update(summary)
    }

    private fun toSignal(statusBarNotification: StatusBarNotification): NotificationSignal? {
        val sourceId = statusBarNotification.packageName?.trim().orEmpty()
        if (sourceId.isEmpty()) return null
        val notification = statusBarNotification.notification ?: return null
        return NotificationSignal(
            sourceId = sourceId,
            sourceLabel = appLabel(sourceId),
            title = notificationTitle(notification),
            priority = notificationPriority(notification),
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            isSilent = notification.priority <= Notification.PRIORITY_MIN,
            postedAtMillis = statusBarNotification.postTime,
        )
    }

    private fun appLabel(packageName: String): String {
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrElse { packageName.substringAfterLast('.').uppercase(Locale.US) }
    }

    private fun notificationTitle(notification: Notification): String {
        val title = notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        if (title.isNotBlank()) return title
        return notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
    }

    private fun notificationPriority(notification: Notification): NotificationSignalPriority {
        return when {
            notification.priority >= Notification.PRIORITY_HIGH -> NotificationSignalPriority.HIGH
            notification.priority <= Notification.PRIORITY_LOW -> NotificationSignalPriority.LOW
            else -> NotificationSignalPriority.DEFAULT
        }
    }
}
