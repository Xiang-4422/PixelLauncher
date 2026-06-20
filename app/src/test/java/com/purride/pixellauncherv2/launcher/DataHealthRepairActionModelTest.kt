package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class DataHealthRepairActionModelTest {

    @Test
    fun actionFor_routesEveryDataHealthItemToItsRepairEntry() {
        val actions = DataHealthItem.entries.associateWith { item ->
            DataHealthRepairActionModel.actionFor(
                item = item,
                postNotificationsRuntimePermissionRequired = true,
            )
        }

        assertEquals(DataHealthRepairAction.NONE, actions.getValue(DataHealthItem.UPDATED))
        assertEquals(DataHealthRepairAction.OPEN_USAGE_ACCESS_SETTINGS, actions.getValue(DataHealthItem.USAGE))
        assertEquals(DataHealthRepairAction.REQUEST_LOCATION_PERMISSION, actions.getValue(DataHealthItem.LOCATION))
        assertEquals(DataHealthRepairAction.REQUEST_CALL_LOG_PERMISSION, actions.getValue(DataHealthItem.CALL_LOG))
        assertEquals(DataHealthRepairAction.REQUEST_SMS_READ_PERMISSION, actions.getValue(DataHealthItem.SMS_READ))
        assertEquals(DataHealthRepairAction.ENSURE_SMS_ROLE, actions.getValue(DataHealthItem.SMS_APP))
        assertEquals(DataHealthRepairAction.ENSURE_SMS_ROLE, actions.getValue(DataHealthItem.SMS_SEND))
        assertEquals(
            DataHealthRepairAction.REQUEST_POST_NOTIFICATIONS_PERMISSION,
            actions.getValue(DataHealthItem.NOTIFICATION_POST),
        )
        assertEquals(
            DataHealthRepairAction.OPEN_NOTIFICATION_LISTENER_SETTINGS,
            actions.getValue(DataHealthItem.NOTIFICATION_LISTENER),
        )
    }

    @Test
    fun actionFor_omitsNotificationPostRuntimePermissionBeforeAndroid13() {
        assertEquals(
            DataHealthRepairAction.NONE,
            DataHealthRepairActionModel.actionFor(
                item = DataHealthItem.NOTIFICATION_POST,
                postNotificationsRuntimePermissionRequired = false,
            ),
        )
    }
}
