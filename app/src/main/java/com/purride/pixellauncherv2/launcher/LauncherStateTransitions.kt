package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.data.DeviceStatus
import com.purride.pixellauncherv2.data.LauncherStatsSnapshot
import com.purride.pixellauncherv2.render.PixelFontId
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.PixelTheme
import com.purride.pixellauncherv2.util.LabelFormatter
import java.text.Collator
import java.util.Locale

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
        val stateWithDrawerApps = if (state.apps.isNotEmpty() && state.drawerQuery.isBlank()) {
            state.copy(drawerVisibleApps = orderDefaultApps(state.apps, state.recentApps))
        } else {
            state
        }
        val drawerApps = currentDrawerApps(stateWithDrawerApps)
        if (drawerApps.isEmpty()) {
            return state.copy(
                mode = LauncherMode.APP_DRAWER,
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val safeSelectedIndex = stateWithDrawerApps.selectedIndex.coerceIn(0, drawerApps.lastIndex)
        return syncDrawerWindow(
            state = stateWithDrawerApps.copy(
                mode = LauncherMode.APP_DRAWER,
                selectedIndex = safeSelectedIndex,
            ),
            visibleRows = visibleRows,
        )
    }

    fun withApps(previous: LauncherState, apps: List<AppEntry>, visibleRows: Int): LauncherState {
        val orderedApps = orderDefaultApps(apps, previous.recentApps)
        val drawerApps = filterDrawerApps(
            orderedApps = orderedApps,
            query = previous.drawerQuery,
        )
        if (orderedApps.isEmpty()) {
            return previous.copy(
                apps = emptyList(),
                drawerVisibleApps = emptyList(),
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
                isLoading = false,
            )
        }

        val preservedApp = currentDrawerApps(previous).getOrNull(previous.selectedIndex)
        val selectedIndex = preservedApp?.let { selected ->
            drawerApps.indexOfFirst { candidate ->
                candidate.packageName == selected.packageName &&
                    candidate.activityName == selected.activityName
            }
        }?.takeIf { it >= 0 }
            ?: previous.recentApps.firstNotNullOfOrNull { recentPackage ->
                drawerApps.indexOfFirst { it.packageName == recentPackage }.takeIf { it >= 0 }
            }
            ?: 0

        return syncDrawerWindow(
            state = previous.copy(
                apps = orderedApps,
                drawerVisibleApps = drawerApps,
                selectedIndex = selectedIndex,
                isLoading = false,
            ),
            visibleRows = visibleRows,
        )
    }

    fun moveSelection(state: LauncherState, delta: Int, visibleRows: Int): LauncherState {
        val drawerApps = currentDrawerApps(state)
        if (drawerApps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val newSelectedIndex = (state.selectedIndex + delta).coerceIn(0, drawerApps.lastIndex)
        return syncDrawerWindow(
            state = state.copy(selectedIndex = newSelectedIndex),
            visibleRows = visibleRows,
        )
    }

    fun pageSelection(state: LauncherState, direction: Int, visibleRows: Int): LauncherState {
        val drawerApps = currentDrawerApps(state)
        if (drawerApps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val indexModel = AppDrawerIndexModel.create(
            apps = drawerApps,
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
        val newSelectedIndex = indexModel.pageStartIndices[newPageIndex].coerceIn(0, drawerApps.lastIndex)
        return state.copy(
            selectedIndex = newSelectedIndex,
            listStartIndex = newSelectedIndex,
            drawerPageIndex = newPageIndex,
            drawerFocus = DrawerFocus.LIST,
        )
    }

    fun selectIndex(state: LauncherState, index: Int, visibleRows: Int): LauncherState {
        val drawerApps = currentDrawerApps(state)
        if (drawerApps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val newSelectedIndex = index.coerceIn(0, drawerApps.lastIndex)
        return syncDrawerWindow(
            state = state.copy(selectedIndex = newSelectedIndex),
            visibleRows = visibleRows,
        )
    }

    fun selectDrawerPage(state: LauncherState, pageIndex: Int, visibleRows: Int): LauncherState {
        val drawerApps = currentDrawerApps(state)
        if (drawerApps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val indexModel = AppDrawerIndexModel.create(
            apps = drawerApps,
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
        val selectedIndex = currentDrawerApps(state).indexOfFirst { it.packageName == packageName }
        return if (selectedIndex >= 0) {
            selectIndex(state, selectedIndex, visibleRows)
        } else {
            state
        }
    }

    fun selectByLetterIndex(state: LauncherState, letterIndex: Int, visibleRows: Int): LauncherState {
        val drawerApps = currentDrawerApps(state)
        if (drawerApps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }
        val alphaIndexModel = DrawerAlphaIndexModel.create(
            apps = drawerApps,
            selectedIndex = state.selectedIndex,
        )
        val targetIndex = alphaIndexModel.resolveNearestLetterAppIndex(letterIndex)
            ?.coerceIn(0, drawerApps.lastIndex)
            ?: return state
        return selectIndex(
            state = state,
            index = targetIndex,
            visibleRows = visibleRows,
        )
    }

    fun updateDrawerQuery(state: LauncherState, query: String, visibleRows: Int): LauncherState {
        val safeQuery = query.take(maxDrawerQueryLength)
        val orderedApps = orderDefaultApps(state.apps, state.recentApps)
        val drawerApps = filterDrawerApps(
            orderedApps = orderedApps,
            query = safeQuery,
        )
        val preservedApp = currentDrawerApps(state).getOrNull(state.selectedIndex)
        val selectedIndex = preservedApp?.let { selected ->
            drawerApps.indexOfFirst { candidate ->
                candidate.packageName == selected.packageName &&
                    candidate.activityName == selected.activityName
            }
        }?.takeIf { it >= 0 } ?: 0

        return syncDrawerWindow(
            state = state.copy(
                drawerQuery = safeQuery,
                drawerVisibleApps = drawerApps,
                selectedIndex = selectedIndex,
            ),
            visibleRows = visibleRows,
        )
    }

    fun appendDrawerQuery(state: LauncherState, text: String, visibleRows: Int): LauncherState {
        if (text.isEmpty()) {
            return state
        }
        return updateDrawerQuery(
            state = state,
            query = state.drawerQuery + text,
            visibleRows = visibleRows,
        )
    }

    fun backspaceDrawerQuery(state: LauncherState, visibleRows: Int): LauncherState {
        if (state.drawerQuery.isEmpty()) {
            return state
        }
        return updateDrawerQuery(
            state = state,
            query = state.drawerQuery.dropLast(1),
            visibleRows = visibleRows,
        )
    }

    fun clearDrawerQuery(state: LauncherState, visibleRows: Int): LauncherState {
        if (state.drawerQuery.isBlank()) {
            val orderedApps = orderDefaultApps(state.apps, state.recentApps)
            return syncDrawerWindow(
                state = state.copy(drawerVisibleApps = orderedApps),
                visibleRows = visibleRows,
            )
        }
        return updateDrawerQuery(
            state = state,
            query = "",
            visibleRows = visibleRows,
        )
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

    fun updateTime(
        state: LauncherState,
        currentTimeText: String,
        currentDateText: String = state.currentDateText,
        currentWeekdayText: String = state.currentWeekdayText,
    ): LauncherState {
        return state.copy(
            currentTimeText = currentTimeText,
            currentDateText = currentDateText,
            currentWeekdayText = currentWeekdayText,
        )
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
        val drawerApps = currentDrawerApps(state)
        if (drawerApps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val safeSelectedIndex = state.selectedIndex.coerceIn(0, drawerApps.lastIndex)
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
        val drawerApps = currentDrawerApps(state)
        if (drawerApps.isEmpty()) {
            return state.copy(
                selectedIndex = 0,
                listStartIndex = 0,
                drawerPageIndex = 0,
                drawerFocus = DrawerFocus.LIST,
            )
        }

        val safeSelectedIndex = state.selectedIndex.coerceIn(0, drawerApps.lastIndex)
        val indexModel = AppDrawerIndexModel.create(
            apps = drawerApps,
            visibleRows = visibleRows,
            selectedIndex = safeSelectedIndex,
        )
        val listStartIndex = calculateListStartIndex(
            selectedIndex = safeSelectedIndex,
            visibleRows = visibleRows,
            totalCount = drawerApps.size,
        )

        return state.copy(
            selectedIndex = safeSelectedIndex,
            listStartIndex = listStartIndex,
            drawerPageIndex = indexModel.currentPageIndex,
            drawerFocus = DrawerFocus.LIST,
        )
    }

    private fun currentDrawerApps(state: LauncherState): List<AppEntry> {
        if (state.drawerVisibleApps.isNotEmpty()) {
            return state.drawerVisibleApps
        }
        if (state.drawerQuery.isNotBlank()) {
            return emptyList()
        }
        return state.apps
    }

    private fun orderDefaultApps(apps: List<AppEntry>, _recentApps: List<String>): List<AppEntry> {
        if (apps.isEmpty()) {
            return apps
        }
        return apps.sortedWith { left, right ->
            labelCollator.compare(
                LabelFormatter.sortKey(left.label),
                LabelFormatter.sortKey(right.label),
            )
        }
    }

    private fun filterDrawerApps(orderedApps: List<AppEntry>, query: String): List<AppEntry> {
        if (query.isBlank()) {
            return orderedApps
        }
        val normalizedQuery = normalizeForSearch(query)
        if (normalizedQuery.isEmpty()) {
            return orderedApps
        }

        return orderedApps
            .asSequence()
            .mapNotNull { app ->
                val normalizedLabel = normalizeForSearch(app.label)
                val packageTail = app.packageName.substringAfterLast('.')
                val normalizedPackageTail = normalizeForSearch(packageTail)
                val score = when {
                    app.label.startsWith(query, ignoreCase = true) -> 0
                    normalizedLabel.startsWith(normalizedQuery) -> 1
                    normalizedPackageTail.startsWith(normalizedQuery) -> 2
                    normalizedLabel.contains(normalizedQuery) -> 3
                    normalizedPackageTail.contains(normalizedQuery) -> 4
                    else -> null
                }
                score?.let { DrawerSearchHit(app = app, score = it) }
            }
            .sortedWith(compareBy<DrawerSearchHit> { it.score }.thenComparator { left, right ->
                labelCollator.compare(left.app.label, right.app.label)
            })
            .map { it.app }
            .toList()
    }

    private fun normalizeForSearch(value: String): String {
        return value
            .lowercase(Locale.getDefault())
            .replace(searchNoiseRegex, "")
    }

    private data class DrawerSearchHit(
        val app: AppEntry,
        val score: Int,
    )

    private val labelCollator: Collator = Collator.getInstance(Locale.getDefault())
    private val searchNoiseRegex = Regex("[\\s\\p{Punct}_]+")
    private const val maxDrawerQueryLength: Int = 40
}
