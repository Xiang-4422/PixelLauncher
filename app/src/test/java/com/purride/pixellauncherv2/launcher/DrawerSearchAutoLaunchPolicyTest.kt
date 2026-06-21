package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrawerSearchAutoLaunchPolicyTest {

    private val browser = AppEntry(
        label = "Browser",
        packageName = "com.example.browser",
        activityName = "BrowserActivity",
    )
    private val camera = AppEntry(
        label = "Camera",
        packageName = "com.example.camera",
        activityName = "CameraActivity",
    )

    @Test
    fun uniqueNonEmptySearchResultIsLaunched() {
        val state = drawerState(query = "BRO", results = listOf(browser))

        assertEquals(browser, DrawerSearchAutoLaunchPolicy.resolve(state))
    }

    @Test
    fun blankSearchNeverAutoLaunches() {
        val state = drawerState(query = "", results = listOf(browser))

        assertNull(DrawerSearchAutoLaunchPolicy.resolve(state))
    }

    @Test
    fun multipleOrMissingResultsDoNotAutoLaunch() {
        assertNull(DrawerSearchAutoLaunchPolicy.resolve(drawerState("A", listOf(browser, camera))))
        assertNull(DrawerSearchAutoLaunchPolicy.resolve(drawerState("NONE", emptyList())))
    }

    @Test
    fun inactiveDrawerSearchDoesNotAutoLaunch() {
        val state = drawerState(query = "BRO", results = listOf(browser)).copy(
            isDrawerSearchFocused = false,
        )

        assertNull(DrawerSearchAutoLaunchPolicy.resolve(state))
    }

    private fun drawerState(query: String, results: List<AppEntry>): LauncherState = LauncherState(
        mode = LauncherMode.APP_DRAWER,
        drawerQuery = query,
        drawerVisibleApps = results,
        isDrawerSearchFocused = true,
    )
}
