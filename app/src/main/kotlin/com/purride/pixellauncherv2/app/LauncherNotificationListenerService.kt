package com.purride.pixellauncherv2.app

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.purride.pixellauncherv2.data.NotificationCommand
import com.purride.pixellauncherv2.data.NotificationCommandStore
import com.purride.pixellauncherv2.data.NotificationSummarySettingsRepository
import com.purride.pixellauncherv2.data.NotificationSummaryStore
import com.purride.pixellauncherv2.launcher.NotificationActionInfo
import com.purride.pixellauncherv2.launcher.NotificationProgressInfo
import com.purride.pixellauncherv2.launcher.NotificationSignal
import com.purride.pixellauncherv2.launcher.NotificationSignalPriority
import com.purride.pixellauncherv2.launcher.NotificationSummary
import java.util.Locale

@Suppress("DEPRECATION")
class LauncherNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        publishActiveNotificationSummary()
    }

    override fun onListenerDisconnected() {
        NotificationCommandStore.clear()
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
        val signals = notifications
            .mapNotNull(::toSignal)
            .filterNot { signal -> signal.sourceId == packageName }
        val commands = notifications
            .mapNotNull(::toCommand)
            .filterNot { command -> command.sourceId == packageName }
        NotificationCommandStore.update(commands)
        val rules = NotificationSummarySettingsRepository(this).rules()
        NotificationSummaryStore.updateSignals(signals, rules)
    }

    private fun toSignal(statusBarNotification: StatusBarNotification): NotificationSignal? {
        val sourceId = statusBarNotification.packageName?.trim().orEmpty()
        if (sourceId.isEmpty()) return null
        val notification = statusBarNotification.notification ?: return null
        return NotificationSignal(
            sourceId = sourceId,
            sourceLabel = appLabel(sourceId),
            key = statusBarNotification.key.orEmpty(),
            title = notificationTitle(notification),
            text = notificationText(notification),
            subText = notificationSubText(notification),
            bigText = notificationBigText(notification),
            summaryText = notificationSummaryText(notification),
            textLines = notificationTextLines(notification),
            category = notification.category.orEmpty(),
            channelId = notificationChannelId(notification),
            isMediaStyle = notificationIsMediaStyle(notification),
            priority = notificationPriority(notification),
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            isSilent = notification.priority <= Notification.PRIORITY_MIN,
            isClearable = statusBarNotification.isClearable,
            progress = notificationProgress(notification),
            actions = notificationActions(notification),
            postedAtMillis = statusBarNotification.postTime,
        )
    }

    private fun toCommand(statusBarNotification: StatusBarNotification): NotificationCommand? {
        val sourceId = statusBarNotification.packageName?.trim().orEmpty()
        val key = statusBarNotification.key?.trim().orEmpty()
        if (sourceId.isEmpty() || key.isEmpty()) return null
        val notification = statusBarNotification.notification ?: return null
        return NotificationCommand(
            key = key,
            sourceId = sourceId,
            contentIntent = notification.contentIntent,
            actions = notification.actions.orEmpty().map { action -> action.actionIntent },
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

    private fun notificationText(notification: Notification): String {
        return notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
    }

    private fun notificationSubText(notification: Notification): String {
        return notification.extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
    }

    private fun notificationBigText(notification: Notification): String {
        return notification.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
    }

    private fun notificationSummaryText(notification: Notification): String {
        return notification.extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty()
    }

    private fun notificationTextLines(notification: Notification): List<String> {
        return notification.extras
            ?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            .orEmpty()
            .mapNotNull { value ->
                value?.toString()?.trim()?.takeIf(String::isNotEmpty)
            }
    }

    private fun notificationChannelId(notification: Notification): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification.channelId.orEmpty()
        } else {
            ""
        }
    }

    private fun notificationIsMediaStyle(notification: Notification): Boolean {
        val template = notification.extras
            ?.getString(Notification.EXTRA_TEMPLATE)
            .orEmpty()
        return notification.category == Notification.CATEGORY_TRANSPORT ||
            template.substringAfterLast('.') == "Notification\$MediaStyle" ||
            template.endsWith("\$MediaStyle")
    }

    private fun notificationProgress(notification: Notification): NotificationProgressInfo {
        val extras = notification.extras ?: return NotificationProgressInfo()
        return NotificationProgressInfo(
            max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0).coerceAtLeast(0),
            value = extras.getInt(Notification.EXTRA_PROGRESS, 0).coerceAtLeast(0),
            indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false),
        )
    }

    private fun notificationActions(notification: Notification): List<NotificationActionInfo> {
        return notification.actions.orEmpty().mapIndexedNotNull { index, action ->
            val title = action.title?.toString()?.trim().orEmpty()
            if (title.isEmpty()) {
                null
            } else {
                NotificationActionInfo(
                    index = index,
                    title = title,
                    requiresInput = !action.remoteInputs.isNullOrEmpty(),
                )
            }
        }
    }

    private fun notificationPriority(notification: Notification): NotificationSignalPriority {
        return when {
            notification.priority >= Notification.PRIORITY_HIGH -> NotificationSignalPriority.HIGH
            notification.priority <= Notification.PRIORITY_LOW -> NotificationSignalPriority.LOW
            else -> NotificationSignalPriority.DEFAULT
        }
    }
}
