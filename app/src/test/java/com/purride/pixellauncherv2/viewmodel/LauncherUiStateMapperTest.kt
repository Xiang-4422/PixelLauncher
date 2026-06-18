package com.purride.pixellauncherv2.viewmodel

import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.PixelFontStyle
import com.purride.pixellauncherv2.launcher.PixelTheme
import com.purride.pixellauncherv2.launcher.SmsPermissionState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards [toLauncherUiState]: the ViewModel projection that translates the
 * internal [LauncherState] into the immutable [LauncherUiState] snapshot the
 * pixel-engine host renders.
 *
 * The mapper is a field-by-field copy that intentionally drops the IDLE-only
 * fields (isIdlePageEnabled / chargeIdleEffect) — that omission is enforced by
 * the type (LauncherUiState has no such fields), so the test focuses on
 * verifying the shared fields survive the mapping rather than getting crossed.
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
            smsDraftText = "draft",
            smsCurrentThreadId = 7L,
            smsCurrentAddress = "10086",
            isDefaultSmsApp = true,
            smsPermissionState = SmsPermissionState.READY,
            selectedFontStyle = PixelFontStyle.MONO,
            selectedDotSizePx = 9,
            isPixelGapEnabled = false,
            pixelGapRatio = 0.5f,
            selectedTheme = PixelTheme.NIGHT,
            drawerListAlignment = DrawerListAlignment.CENTER,
            batteryLevel = 42,
            isCharging = true,
            launchCount = 11,
            lastLaunchPackageName = "p.a",
            nextAlarmText = "07:30",
            missedCallCount = 2,
            unreadSmsCount = 5,
            rainHintText = "RAIN 18:00",
            screenUsageTimeText = "01:23",
            screenOpenCountText = "37",
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
        // sms
        assertEquals("draft", ui.smsDraftText)
        assertEquals(7L, ui.smsCurrentThreadId)
        assertEquals("10086", ui.smsCurrentAddress)
        assertEquals(true, ui.isDefaultSmsApp)
        assertEquals(SmsPermissionState.READY, ui.smsPermissionState)
        // appearance
        assertEquals(PixelFontStyle.MONO, ui.selectedFontStyle)
        assertEquals(9, ui.selectedDotSizePx)
        assertEquals(false, ui.isPixelGapEnabled)
        assertEquals(0.5f, ui.pixelGapRatio, 0f)
        assertEquals(PixelTheme.NIGHT, ui.selectedTheme)
        assertEquals(DrawerListAlignment.CENTER, ui.drawerListAlignment)
        // device + stats + status rows
        assertEquals(42, ui.batteryLevel)
        assertEquals(true, ui.isCharging)
        assertEquals(11, ui.launchCount)
        assertEquals("p.a", ui.lastLaunchPackageName)
        assertEquals("07:30", ui.nextAlarmText)
        assertEquals(2, ui.missedCallCount)
        assertEquals(5, ui.unreadSmsCount)
        assertEquals("RAIN 18:00", ui.rainHintText)
        assertEquals("01:23", ui.screenUsageTimeText)
        assertEquals("37", ui.screenOpenCountText)
    }

    @Test
    fun toLauncherUiState_defaultStateMapsToDefaultSnapshot() {
        assertEquals(LauncherUiState(), LauncherState().toLauncherUiState())
    }
}
