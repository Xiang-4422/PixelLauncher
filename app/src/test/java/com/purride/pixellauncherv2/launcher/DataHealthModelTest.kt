package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class DataHealthModelTest {

    @Test
    fun summary_isOkWhenRequiredDataSourcesAreReady() {
        val state = LauncherState(
            hasUsageAccess = true,
            hasLocationPermission = true,
            hasCallLogPermission = true,
            hasSmsReadPermission = true,
            isDefaultSmsApp = true,
            smsPermissionState = SmsPermissionState.READY,
            hasPostNotificationPermission = true,
            hasNotificationListenerAccess = true,
        )

        assertEquals("OK", DataHealthModel.summary(state))
    }

    @Test
    fun summary_countsMissingDataSources() {
        val state = LauncherState(
            hasUsageAccess = true,
            hasLocationPermission = false,
            hasCallLogPermission = true,
            hasSmsReadPermission = false,
            isDefaultSmsApp = false,
            smsPermissionState = SmsPermissionState.MISSING,
            hasPostNotificationPermission = true,
            hasNotificationListenerAccess = false,
        )

        assertEquals("5 ISSUE", DataHealthModel.summary(state))
    }

    @Test
    fun summaryCountsSmsSendReadiness() {
        val state = LauncherState(
            hasUsageAccess = true,
            hasLocationPermission = true,
            hasCallLogPermission = true,
            hasSmsReadPermission = true,
            isDefaultSmsApp = true,
            smsPermissionState = SmsPermissionState.READ_ONLY,
            hasPostNotificationPermission = true,
            hasNotificationListenerAccess = true,
        )

        assertEquals("1 ISSUE", DataHealthModel.summary(state))
    }

    @Test
    fun linesExposeDataSourceReadinessAndMissingReasons() {
        val state = LauncherState(
            hasUsageAccess = true,
            hasLocationPermission = false,
            hasCallLogPermission = true,
            hasSmsReadPermission = true,
            isDefaultSmsApp = false,
            smsPermissionState = SmsPermissionState.READ_ONLY,
            hasPostNotificationPermission = false,
            hasNotificationListenerAccess = true,
            dataHealthUpdatedTimeText = "09:41",
        )

        val lines = DataHealthModel.lines(state)
        val byTitle = lines.associateBy { it.title }
        val items = lines.map { it.item }

        assertEquals(
            listOf(
                DataHealthItem.UPDATED,
                DataHealthItem.USAGE,
                DataHealthItem.LOCATION,
                DataHealthItem.CALL_LOG,
                DataHealthItem.SMS_READ,
                DataHealthItem.SMS_APP,
                DataHealthItem.SMS_SEND,
                DataHealthItem.NOTIFICATION_POST,
                DataHealthItem.NOTIFICATION_LISTENER,
            ),
            items,
        )
        assertEquals("09:41", byTitle.getValue("UPDATED").value)
        assertEquals("READY", byTitle.getValue("USAGE").value)
        assertEquals("", byTitle.getValue("USAGE").reason)
        assertEquals("NO PERM", byTitle.getValue("LOCATION").value)
        assertEquals("RUNTIME PERM", byTitle.getValue("LOCATION").reason)
        assertEquals("READY", byTitle.getValue("CALL LOG").value)
        assertEquals("", byTitle.getValue("CALL LOG").reason)
        assertEquals("READY", byTitle.getValue("SMS READ").value)
        assertEquals("SYSTEM", byTitle.getValue("SMS APP").value)
        assertEquals("DEFAULT SMS ROLE", byTitle.getValue("SMS APP").reason)
        assertEquals("READ ONLY", byTitle.getValue("SMS SEND").value)
        assertEquals("DEFAULT SMS ROLE", byTitle.getValue("SMS SEND").reason)
        assertEquals("NO POST", byTitle.getValue("NOTIFY POST").value)
        assertEquals("POST NOTIFY", byTitle.getValue("NOTIFY POST").reason)
        assertEquals("READY", byTitle.getValue("NOTIFY LISTEN").value)
        assertEquals("", byTitle.getValue("NOTIFY LISTEN").reason)
    }

    @Test
    fun linesUsePlaceholderWhenUpdateTimeIsMissing() {
        val byTitle = DataHealthModel.lines(LauncherState()).associate { it.title to it.value }

        assertEquals("--:--", byTitle["UPDATED"])
    }

    @Test
    fun missingNotificationListenerShowsAndroidAccessReason() {
        val byTitle = DataHealthModel.lines(LauncherState()).associateBy { it.title }

        assertEquals("NO LISTEN", byTitle.getValue("NOTIFY LISTEN").value)
        assertEquals("LISTENER ACCESS", byTitle.getValue("NOTIFY LISTEN").reason)
    }
}
