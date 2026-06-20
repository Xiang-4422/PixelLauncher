package com.purride.pixellauncherv2.viewmodel

import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.ChargeIdleEffect
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.PixelTheme
import com.purride.pixellauncherv2.launcher.SmsPermissionState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards [toLauncherUiState]: the ViewModel projection that translates the
 * internal [LauncherState] into the immutable [LauncherUiState] snapshot the
 * pixel-engine host renders.
 *
 * The mapper is a field-by-field copy, so this test focuses on verifying fields
 * survive the mapping rather than getting crossed.
 */
class LauncherUiStateMapperTest {

    @Test
    fun toLauncherUiState_carriesSharedFieldsAcrossEveryCategory() {
        val state = LauncherState(
            apps = listOf(AppEntry(label = "A", packageName = "p.a", activityName = "act")),
            drawerQuery = "qq",
            isDrawerSearchFocused = true,
            selectedIndex = 4,
            listStartIndex = 2,
            drawerPageIndex = 1,
            isLoading = false,
            currentTimeText = "09:41",
            currentDateText = "2026-05-31",
            mode = LauncherMode.APP_DRAWER,
            returnMode = LauncherMode.SETTINGS,
            settingsSelectedIndex = 3,
            appEditorSelectedIndex = 1,
            appEditorNameDraft = "Pay",
            appEditorAliasDraft = "bank bill",
            isSmsThreadsLoading = true,
            smsDraftText = "draft",
            smsSendStatusText = "FAILED",
            smsCurrentThreadId = 7L,
            smsCurrentAddress = "10086",
            isDefaultSmsApp = true,
            smsPermissionState = SmsPermissionState.READY,
            selectedDotSizePx = 9,
            isPixelGapEnabled = false,
            selectedTheme = PixelTheme.NIGHT,
            drawerListAlignment = DrawerListAlignment.CENTER,
            isIdlePageEnabled = true,
            chargeAutoIdleEnabled = true,
            inactivityAutoIdleEnabled = false,
            idleTimeoutSeconds = 60,
            openDrawerInSearchMode = true,
            chargeIdleEffect = ChargeIdleEffect.TANK,
            batteryLevel = 42,
            isCharging = true,
            launchCount = 11,
            lastLaunchPackageName = "p.a",
            nextAlarmText = "07:30",
            missedCallCount = 2,
            unreadSmsCount = 5,
            notificationSummaryText = "BANK OTP",
            notificationCount = 1,
            rainHintText = "RAIN 18:00",
            rainUpdatedTimeText = "09:41",
            screenUsageTimeText = "01:23",
            screenOpenCountText = "37",
            statusBarMessageText = "USE TODAY",
            hasUsageAccess = true,
            hasLocationPermission = true,
            hasCallLogPermission = false,
            hasSmsReadPermission = true,
            hasPostNotificationPermission = false,
            hasNotificationListenerAccess = true,
            dataHealthUpdatedTimeText = "09:42",
        )

        val ui = state.toLauncherUiState()

        // drawer
        assertEquals(state.apps, ui.apps)
        assertEquals("qq", ui.drawerQuery)
        assertEquals(true, ui.isDrawerSearchFocused)
        assertEquals(4, ui.selectedIndex)
        assertEquals(2, ui.listStartIndex)
        assertEquals(1, ui.drawerPageIndex)
        assertEquals(false, ui.isLoading)
        // time + navigation
        assertEquals("09:41", ui.currentTimeText)
        assertEquals("2026-05-31", ui.currentDateText)
        assertEquals(LauncherMode.APP_DRAWER, ui.mode)
        assertEquals(LauncherMode.SETTINGS, ui.returnMode)
        assertEquals(3, ui.settingsSelectedIndex)
        assertEquals(1, ui.appEditorSelectedIndex)
        assertEquals("Pay", ui.appEditorNameDraft)
        assertEquals("bank bill", ui.appEditorAliasDraft)
        // sms
        assertEquals(true, ui.isSmsThreadsLoading)
        assertEquals("draft", ui.smsDraftText)
        assertEquals("FAILED", ui.smsSendStatusText)
        assertEquals(7L, ui.smsCurrentThreadId)
        assertEquals("10086", ui.smsCurrentAddress)
        assertEquals(true, ui.isDefaultSmsApp)
        assertEquals(SmsPermissionState.READY, ui.smsPermissionState)
        // appearance
        assertEquals(9, ui.selectedDotSizePx)
        assertEquals(false, ui.isPixelGapEnabled)
        assertEquals(PixelTheme.NIGHT, ui.selectedTheme)
        assertEquals(DrawerListAlignment.CENTER, ui.drawerListAlignment)
        assertEquals(true, ui.isIdlePageEnabled)
        assertEquals(true, ui.chargeAutoIdleEnabled)
        assertEquals(false, ui.inactivityAutoIdleEnabled)
        assertEquals(60, ui.idleTimeoutSeconds)
        assertEquals(true, ui.openDrawerInSearchMode)
        assertEquals(ChargeIdleEffect.TANK, ui.chargeIdleEffect)
        // device + stats + status rows
        assertEquals(42, ui.batteryLevel)
        assertEquals(true, ui.isCharging)
        assertEquals(11, ui.launchCount)
        assertEquals("p.a", ui.lastLaunchPackageName)
        assertEquals("07:30", ui.nextAlarmText)
        assertEquals(2, ui.missedCallCount)
        assertEquals(5, ui.unreadSmsCount)
        assertEquals("BANK OTP", ui.notificationSummaryText)
        assertEquals(1, ui.notificationCount)
        assertEquals("RAIN 18:00", ui.rainHintText)
        assertEquals("09:41", ui.rainUpdatedTimeText)
        assertEquals("01:23", ui.screenUsageTimeText)
        assertEquals("37", ui.screenOpenCountText)
        assertEquals("USE TODAY", ui.statusBarMessageText)
        assertEquals(true, ui.hasUsageAccess)
        assertEquals(true, ui.hasLocationPermission)
        assertEquals(false, ui.hasCallLogPermission)
        assertEquals(true, ui.hasSmsReadPermission)
        assertEquals(false, ui.hasPostNotificationPermission)
        assertEquals(true, ui.hasNotificationListenerAccess)
        assertEquals("09:42", ui.dataHealthUpdatedTimeText)
    }

    @Test
    fun toLauncherUiState_defaultStateMapsToDefaultSnapshot() {
        assertEquals(LauncherUiState(), LauncherState().toLauncherUiState())
    }
}
