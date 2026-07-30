package com.purride.pixellauncherv2.acceptance

import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.LauncherStateTransitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Launcher 核心状态旅程的 JVM 验收测试。
 *
 * 这里串联真实 reducer，验证默认状态、应用加载、抽屉搜索与顶层页面返回形成一条完整模型链路，
 * 且不启动 Activity、不申请权限，也不依赖设备上的应用、短信或通话数据。
 */
class LauncherCoreStateJourneyAcceptanceTest {

    /** 默认模型必须先停留在 Home，并等待应用仓库完成首次加载。 */
    @Test
    fun defaultState_usesHomeLoadingStateBeforeAppsArrive() {
        val defaultState = LauncherState()

        assertEquals(LauncherMode.HOME, defaultState.mode)
        assertTrue(defaultState.isLoading)
        assertTrue(defaultState.apps.isEmpty())
        assertTrue(defaultState.drawerVisibleApps.isEmpty())
        assertEquals("", defaultState.drawerQuery)
    }

    /** 核心模型链路应能从 Home 搜索应用、进入设置，再回到原抽屉和 Home。 */
    @Test
    fun stateJourney_loadsAppsSearchesDrawerAndReturnsThroughSettings() {
        val installedApps = listOf(
            AppEntry(
                label = "微信",
                packageName = "com.tencent.mm",
                activityName = "MainActivity",
            ),
            AppEntry(
                label = "浏览器",
                englishLabel = "Browser",
                packageName = "org.mozilla.firefox",
                activityName = "BrowserActivity",
            ),
            AppEntry(
                label = "相机",
                packageName = "com.android.camera",
                activityName = "CaptureActivity",
            ),
        )

        val loadedState = LauncherStateTransitions.withApps(
            previous = LauncherState(),
            apps = installedApps,
            visibleRows = 4,
        )
        assertFalse(loadedState.isLoading)
        assertEquals(installedApps.toSet(), loadedState.apps.toSet())
        assertEquals(installedApps.size, loadedState.drawerVisibleApps.size)

        val drawerState = LauncherStateTransitions.showAppDrawer(loadedState, visibleRows = 4)
        assertEquals(LauncherMode.APP_DRAWER, drawerState.mode)
        assertEquals(0, drawerState.selectedIndex)

        val searchedState = LauncherStateTransitions.updateDrawerQuery(
            state = drawerState,
            query = "wx",
            visibleRows = 4,
        )
        assertEquals("WX", searchedState.drawerQuery)
        assertEquals(listOf("微信"), searchedState.drawerVisibleApps.map(AppEntry::label))
        assertEquals(0, searchedState.selectedIndex)

        val settingsState = LauncherStateTransitions.showSettings(searchedState, visibleRows = 4)
        assertEquals(LauncherMode.SETTINGS, settingsState.mode)
        assertEquals(LauncherMode.APP_DRAWER, settingsState.returnMode)

        val returnedDrawerState = LauncherStateTransitions.hideSettings(settingsState)
        assertEquals(LauncherMode.APP_DRAWER, returnedDrawerState.mode)
        assertEquals("WX", returnedDrawerState.drawerQuery)
        assertEquals(listOf("微信"), returnedDrawerState.drawerVisibleApps.map(AppEntry::label))

        val homeState = LauncherStateTransitions.showHome(returnedDrawerState)
        assertEquals(LauncherMode.HOME, homeState.mode)
        assertFalse(homeState.isDrawerSearchFocused)
    }
}
