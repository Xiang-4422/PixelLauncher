package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.data.DeviceStatus
import com.purride.pixellauncherv2.data.LauncherStatsSnapshot
import com.purride.pixellauncherv2.render.PixelTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun withAppsPreservesPreviousSelectionAndMovesItToTopWhenPossible() {
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
        assertEquals(1, newState.listStartIndex)
        assertEquals(0, newState.drawerPageIndex)
        assertEquals(false, newState.isLoading)
    }

    @Test
    fun showAppDrawerResetsToFirstItem() {
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
        assertEquals(0, drawerState.listStartIndex)
        assertEquals(0, drawerState.selectedIndex)
        assertEquals(0, drawerState.drawerPageIndex)
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
    fun pageSelectionAdvancesFromCurrentTopItemWithoutOverlap() {
        val largerApps = List(20) { index ->
            AppEntry(
                label = "App $index",
                packageName = "pkg.$index",
                activityName = "Activity$index",
            )
        }
        val state = LauncherState(
            apps = largerApps,
            drawerVisibleApps = largerApps,
            selectedIndex = 8,
            listStartIndex = 8,
            isLoading = false,
            mode = LauncherMode.APP_DRAWER,
        )

        val pagedState = LauncherStateTransitions.pageSelection(
            state = state,
            direction = 1,
            visibleRows = 7,
        )

        assertEquals(15, pagedState.selectedIndex)
        assertEquals(15, pagedState.listStartIndex)
        assertEquals(2, pagedState.drawerPageIndex)
    }

    @Test
    fun pageSelectionMovesBackwardFromCurrentTopItemWithoutOverlap() {
        val largerApps = List(20) { index ->
            AppEntry(
                label = "App $index",
                packageName = "pkg.$index",
                activityName = "Activity$index",
            )
        }
        val state = LauncherState(
            apps = largerApps,
            drawerVisibleApps = largerApps,
            selectedIndex = 8,
            listStartIndex = 8,
            isLoading = false,
            mode = LauncherMode.APP_DRAWER,
        )

        val pagedState = LauncherStateTransitions.pageSelection(
            state = state,
            direction = -1,
            visibleRows = 7,
        )

        assertEquals(1, pagedState.selectedIndex)
        assertEquals(1, pagedState.listStartIndex)
        assertEquals(0, pagedState.drawerPageIndex)
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
    fun updateUiBehaviorStoresDrawerAndIdlePreferences() {
        val updatedState = LauncherStateTransitions.updateUiBehavior(
            state = LauncherState(),
            drawerListAlignment = DrawerListAlignment.RIGHT,
            isIdlePageEnabled = false,
            openDrawerInSearchMode = true,
        )

        assertEquals(DrawerListAlignment.RIGHT, updatedState.drawerListAlignment)
        assertEquals(false, updatedState.isIdlePageEnabled)
        assertEquals(true, updatedState.openDrawerInSearchMode)
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
    fun showIdleDoesNothingWhenIdlePageIsDisabled() {
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            isIdlePageEnabled = false,
        )

        val idleState = LauncherStateTransitions.showIdle(state)

        assertEquals(LauncherMode.APP_DRAWER, idleState.mode)
        assertEquals(false, idleState.isIdlePageEnabled)
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
    fun withAppsAppliesLightRecentBoostWithinSameInitialGroup() {
        val previous = LauncherState(
            recentApps = listOf("pkg.atlas"),
            isLoading = true,
        )
        val unsortedApps = listOf(
            AppEntry(label = "Alpha", packageName = "pkg.a", activityName = "A"),
            AppEntry(label = "Atlas", packageName = "pkg.atlas", activityName = "Atlas"),
            AppEntry(label = "Apex", packageName = "pkg.apex", activityName = "Apex"),
            AppEntry(label = "Bravo", packageName = "pkg.b", activityName = "B"),
        )

        val newState = LauncherStateTransitions.withApps(
            previous = previous,
            apps = unsortedApps,
            visibleRows = 3,
        )

        val orderedPackages = newState.drawerVisibleApps.map { it.packageName }
        assertTrue(orderedPackages.indexOf("pkg.atlas") < orderedPackages.indexOf("pkg.apex"))
        assertTrue(orderedPackages.indexOf("pkg.b") > orderedPackages.indexOf("pkg.apex"))
    }

    @Test
    fun updateDrawerQueryMatchesPinyinFullPrefix() {
        val apps = listOf(
            AppEntry(label = "微信", packageName = "pkg.wechat", activityName = "Wechat"),
            AppEntry(label = "相机", packageName = "pkg.camera", activityName = "Camera"),
        )
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            apps = apps,
            drawerVisibleApps = apps,
            recentApps = emptyList(),
            isLoading = false,
        )

        val updated = LauncherStateTransitions.updateDrawerQuery(
            state = state,
            query = "weixin",
            visibleRows = 4,
        )

        assertEquals(listOf("pkg.wechat"), updated.drawerVisibleApps.map { it.packageName })
        assertEquals(0, updated.selectedIndex)
        assertEquals(0, updated.listStartIndex)
    }

    @Test
    fun updateDrawerQueryMatchesChineseNameDirectly() {
        val apps = listOf(
            AppEntry(label = "微信", packageName = "pkg.wechat", activityName = "Wechat"),
            AppEntry(label = "支付宝", packageName = "pkg.alipay", activityName = "Alipay"),
        )
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            apps = apps,
            drawerVisibleApps = apps,
            recentApps = emptyList(),
            isLoading = false,
        )

        val updated = LauncherStateTransitions.updateDrawerQuery(
            state = state,
            query = "微",
            visibleRows = 4,
        )

        assertEquals(listOf("pkg.wechat"), updated.drawerVisibleApps.map { it.packageName })
    }

    @Test
    fun updateDrawerQueryMatchesPinyinInitialPrefix() {
        val apps = listOf(
            AppEntry(label = "支付宝", packageName = "pkg.alipay", activityName = "Alipay"),
            AppEntry(label = "浏览器", packageName = "pkg.browser", activityName = "Browser"),
        )
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            apps = apps,
            drawerVisibleApps = apps,
            recentApps = emptyList(),
            isLoading = false,
        )

        val updated = LauncherStateTransitions.updateDrawerQuery(
            state = state,
            query = "zfb",
            visibleRows = 4,
        )

        assertEquals(listOf("pkg.alipay"), updated.drawerVisibleApps.map { it.packageName })
    }

    @Test
    fun searchResultsUseRecentAppsAsLightTieBreakerWithinSameMatchTier() {
        val apps = listOf(
            AppEntry(label = "Calendar", packageName = "pkg.calendar", activityName = "Calendar"),
            AppEntry(label = "Camera", packageName = "pkg.camera", activityName = "Camera"),
            AppEntry(label = "Calculator", packageName = "pkg.calculator", activityName = "Calculator"),
        )
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            apps = apps,
            drawerVisibleApps = apps,
            recentApps = listOf("pkg.camera"),
            isLoading = false,
        )

        val updated = LauncherStateTransitions.updateDrawerQuery(
            state = state,
            query = "ca",
            visibleRows = 5,
        )

        assertEquals("pkg.camera", updated.drawerVisibleApps.firstOrNull()?.packageName)
    }

    @Test
    fun updateDrawerQueryMatchesEnglishByPackageName() {
        val apps = listOf(
            AppEntry(
                label = "微信",
                packageName = "com.tencent.mm",
                activityName = "com.tencent.mm.ui.LauncherUI",
            ),
            AppEntry(
                label = "浏览器",
                packageName = "com.android.browser",
                activityName = "com.android.browser.BrowserActivity",
            ),
        )
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            apps = apps,
            drawerVisibleApps = apps,
            recentApps = emptyList(),
            isLoading = false,
        )

        val updated = LauncherStateTransitions.updateDrawerQuery(
            state = state,
            query = "tencent",
            visibleRows = 4,
        )

        assertEquals(listOf("com.tencent.mm"), updated.drawerVisibleApps.map { it.packageName })
    }

    @Test
    fun updateDrawerQueryMatchesEnglishByActivityName() {
        val apps = listOf(
            AppEntry(
                label = "图库",
                packageName = "pkg.gallery",
                activityName = "com.example.photo.GalleryActivity",
            ),
            AppEntry(
                label = "相机",
                packageName = "pkg.camera",
                activityName = "com.example.photo.CameraActivity",
            ),
        )
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            apps = apps,
            drawerVisibleApps = apps,
            recentApps = emptyList(),
            isLoading = false,
        )

        val updated = LauncherStateTransitions.updateDrawerQuery(
            state = state,
            query = "gallery",
            visibleRows = 4,
        )

        assertEquals(listOf("pkg.gallery"), updated.drawerVisibleApps.map { it.packageName })
    }

    @Test
    fun updateDrawerQueryMatchesEnglishLocalizedLabel() {
        val apps = listOf(
            AppEntry(
                label = "微信",
                packageName = "pkg.chat",
                activityName = "pkg.chat.MainActivity",
                englishLabel = "WeChat",
            ),
            AppEntry(
                label = "短信",
                packageName = "pkg.sms",
                activityName = "pkg.sms.MainActivity",
                englishLabel = "Messages",
            ),
        )
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            apps = apps,
            drawerVisibleApps = apps,
            recentApps = emptyList(),
            isLoading = false,
        )

        val updated = LauncherStateTransitions.updateDrawerQuery(
            state = state,
            query = "wechat",
            visibleRows = 4,
        )

        assertEquals(listOf("pkg.chat"), updated.drawerVisibleApps.map { it.packageName })
    }

    @Test
    fun updateDrawerQueryResetsSelectionToFirstResult() {
        val apps = listOf(
            AppEntry(label = "Alpha", packageName = "pkg.a", activityName = "A"),
            AppEntry(label = "Camera", packageName = "pkg.camera", activityName = "Camera"),
            AppEntry(label = "Calendar", packageName = "pkg.calendar", activityName = "Calendar"),
        )
        val state = LauncherState(
            mode = LauncherMode.APP_DRAWER,
            apps = apps,
            drawerVisibleApps = apps,
            drawerQuery = "",
            selectedIndex = 2,
            listStartIndex = 2,
            recentApps = emptyList(),
            isLoading = false,
        )

        val updated = LauncherStateTransitions.updateDrawerQuery(
            state = state,
            query = "ca",
            visibleRows = 4,
        )

        assertEquals(0, updated.selectedIndex)
        assertEquals(0, updated.listStartIndex)
        assertEquals(listOf("pkg.calendar", "pkg.camera"), updated.drawerVisibleApps.map { it.packageName })
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
        assertEquals(2, exitedState.selectedIndex)
        assertEquals(2, exitedState.listStartIndex)
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
        assertEquals(1, exitedState.selectedIndex)
        assertEquals(1, exitedState.listStartIndex)
    }
}
