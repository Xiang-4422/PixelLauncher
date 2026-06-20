package com.purride.pixellauncherv2.launcher

enum class DataHealthRepairAction {
    NONE,
    OPEN_USAGE_ACCESS_SETTINGS,
    REQUEST_LOCATION_PERMISSION,
    REQUEST_CALL_LOG_PERMISSION,
    REQUEST_SMS_READ_PERMISSION,
    ENSURE_SMS_ROLE,
    REQUEST_POST_NOTIFICATIONS_PERMISSION,
    OPEN_NOTIFICATION_LISTENER_SETTINGS,
}

object DataHealthRepairActionModel {

    fun actionFor(
        item: DataHealthItem,
        postNotificationsRuntimePermissionRequired: Boolean,
    ): DataHealthRepairAction {
        return when (item) {
            DataHealthItem.UPDATED -> DataHealthRepairAction.NONE
            DataHealthItem.USAGE -> DataHealthRepairAction.OPEN_USAGE_ACCESS_SETTINGS
            DataHealthItem.LOCATION -> DataHealthRepairAction.REQUEST_LOCATION_PERMISSION
            DataHealthItem.CALL_LOG -> DataHealthRepairAction.REQUEST_CALL_LOG_PERMISSION
            DataHealthItem.SMS_READ -> DataHealthRepairAction.REQUEST_SMS_READ_PERMISSION
            DataHealthItem.SMS_APP,
            DataHealthItem.SMS_SEND -> DataHealthRepairAction.ENSURE_SMS_ROLE

            DataHealthItem.NOTIFICATION_POST -> {
                if (postNotificationsRuntimePermissionRequired) {
                    DataHealthRepairAction.REQUEST_POST_NOTIFICATIONS_PERMISSION
                } else {
                    DataHealthRepairAction.NONE
                }
            }

            DataHealthItem.NOTIFICATION_LISTENER -> DataHealthRepairAction.OPEN_NOTIFICATION_LISTENER_SETTINGS
        }
    }
}
