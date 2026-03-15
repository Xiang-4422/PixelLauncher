package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.data.DeviceStatus
import com.purride.pixellauncherv2.data.LauncherStatsSnapshot
import com.purride.pixellauncherv2.render.PixelTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherStateTransitionsTest {

    private val apps = List(8) { index ->
        AppEntry(
            label = "App $index",
            packageName = "pkg.$index",
            activityName = "Activity$index",
        )
    }

    @Test
    fun moveSelectionSnapsWindowToNewPageWhenSelectionLeavesViewport() {
        val state = LauncherState(
            apps = apps,
            selectedIndex = 2,
            listStartIndex = 0,
            isLoading = false,
        )

        val movedState = LauncherStateTransitions.moveSelection(
            state = state,
            delta = 1,
            visibleRows = 3,
        )

        assertEquals(3, movedState.selectedIndex)
        assertEquals(3, movedState.listStartIndex)
        assertEquals(1, movedState.drawerPageIndex)
    }

    @Test
    fun withAppsPreservesPreviousSelectionWhenPossible() {
        val previous = LauncherState(
            apps = apps,
            selectedIndex = 4,
            listStartIndex = 2,
            isLoading = true,
        )
        val reloadedApps = listOf(apps[0], apps[4], apps[6])

        val newState = LauncherStateTransitions.withApps(
            previous = previous,
            apps = reloadedApps,
            visibleRows = 2,
        )

        assertEquals(1, newState.selectedIndex)
        assertEquals(0, newState.listStartIndex)
        assertEquals(0, newState.drawerPageIndex)
        assertEquals(false, newState.isLoading)
    }

    @Test
    fun showAppDrawerMakesSelectedItemVisible() {
        val state = LauncherState(
            apps = apps,
            selectedIndex = 6,
            listStartIndex = 0,
            isLoading = false,
        )

        val drawerState = LauncherStateTransitions.showAppDrawer(
            state = state,
            visibleRows = 3,
        )

        assertEquals(LauncherMode.APP_DRAWER, drawerState.mode)
        assertEquals(6, drawerState.listStartIndex)
        assertEquals(6, drawerState.selectedIndex)
        assertEquals(2, drawerState.drawerPageIndex)
    }

    @Test
    fun showAppDrawerUsesAllAppsWhenQueryIsBlank() {
        val state = LauncherState(
            apps = listOf(
                AppEntry(label = "Alpha", packageName = "pkg.a", activityName = "A"),
                AppEntry(label = "Bravo", packageName = "pkg.b", activityName = "B"),
                AppEntry(label = "Charlie", packageName = "pkg.c", activityName = "C"),
            ),
            recentApps = listOf("pkg.c", "pkg.a"),
            selectedIndex = 0,
            isLoading = false,
        )

        val drawerState = LauncherStateTransitions.showAppDrawer(
            state = state,
            visibleRows = 3,
        )

        assertEquals(LauncherMode.APP_DRAWER, drawerState.mode)
        assertEquals(listOf("pkg.a", "pkg.b", "pkg.c"), drawerState.drawerVisibleApps.map { it.packageName })
        assertEquals(0, drawerState.selectedIndex)
        assertEquals(0, drawerState.listStartIndex)
    }

    @Test
    fun showHomeOnlySwitchesMode() {
        val state = LauncherState(
            apps = apps,
            selectedIndex = 5,
            listStartIndex = 3,
            isLoading = false,
            mode = LauncherMode.APP_DRAWER,
        )

        val homeState = LauncherStateTransitions.showHome(state)

        assertEquals(LauncherMode.HOME, homeState.mode)
        assertEquals(5, homeState.selectedIndex)
        assertEquals(3, homeState.listStartIndex)
    }

    @Test
    fun pageSelectionMovesDrawerToNextPage() {
        val state = LauncherState(
            apps = apps,
            selectedIndex = 0,
            listStartIndex = 0,
            isLoading = false,
            mode = LauncherMode.APP_DRAWER,
        )

        val pagedState = LauncherStateTransitions.pageSelection(
            state = state,
            direction = 1,
            visibleRows = 3,
        )

        assertEquals(3, pagedState.selectedIndex)
        assertEquals(3, pagedState.listStartIndex)
        assertEquals(1, pagedState.drawerPageIndex)
    }

    @Test
    fun selectDrawerPageJumpsToPageStartAndUpdatesPageIndex() {
        val state = LauncherState(
            apps = apps,
            selectedIndex = 1,
            listStartIndex = 0,
            isLoading = false,
            mode = LauncherMode.APP_DRAWER,
        )

        val selectedState = LauncherStateTransitions.selectDrawerPage(
            state = state,
            pageIndex = 2,
            visibleRows = 3,
        )

        assertEquals(6, selectedState.selectedIndex)
        assertEquals(6, selectedState.listStartIndex)
        assertEquals(2, selectedState.drawerPageIndex)
    }

    @Test
    fun showSettingsPreservesReturnModeFromDrawer() {
        val state = LauncherState(
            apps = apps,
            selectedIndex = 2,
            listStartIndex = 1,
            isLoading = false,
            mode = LauncherMode.APP_DRAWER,
        )

        val settingsState = LauncherStateTransitions.showSettings(state)

        assertEquals(LauncherMode.SETTINGS, settingsState.mode)
        assertEquals(LauncherMode.APP_DRAWER, settingsState.returnMode)
    }

    @Test
    fun hideSettingsReturnsToPreviousMode() {
        val state = LauncherState(
            apps = apps,
            isLoading = false,
            mode = LauncherMode.SETTINGS,
            returnMode = LauncherMode.APP_DRAWER,
        )

        val contentState = LauncherStateTransitions.hideSettings(state)

        assertEquals(LauncherMode.APP_DRAWER, contentState.mode)
    }

    @Test
    fun showDiagnosticsKeepsExistingReturnModeForSettingsExit() {
        val state = LauncherState(
            mode = LauncherMode.SETTINGS,
            returnMode = LauncherMode.HOME,
        )

        val diagnosticsState = LauncherStateTransitions.showDiagnostics(state)

        assertEquals(LauncherMode.DIAGNOSTICS, diagnosticsState.mode)
        assertEquals(LauncherMode.HOME, diagnosticsState.returnMode)
    }

    @Test
    fun hideSettingsFallsBackToHomeWhenReturnModeIsInvalid() {
        val state = LauncherState(
            mode = LauncherMode.SETTINGS,
            returnMode = LauncherMode.SETTINGS,
        )

        val contentState = LauncherStateTransitions.hideSettings(state)

        assertEquals(LauncherMode.HOME, contentState.mode)
        assertEquals(LauncherMode.HOME, contentState.returnMode)
    }

    @Test
    fun updateAppearanceStoresSelectedResolutionPreset() {
        val updatedState = LauncherStateTransitions.updateAppearance(
            state = LauncherState(),
            selectedDotSizePx = 18,
            selectedTheme = PixelTheme.AMBER_CRT,
        )

        assertEquals(18, updatedState.selectedDotSizePx)
        assertEquals(PixelTheme.AMBER_CRT, updatedState.selectedTheme)
    }

    @Test
    fun showIdleStoresPreviousModeForWakeUp() {
        val idleState = LauncherStateTransitions.showIdle(
            LauncherState(mode = LauncherMode.APP_DRAWER),
        )

        assertEquals(LauncherMode.IDLE, idleState.mode)
        assertEquals(LauncherMode.APP_DRAWER, idleState.returnMode)
    }

    @Test
    fun updateStatsStoresRecentAppsAndLaunchMetadata() {
        val updatedState = LauncherStateTransitions.updateStats(
            state = LauncherState(),
            stats = LauncherStatsSnapshot(
                launchCount = 4,
                recentApps = listOf("pkg.camera", "pkg.music"),
                lastLaunchPackageName = "pkg.camera",
            ),
        )

        assertEquals(4, updatedState.launchCount)
        assertEquals(listOf("pkg.camera", "pkg.music"), updatedState.recentApps)
        assertEquals("pkg.camera", updatedState.lastLaunchPackageName)
    }

    @Test
    fun updateDeviceStatusStoresBatteryAndCharging() {
        val updatedState = LauncherStateTransitions.updateDeviceStatus(
            state = LauncherState(),
            deviceStatus = DeviceStatus(
                batteryLevel = 42,
                isCharging = true,
            ),
        )

        assertEquals(42, updatedState.batteryLevel)
        assertEquals(true, updatedState.isCharging)
    }

    @Test
    fun withAppsKeepsAlphabeticalOrderWithoutRecentBoost() {
        val previous = LauncherState(
            recentApps = listOf("pkg.z"),
            isLoading = true,
        )
        val unsortedApps = listOf(
            AppEntry(label = "Zulu", packageName = "pkg.z", activityName = "Z"),
            AppEntry(label = "Alpha", packageName = "pkg.a", activityName = "A"),
            AppEntry(label = "Bravo", packageName = "pkg.b", activityName = "B"),
        )

        val newState = LauncherStateTransitions.withApps(
            previous = previous,
            apps = unsortedApps,
            visibleRows = 3,
        )

        assertEquals(listOf("pkg.a", "pkg.b", "pkg.z"), newState.drawerVisibleApps.map { it.packageName })
    }

    @Test
    fun selectByLetterIndexMovesSelectionAndWindow() {
        val state = LauncherState(
            apps = listOf(
                AppEntry(label = "Alpha", packageName = "pkg.a", activityName = "A"),
                AppEntry(label = "Charlie", packageName = "pkg.c", activityName = "C"),
                AppEntry(label = "Foxtrot", packageName = "pkg.f", activityName = "F"),
            ),
            drawerVisibleApps = listOf(
                AppEntry(label = "Alpha", packageName = "pkg.a", activityName = "A"),
                AppEntry(label = "Charlie", packageName = "pkg.c", activityName = "C"),
                AppEntry(label = "Foxtrot", packageName = "pkg.f", activityName = "F"),
            ),
            selectedIndex = 0,
            listStartIndex = 0,
            mode = LauncherMode.APP_DRAWER,
            isLoading = false,
        )

        val selectedState = LauncherStateTransitions.selectByLetterIndex(
            state = state,
            letterIndex = 5, // F
            visibleRows = 2,
        )

        assertEquals(2, selectedState.selectedIndex)
        assertEquals(2, selectedState.listStartIndex)
        assertEquals(1, selectedState.drawerPageIndex)
    }

    @Test
    fun exitDrawerSearchClearsQueryAndSearchFlagsWhileStayingInDrawer() {
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            apps = listOf(
                AppEntry(label = "Alpha", packageName = "pkg.a", activityName = "A"),
                AppEntry(label = "Bravo", packageName = "pkg.b", activityName = "B"),
                AppEntry(label = "Charlie", packageName = "pkg.c", activityName = "C"),
            ),
            drawerVisibleApps = listOf(
                AppEntry(label = "Charlie", packageName = "pkg.c", activityName = "C"),
            ),
            drawerQuery = "cha",
            isDrawerSearchFocused = true,
            isDrawerRailSliding = true,
            selectedIndex = 0,
            listStartIndex = 0,
        )

        val exitedState = LauncherStateTransitions.exitDrawerSearch(
            state = state,
            visibleRows = 3,
        )

        assertEquals(LauncherMode.APP_DRAWER, exitedState.mode)
        assertEquals("", exitedState.drawerQuery)
        assertEquals(false, exitedState.isDrawerSearchFocused)
        assertEquals(false, exitedState.isDrawerRailSliding)
        assertEquals(listOf("pkg.a", "pkg.b", "pkg.c"), exitedState.drawerVisibleApps.map { it.packageName })
    }

    @Test
    fun exitDrawerSearchWithBlankQueryStillResetsSearchFlags() {
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            apps = listOf(
                AppEntry(label = "Alpha", packageName = "pkg.a", activityName = "A"),
                AppEntry(label = "Bravo", packageName = "pkg.b", activityName = "B"),
            ),
            drawerVisibleApps = listOf(
                AppEntry(label = "Bravo", packageName = "pkg.b", activityName = "B"),
            ),
            drawerQuery = "",
            isDrawerSearchFocused = true,
            isDrawerRailSliding = true,
            selectedIndex = 0,
            listStartIndex = 0,
        )

        val exitedState = LauncherStateTransitions.exitDrawerSearch(
            state = state,
            visibleRows = 3,
        )

        assertEquals("", exitedState.drawerQuery)
        assertEquals(false, exitedState.isDrawerSearchFocused)
        assertEquals(false, exitedState.isDrawerRailSliding)
        assertEquals(listOf("pkg.a", "pkg.b"), exitedState.drawerVisibleApps.map { it.packageName })
    }
}
