package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.viewmodel.LauncherUiState

enum class DataHealthItem {
    UPDATED,
    USAGE,
    LOCATION,
    CALL_LOG,
    SMS_READ,
    SMS_APP,
    SMS_SEND,
    NOTIFICATION_POST,
    NOTIFICATION_LISTENER,
}

data class DataHealthLine(
    val item: DataHealthItem,
    val title: String,
    val value: String,
    val reason: String = "",
)

object DataHealthModel {

    fun summary(state: LauncherState): String = summary(
        hasUsageAccess = state.hasUsageAccess,
        hasLocationPermission = state.hasLocationPermission,
        hasCallLogPermission = state.hasCallLogPermission,
        hasSmsReadPermission = state.hasSmsReadPermission,
        isDefaultSmsApp = state.isDefaultSmsApp,
        smsPermissionState = state.smsPermissionState,
        hasPostNotificationPermission = state.hasPostNotificationPermission,
        hasNotificationListenerAccess = state.hasNotificationListenerAccess,
    )

    fun summary(state: LauncherUiState): String = summary(
        hasUsageAccess = state.hasUsageAccess,
        hasLocationPermission = state.hasLocationPermission,
        hasCallLogPermission = state.hasCallLogPermission,
        hasSmsReadPermission = state.hasSmsReadPermission,
        isDefaultSmsApp = state.isDefaultSmsApp,
        smsPermissionState = state.smsPermissionState,
        hasPostNotificationPermission = state.hasPostNotificationPermission,
        hasNotificationListenerAccess = state.hasNotificationListenerAccess,
    )

    fun lines(state: LauncherState): List<DataHealthLine> {
        return lines(
            hasUsageAccess = state.hasUsageAccess,
            hasLocationPermission = state.hasLocationPermission,
            hasCallLogPermission = state.hasCallLogPermission,
            hasSmsReadPermission = state.hasSmsReadPermission,
            isDefaultSmsApp = state.isDefaultSmsApp,
            smsPermissionState = state.smsPermissionState,
            hasPostNotificationPermission = state.hasPostNotificationPermission,
            hasNotificationListenerAccess = state.hasNotificationListenerAccess,
            dataHealthUpdatedTimeText = state.dataHealthUpdatedTimeText,
        )
    }

    fun lines(state: LauncherUiState): List<DataHealthLine> {
        return lines(
            hasUsageAccess = state.hasUsageAccess,
            hasLocationPermission = state.hasLocationPermission,
            hasCallLogPermission = state.hasCallLogPermission,
            hasSmsReadPermission = state.hasSmsReadPermission,
            isDefaultSmsApp = state.isDefaultSmsApp,
            smsPermissionState = state.smsPermissionState,
            hasPostNotificationPermission = state.hasPostNotificationPermission,
            hasNotificationListenerAccess = state.hasNotificationListenerAccess,
            dataHealthUpdatedTimeText = state.dataHealthUpdatedTimeText,
        )
    }

    private fun summary(
        hasUsageAccess: Boolean,
        hasLocationPermission: Boolean,
        hasCallLogPermission: Boolean,
        hasSmsReadPermission: Boolean,
        isDefaultSmsApp: Boolean,
        smsPermissionState: SmsPermissionState,
        hasPostNotificationPermission: Boolean,
        hasNotificationListenerAccess: Boolean,
    ): String {
        val issueCount = listOf(
            hasUsageAccess,
            hasLocationPermission,
            hasCallLogPermission,
            hasSmsReadPermission,
            isDefaultSmsApp,
            smsPermissionState == SmsPermissionState.READY,
            hasPostNotificationPermission,
            hasNotificationListenerAccess,
        ).count { !it }
        return if (issueCount == 0) "OK" else "$issueCount ISSUE"
    }

    private fun lines(
        hasUsageAccess: Boolean,
        hasLocationPermission: Boolean,
        hasCallLogPermission: Boolean,
        hasSmsReadPermission: Boolean,
        isDefaultSmsApp: Boolean,
        smsPermissionState: SmsPermissionState,
        hasPostNotificationPermission: Boolean,
        hasNotificationListenerAccess: Boolean,
        dataHealthUpdatedTimeText: String,
    ): List<DataHealthLine> {
        return listOf(
            DataHealthLine(DataHealthItem.UPDATED, "UPDATED", dataHealthUpdatedTimeText.ifBlank { "--:--" }),
            accessLine(
                item = DataHealthItem.USAGE,
                title = "USAGE",
                ready = hasUsageAccess,
                missingValue = "NO ACCESS",
                missingReason = "USAGE ACCESS",
            ),
            accessLine(
                item = DataHealthItem.LOCATION,
                title = "LOCATION",
                ready = hasLocationPermission,
                missingValue = "NO PERM",
                missingReason = "RUNTIME PERM",
            ),
            accessLine(
                item = DataHealthItem.CALL_LOG,
                title = "CALL LOG",
                ready = hasCallLogPermission,
                missingValue = "NO PERM",
                missingReason = "RUNTIME PERM",
            ),
            accessLine(
                item = DataHealthItem.SMS_READ,
                title = "SMS READ",
                ready = hasSmsReadPermission,
                missingValue = "NO READ",
                missingReason = "RUNTIME PERM",
            ),
            DataHealthLine(
                item = DataHealthItem.SMS_APP,
                title = "SMS APP",
                value = if (isDefaultSmsApp) "DEFAULT" else "SYSTEM",
                reason = if (isDefaultSmsApp) "" else "DEFAULT SMS ROLE",
            ),
            DataHealthLine(
                item = DataHealthItem.SMS_SEND,
                title = "SMS SEND",
                value = smsPermissionLabel(smsPermissionState),
                reason = smsPermissionReason(smsPermissionState),
            ),
            accessLine(
                item = DataHealthItem.NOTIFICATION_POST,
                title = "NOTIFY POST",
                ready = hasPostNotificationPermission,
                missingValue = "NO POST",
                missingReason = "POST NOTIFY",
            ),
            accessLine(
                item = DataHealthItem.NOTIFICATION_LISTENER,
                title = "NOTIFY LISTEN",
                ready = hasNotificationListenerAccess,
                missingValue = "NO LISTEN",
                missingReason = "LISTENER ACCESS",
            ),
        )
    }

    private fun accessLine(
        item: DataHealthItem,
        title: String,
        ready: Boolean,
        missingValue: String,
        missingReason: String,
    ): DataHealthLine {
        return DataHealthLine(
            item = item,
            title = title,
            value = if (ready) "READY" else missingValue,
            reason = if (ready) "" else missingReason,
        )
    }

    private fun smsPermissionLabel(state: SmsPermissionState): String {
        return when (state) {
            SmsPermissionState.MISSING -> "MISSING"
            SmsPermissionState.READ_ONLY -> "READ ONLY"
            SmsPermissionState.READY -> "READY"
        }
    }

    private fun smsPermissionReason(state: SmsPermissionState): String {
        return when (state) {
            SmsPermissionState.MISSING -> "SMS PERM + ROLE"
            SmsPermissionState.READ_ONLY -> "DEFAULT SMS ROLE"
            SmsPermissionState.READY -> ""
        }
    }
}
