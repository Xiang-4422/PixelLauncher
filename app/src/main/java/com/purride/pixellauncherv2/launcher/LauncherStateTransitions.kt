package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.data.DeviceStatus
import com.purride.pixellauncherv2.data.LauncherStatsSnapshot
import com.purride.pixellauncherv2.render.PixelFontId
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.PixelTheme

object LauncherStateTransitions {

    fun showHome(state: LauncherState): LauncherState {
        return state.copy(mode = LauncherMode.HOME)
    }

    fun showSettings(state: LauncherState): LauncherState {
        val returnMode = when (state.mode) {
            LauncherMode.HOME,
            LauncherMode.APP_DRAWER,
            LauncherMode.IDLE -> state.mode

            LauncherMode.SETTINGS,
            LauncherMode.DIAGNOSTICS -> state.returnMode
        }
        return state.copy(
            mode = LauncherMode.SETTINGS,
            returnMode = returnMode,
            settingsSelectedIndex = state.settingsSelectedIndex.coerceIn(0, SettingsMenuModel.rows(state).lastIndex),
        )
    }

    fun hideSettings(state: LauncherState): LauncherState {
        val fallbackMode = when (state.returnMode) {
            LauncherMode.HOME,
            LauncherMode.APP_DRAWER,
            LauncherMode.IDLE -> state.returnMode

            LauncherMode.SETTINGS,
            LauncherMode.DIAGNOSTICS -> LauncherMode.HOME
        }
        return state.copy(
            mode = fallbackMode,
            returnMode = fallbackMode,
        )
    }

    fun showDiagnostics(state: LauncherState): LauncherState {
        return state.copy(mode = LauncherMode.DIAGNOSTICS)
    }

    fun hideDiagnostics(state: LauncherState): LauncherState {
        return state.copy(mode = LauncherMode.SETTINGS)
    }

    fun showIdle(state: LauncherState): LauncherState {
        if (state.mode != LauncherMode.HOME && state.mode != LauncherMode.APP_DRAWER) {
            return state
        }
        return state.copy(
            mode = LauncherMode.IDLE,
            returnMode = state.mode,
        )
    }

    fun hideIdle(state: LauncherState): LauncherState {
        return state.copy(mode = state.returnMode)
    }

    fun showAppDrawer(state: LauncherState, visibleRows: Int): LauncherState {
        if (state.apps.isEmpty()) {
            return state.copy(
                mode = LauncherMode.APP_DRAWER,
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val safeSelectedIndex = state.selectedIndex.coerceIn(0, state.apps.lastIndex)
        return syncDrawerWindow(
            state = state.copy(
                mode = LauncherMode.APP_DRAWER,
                selectedIndex = safeSelectedIndex,
            ),
            visibleRows = visibleRows,
        )
    }

    fun withApps(previous: LauncherState, apps: List<AppEntry>, visibleRows: Int): LauncherState {
        if (apps.isEmpty()) {
            return previous.copy(
                apps = emptyList(),
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
                isLoading = false,
            )
        }

        val preservedApp = previous.apps.getOrNull(previous.selectedIndex)
        val selectedIndex = preservedApp?.let { selected ->
            apps.indexOfFirst { candidate ->
                candidate.packageName == selected.packageName &&
                    candidate.activityName == selected.activityName
            }
        }?.takeIf { it >= 0 }
            ?: previous.recentApps.firstNotNullOfOrNull { recentPackage ->
                apps.indexOfFirst { it.packageName == recentPackage }.takeIf { it >= 0 }
            }
            ?: 0

        return syncDrawerWindow(
            state = previous.copy(
                apps = apps,
                selectedIndex = selectedIndex,
                isLoading = false,
            ),
            visibleRows = visibleRows,
        )
    }

    fun moveSelection(state: LauncherState, delta: Int, visibleRows: Int): LauncherState {
        if (state.apps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val newSelectedIndex = (state.selectedIndex + delta).coerceIn(0, state.apps.lastIndex)
        return syncDrawerWindow(
            state = state.copy(selectedIndex = newSelectedIndex),
            visibleRows = visibleRows,
        )
    }

    fun pageSelection(state: LauncherState, direction: Int, visibleRows: Int): LauncherState {
        if (state.apps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val indexModel = AppDrawerIndexModel.create(
            apps = state.apps,
            visibleRows = visibleRows,
            selectedIndex = state.selectedIndex,
        )
        if (indexModel.pageCount == 0) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val newPageIndex = (indexModel.currentPageIndex + direction).coerceIn(0, indexModel.pageCount - 1)
        val newSelectedIndex = indexModel.pageStartIndices[newPageIndex].coerceIn(0, state.apps.lastIndex)
        return state.copy(
            selectedIndex = newSelectedIndex,
            listStartIndex = newSelectedIndex,
            drawerPageIndex = newPageIndex,
            drawerFocus = DrawerFocus.LIST,
        )
    }

    fun selectIndex(state: LauncherState, index: Int, visibleRows: Int): LauncherState {
        if (state.apps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val newSelectedIndex = index.coerceIn(0, state.apps.lastIndex)
        return syncDrawerWindow(
            state = state.copy(selectedIndex = newSelectedIndex),
            visibleRows = visibleRows,
        )
    }

    fun selectDrawerPage(state: LauncherState, pageIndex: Int, visibleRows: Int): LauncherState {
        if (state.apps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val indexModel = AppDrawerIndexModel.create(
            apps = state.apps,
            visibleRows = visibleRows,
            selectedIndex = state.selectedIndex,
        )
        if (indexModel.pageCount == 0) {
            return state
        }

        val safePageIndex = pageIndex.coerceIn(0, indexModel.pageCount - 1)
        val pageStartIndex = indexModel.pageStartIndices[safePageIndex]
        return state.copy(
            selectedIndex = pageStartIndex,
            listStartIndex = pageStartIndex,
            drawerPageIndex = safePageIndex,
            drawerFocus = DrawerFocus.LIST,
        )
    }

    fun selectByPackageName(state: LauncherState, packageName: String, visibleRows: Int): LauncherState {
        val selectedIndex = state.apps.indexOfFirst { it.packageName == packageName }
        return if (selectedIndex >= 0) {
            selectIndex(state, selectedIndex, visibleRows)
        } else {
            state
        }
    }

    fun selectSettingsIndex(state: LauncherState, index: Int): LauncherState {
        val maxIndex = (SettingsMenuModel.rows(state).size - 1).coerceAtLeast(0)
        return state.copy(settingsSelectedIndex = index.coerceIn(0, maxIndex))
    }

    fun moveSettingsSelection(state: LauncherState, delta: Int): LauncherState {
        return selectSettingsIndex(
            state = state,
            index = state.settingsSelectedIndex + delta,
        )
    }

    fun updateTime(state: LauncherState, currentTimeText: String): LauncherState {
        return state.copy(currentTimeText = currentTimeText)
    }

    fun updateAppearance(
        state: LauncherState,
        selectedFontId: PixelFontId = state.selectedFontId,
        selectedPixelShape: PixelShape = state.selectedPixelShape,
        selectedDotSizePx: Int = state.selectedDotSizePx,
        selectedTheme: PixelTheme = state.selectedTheme,
    ): LauncherState {
        return state.copy(
            selectedFontId = selectedFontId,
            selectedPixelShape = selectedPixelShape,
            selectedDotSizePx = selectedDotSizePx,
            selectedTheme = selectedTheme,
        )
    }

    fun updateDeviceStatus(state: LauncherState, deviceStatus: DeviceStatus): LauncherState {
        return state.copy(
            batteryLevel = deviceStatus.batteryLevel,
            isCharging = deviceStatus.isCharging,
        )
    }

    fun updateStats(state: LauncherState, stats: LauncherStatsSnapshot): LauncherState {
        return state.copy(
            recentApps = stats.recentApps,
            launchCount = stats.launchCount,
            lastLaunchPackageName = stats.lastLaunchPackageName,
        )
    }

    fun updateTerminalStatus(state: LauncherState, terminalStatusText: String): LauncherState {
        return state.copy(terminalStatusText = terminalStatusText)
    }

    fun recordInteraction(state: LauncherState, uptimeMs: Long): LauncherState {
        return state.copy(lastInteractionUptimeMs = uptimeMs)
    }

    fun reflowWindow(state: LauncherState, visibleRows: Int): LauncherState {
        if (state.apps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val safeSelectedIndex = state.selectedIndex.coerceIn(0, state.apps.lastIndex)
        return syncDrawerWindow(
            state = state.copy(selectedIndex = safeSelectedIndex),
            visibleRows = visibleRows,
        )
    }

    fun calculateListStartIndex(selectedIndex: Int, visibleRows: Int, totalCount: Int): Int {
        if (totalCount <= 0) {
            return 0
        }

        val safeRows = visibleRows.coerceAtLeast(1)
        val safeSelectedIndex = selectedIndex.coerceIn(0, totalCount - 1)
        return ((safeSelectedIndex / safeRows) * safeRows).coerceAtLeast(0)
    }

    private fun syncDrawerWindow(state: LauncherState, visibleRows: Int): LauncherState {
        if (state.apps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val safeSelectedIndex = state.selectedIndex.coerceIn(0, state.apps.lastIndex)
        val indexModel = AppDrawerIndexModel.create(
            apps = state.apps,
            visibleRows = visibleRows,
            selectedIndex = safeSelectedIndex,
        )
        val listStartIndex = calculateListStartIndex(
            selectedIndex = safeSelectedIndex,
            visibleRows = visibleRows,
            totalCount = state.apps.size,
        )

        return state.copy(
            selectedIndex = safeSelectedIndex,
            listStartIndex = listStartIndex,
            drawerPageIndex = indexModel.currentPageIndex,
            drawerFocus = DrawerFocus.LIST,
        )
    }
}
